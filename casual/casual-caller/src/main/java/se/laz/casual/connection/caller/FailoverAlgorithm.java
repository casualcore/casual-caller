/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.network.connection.CasualConnectionException;
import se.laz.casual.network.connection.DomainDisconnectedException;

import javax.resource.ResourceException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FailoverAlgorithm
{
    private static final Logger LOG = Logger.getLogger(FailoverAlgorithm.class.getName());
    private static final String ALL_FAIL_MESSAGE = "Received a set of ConnectionFactoryEntries, but not one was valid for service ";

    public ServiceReturn<CasualBuffer> tpcallWithFailover(
            String serviceName,
            ConnectionFactoryLookup lookup,
            FunctionThrowsResourceException<CasualConnection, ServiceReturn<CasualBuffer>> doCall,
            FunctionNoArg<ServiceReturn<CasualBuffer>> doTpenoent)
    {
        List<ConnectionFactoryEntry> validEntries = getFoundAndValidEntries(lookup, serviceName);

        // No valid casual server found (revalidation is on a timer in ConnectionFactoryEntryValidationTimer)
        if (validEntries.isEmpty())
        {
            LOG.warning(() -> ALL_FAIL_MESSAGE + serviceName);
            return doTpenoent.apply();
        }

        ServiceReturn<CasualBuffer> result = issueCall(serviceName, validEntries, doCall);
        if (result.getErrorState() == ErrorState.TPENOENT)
        {
            // using a known cached service entry results in TPENOENT
            // clear the service from the cache ( for all pools), get potentially new entries
            // issue call again if possible
            lookup.removeFromServiceCache(serviceName);
            validEntries = getFoundAndValidEntries(lookup, serviceName);
            // No valid casual server found (revalidation is on a timer in ConnectionFactoryEntryValidationTimer)
            if (validEntries.isEmpty())
            {
                LOG.warning(() -> ALL_FAIL_MESSAGE + serviceName);
                return doTpenoent.apply();
            }
            result = issueCall(serviceName, validEntries, doCall);
        }
        return result;
    }

    public CompletableFuture<ServiceReturn<CasualBuffer>> tpacallWithFailover(
            String serviceName,
            ConnectionFactoryLookup lookup,
            FunctionThrowsResourceException<CasualConnection, CompletableFuture<ServiceReturn<CasualBuffer>>> doCall,
            FunctionNoArg<CompletableFuture<ServiceReturn<CasualBuffer>>> doTpenoent)
    {
        List<ConnectionFactoryEntry> validEntries = getFoundAndValidEntries(lookup, serviceName);

        // No valid casual server found (revalidation is on a timer in ConnectionFactoryEntryValidationTimer)
        if (validEntries.isEmpty())
        {
            LOG.warning(() -> ALL_FAIL_MESSAGE + serviceName);
            return doTpenoent.apply();
        }
        return issueCall(serviceName, validEntries, doCall);
    }

    private List<ConnectionFactoryEntry> getFoundAndValidEntries(ConnectionFactoryLookup lookup, String serviceName)
    {
        // This is always through the cache, either it was already there or a lookup was issued and then stored
        List<ConnectionFactoryEntry> prioritySortedFactories = lookup.get(serviceName);
        List<ConnectionFactoryEntry> validEntries = prioritySortedFactories.stream().filter(ConnectionFactoryEntry::isValid).collect(Collectors.toList());
        LOG.finest(() -> "Entries found for '" + serviceName + "' with " + validEntries.size() + " of " + prioritySortedFactories.size() + " possible connection factories");
        return validEntries;
    }

    private <T> T issueCall(String serviceName, List<ConnectionFactoryEntry> validEntries, FunctionThrowsResourceException<CasualConnection, T> doCall)
    {
        Exception thrownException = null;

        // Sticky transaction handling
        try
        {
            Optional<T> stickyMaybe = handleTransactionSticky(serviceName, validEntries, doCall);

            if (stickyMaybe.isPresent())
            {
                return stickyMaybe.get();
            }
        }
        catch (ResourceException | DomainDisconnectedException e)
        {
            LOG.finest("Failed call for stickied pool with exception, will run failover if applicable");
            thrownException = e;
        }

        // Normal flow
        for (ConnectionFactoryEntry connectionFactoryEntry : validEntries)
        {
            try (CasualConnection con = connectionFactoryEntry.getConnectionFactory().getConnection())
            {
                return doCall.apply(con);
            }
            catch (CasualConnectionException e)
            {
                //This error branch will most likely happen if there are connection errors during a service call
                connectionFactoryEntry.invalidate();

                // These exceptions are rollback-only, do not attempt any retries.
                throw new CasualResourceException("Call failed during execution to service=" + serviceName + " on connection=" + connectionFactoryEntry.getJndiName() + " because of a network connection error, retries not possible.", e);
            }
            catch (ResourceException | DomainDisconnectedException e)
            {
                // This error branch will most likely happen on failure to establish connection with a casual backend
                // or when a casual domain is disconnecting
                connectionFactoryEntry.invalidate();

                // Do retries on ResourceExceptions. Save the thrown exception and return to the loop
                // If there are more entries to try that will be done, or the flow will exit and this
                // exception will be thrown wrapped at the end of the method.
                thrownException = e;
            }
        }
        throw new CasualResourceException("Call failed to all " + validEntries.size() + " available casual connections.", thrownException);
    }

    /**
     * Try to use a stickied pool if pool stickiness is configured and any stickied pool is available and serves the requested service
     *
     * @param serviceName Service to call
     * @param factories   Currently available factories for service to call
     * @param doCall      Provided service call procedure
     * @return Optional ServiceReturn. An empty result could indicate that stickiness isn't enabled, sticky isn't set yet for the current transaction (which will be the case for the first call) or the stickied pool was unavailable so call to sticky was skipped. Empty should always lead to retry down the line if possible, otherwise a TPENOENT response.
     * @throws ResourceException Some softer errors are reported as resource exceptions. If these are thrown later retries with other pools is possible.
     */
    private <T> Optional<T> handleTransactionSticky(
            String serviceName,
            List<ConnectionFactoryEntry> factories,
            FunctionThrowsResourceException<CasualConnection, T> doCall) throws ResourceException
    {
        if (!TransactionPoolMapper.getInstance().isPoolMappingActive())
        {
            return Optional.empty();
        }

        Optional<ConnectionFactoryEntry> stickyFactoryMaybe = getAndSetSticky(serviceName, factories);

        if (stickyFactoryMaybe.isPresent())
        {
            // We have a specific stickied pool to use that looks usable, try to use it
            ConnectionFactoryEntry connectionFactoryEntry = stickyFactoryMaybe.get();
            factories.remove(connectionFactoryEntry); // If we later need to do failover stuff we don't want to retry with this one
            LOG.finest(() -> "Attempting to use pool=" + connectionFactoryEntry.getJndiName() + " with sticky to current transaction.");

            try (CasualConnection con = connectionFactoryEntry.getConnectionFactory().getConnection())
            {
                return Optional.of(doCall.apply(con));
            }
            catch (CasualConnectionException e)
            {
                //This error branch will most likely happen if there are connection errors during a service call
                connectionFactoryEntry.invalidate();

                // These exceptions are rollback-only, do not attempt any retries.
                throw new CasualResourceException("Call failed during execution to service=" + serviceName
                        + " on connection=" + connectionFactoryEntry.getJndiName()
                        + " because of a network connection error, retries not possible.", e);
            }
        }
        else
        {
            LOG.finest(() -> "Current sticky is " + TransactionPoolMapper.getInstance().getPoolNameForCurrentTransaction()
                    + " but called service=" + serviceName
                    + " is currently available in pools [" + factories.stream().map(ConnectionFactoryEntry::getJndiName).collect(Collectors.joining(","))
                    + "]. Will use available pools instead of stickied pool.");
            return Optional.empty();
        }
    }

    private Optional<ConnectionFactoryEntry> getAndSetSticky(String serviceName, List<ConnectionFactoryEntry> validFactories)
    {
        String transactionPoolName = TransactionPoolMapper.getInstance().getPoolNameForCurrentTransaction();

        if (transactionPoolName == null)
        {
            // Service exists in some pool, pick first one as sticky (would otherwise be picked later in normal flow)
            ConnectionFactoryEntry newStickyFactory = validFactories.get(0);
            TransactionPoolMapper.getInstance().setPoolNameForCurrentTransaction(newStickyFactory.getJndiName());
            LOG.finest(() -> "No sticky present for call to service=" + serviceName + ", setting sticky=" + newStickyFactory.getJndiName());
            return Optional.of(newStickyFactory);
        }
        else
        {
            Optional<ConnectionFactoryEntry> stickyMatch = validFactories
                    .stream()
                    .filter(connectionFactoryEntry -> transactionPoolName.equals(connectionFactoryEntry.getJndiName()) && connectionFactoryEntry.isValid())
                    .findFirst();

            if (stickyMatch.isPresent())
            {
                LOG.finest(() -> "Using stickied pool=" + transactionPoolName + " for call to service=" + serviceName);
                validFactories.remove(stickyMatch.get());
                return stickyMatch;
            }
            else
            {
                LOG.finest(() -> "There was a sticky=" + transactionPoolName + ", but it did not match the valid factories for the called service=" + serviceName);
                return Optional.empty();
            }
        }
    }


    public interface FunctionNoArg<R>
    {
        R apply();
    }

    public interface FunctionThrowsResourceException<I, R>
    {
        R apply(I input) throws ResourceException;
    }
}