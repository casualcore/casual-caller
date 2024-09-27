/*
 * Copyright (c) 2021 - 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import jakarta.resource.ResourceException;
import se.laz.casual.jca.CasualConnection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import se.laz.casual.connection.caller.functions.FunctionThrowsResourceException;
import se.laz.casual.network.connection.CasualConnectionException;

public class StickyTransactionHandler
{
    private static final Logger LOG = Logger.getLogger(StickyTransactionHandler.class.getName());

    private StickyTransactionHandler()
    {}
    /**
     * Try to use a stickied pool if pool stickiness is configured and any stickied pool is available and serves the requested service
     *
     * @param serviceName Service to call
     * @param factories   Currently available factories for service to call
     * @param doCall      Provided service call procedure
     * @return Optional ServiceReturn. An empty result could indicate that stickiness isn't enabled, sticky isn't set yet for the current transaction (which will be the case for the first call) or the stickied pool was unavailable so call to sticky was skipped. Empty should always lead to retry down the line if possible, otherwise a TPENOENT response.
     * @throws ResourceException Some softer errors are reported as resource exceptions. If these are thrown later retries with other pools is possible.
     */
    public static <T> Optional<T> handleTransactionSticky(
            String serviceName,
            List<ConnectionFactoryEntry> factories,
            FunctionThrowsResourceException<T> doCall,
            Supplier<TransactionPoolMapper> transactionPoolMapperSupplier) throws ResourceException
    {
        if (!transactionPoolMapperSupplier.get().isPoolMappingActive())
        {
            return Optional.empty();
        }

        Optional<StickiedCallInfo> stickyMaybe = getAndSetSticky(serviceName, factories, transactionPoolMapperSupplier);

        if (stickyMaybe.isPresent())
        {
            // We have a specific stickied pool to use that looks usable, try to use it
            StickiedCallInfo sticky = stickyMaybe.get();
            factories.remove(sticky.connectionFactoryEntry()); // If we later need to do failover stuff we don't want to retry with this one
            LOG.finest(() -> "Attempting to use pool=" + sticky.connectionFactoryEntry().getJndiName() + " with sticky to current transaction.");
            try (CasualConnection con = sticky.connectionFactoryEntry().getConnectionFactory().getConnection())
            {
                return Optional.of(doCall.apply(con, sticky.execution()));
            }
            catch (CasualConnectionException e)
            {
                //This error branch will most likely happen if there are connection errors during a service call
                sticky.connectionFactoryEntry().invalidate();

                // These exceptions are rollback-only, do not attempt any retries.
                throw new CasualResourceException("Call failed during execution to service=" + serviceName
                        + " on connection=" + sticky.connectionFactoryEntry().getJndiName()
                        + " because of a network connection error, retries not possible.", e);
            }
        }
        else
        {
            LOG.finest(() -> "Current sticky is " + transactionPoolMapperSupplier.get().getStickyInformationForCurrentTransaction()
                    + " but called service=" + serviceName
                    + " is currently available in pools [" + factories.stream().map(ConnectionFactoryEntry::getJndiName).collect(Collectors.joining(","))
                    + "]. Will use available pools instead of stickied pool.");
            return Optional.empty();
        }
    }

    private static Optional<StickiedCallInfo> getAndSetSticky(String serviceName, List<ConnectionFactoryEntry> validFactories, Supplier<TransactionPoolMapper> transactionPoolMapperSupplier)
    {
        StickyInformation stickyInformation = transactionPoolMapperSupplier.get().getStickyInformationForCurrentTransaction();

        if (stickyInformation == null)
        {
            // Service exists in some pool, pick first one as sticky (would otherwise be picked later in normal flow)
            ConnectionFactoryEntry newStickyFactory = validFactories.get(0);
            StickyInformation newStickyInformation = new StickyInformation(newStickyFactory.getJndiName(), UUID.randomUUID());
            transactionPoolMapperSupplier.get().setStickyInformationForCurrentTransaction(newStickyInformation);
            LOG.finest(() -> "No sticky present for call to service=" + serviceName + ", setting sticky=" + newStickyFactory.getJndiName() + " with=" + newStickyInformation);
            return Optional.of(new StickiedCallInfo(newStickyFactory, newStickyInformation.execution()));
        }
        else
        {
            Optional<ConnectionFactoryEntry> stickyMatch = validFactories
                    .stream()
                    .filter(connectionFactoryEntry -> stickyInformation.poolName().equals(connectionFactoryEntry.getJndiName()) && connectionFactoryEntry.isValid())
                    .findFirst();

            if (stickyMatch.isPresent())
            {
                LOG.finest(() -> "Using stickied pool=" + stickyInformation + " for call to service=" + serviceName);
                ConnectionFactoryEntry stickyEntry = stickyMatch.get();
                validFactories.remove(stickyEntry);
                return Optional.of(new StickiedCallInfo(stickyEntry, stickyInformation.execution()));
            }
            else
            {
                LOG.finest(() -> "There was a sticky=" + stickyInformation + ", but it did not match the valid factories for the called service=" + serviceName);
                return Optional.empty();
            }
        }
    }
}
