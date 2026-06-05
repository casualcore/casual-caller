/*
 * Copyright (c) 2021 - 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.conversation.TpConnectReturn;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.connection.caller.conversation.ConversationFailover;
import se.laz.casual.connection.caller.functions.BiFunctionThrowsResourceException;
import se.laz.casual.connection.caller.functions.FunctionThrowsResourceException;
import se.laz.casual.jca.CasualConnection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class FailoverAlgorithm
{
    private static final Logger LOG = Logger.getLogger(FailoverAlgorithm.class.getName());
    private static final String ALL_FAIL_MESSAGE = "Received a set of ConnectionFactoryEntries, but not one was valid for service ";

    public ServiceReturn<CasualBuffer> tpcallWithFailover(
            String serviceName,
            ConnectionFactoryLookup lookup,
            BiFunctionThrowsResourceException<CasualConnection, UUID, ServiceReturn<CasualBuffer>> doCall,
            Supplier<ServiceReturn<CasualBuffer>> doTpenoent)
    {
        List<ConnectionFactoryEntry> validEntries = getFoundAndValidEntries(lookup, serviceName);
        // No valid casual server found (revalidation is on a timer in ConnectionFactoryEntryValidationTimer)
        if (validEntries.isEmpty())
        {
            LOG.warning(() -> ALL_FAIL_MESSAGE + serviceName);
            return doTpenoent.get();
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
                return doTpenoent.get();
            }
            result = issueCall(serviceName, validEntries, doCall);
        }
        return result;
    }

    public CompletableFuture<Optional<ServiceReturn<CasualBuffer>>> tpacallWithFailover(
            String serviceName,
            ConnectionFactoryLookup lookup,
            BiFunctionThrowsResourceException<CasualConnection, UUID, CompletableFuture<Optional<ServiceReturn<CasualBuffer>>>> doCall,
            Supplier<CompletableFuture<Optional<ServiceReturn<CasualBuffer>>>> doTpenoent)
    {
        List<ConnectionFactoryEntry> validEntries = getFoundAndValidEntries(lookup, serviceName);
        // No valid casual server found (revalidation is on a timer in ConnectionFactoryEntryValidationTimer)
        if (validEntries.isEmpty())
        {
            LOG.warning(() -> ALL_FAIL_MESSAGE + serviceName);
            return doTpenoent.get();
        }
        return issueCall(serviceName, validEntries, doCall);
    }

    public TpConnectReturn tpconnectWithFailover(String serviceName,
                                                 ConnectionFactoryLookup lookup,
                                                 FunctionThrowsResourceException<TpConnectReturn, CasualConnection> doCall,
                                                 Supplier<TpConnectReturn> doTpenoent)
    {
        List<ConnectionFactoryEntry> validEntries = getFoundAndValidEntries(lookup, serviceName);
        // No valid casual server found (revalidation is on a timer in ConnectionFactoryEntryValidationTimer)
        if (validEntries.isEmpty())
        {
            LOG.warning(() -> ALL_FAIL_MESSAGE + serviceName);
            return doTpenoent.get();
        }
        return ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall);
    }

    // list needs to be mutable
    @SuppressWarnings("java:S6204")
    private List<ConnectionFactoryEntry> getFoundAndValidEntries(ConnectionFactoryLookup lookup, String serviceName)
    {
        // This is always through the cache, either it was already there or a lookup was issued and then stored
        List<ConnectionFactoryEntry> prioritySortedFactories = lookup.get(serviceName);
        List<ConnectionFactoryEntry> validEntries = prioritySortedFactories.stream()
                                                                           .filter(ConnectionFactoryEntry::isValid).collect(Collectors.toList());
        LOG.finest(() -> "Entries found for '" + serviceName + "' with " + validEntries.size() + " of " + prioritySortedFactories.size() + " possible connection factories");
        if(validEntries.isEmpty())
        {
            LOG.info(() -> "No valid connection factories found for service " + serviceName + " prioritySortedFactories: " + prioritySortedFactories);
        }
        return validEntries;
    }

    private <T> T issueCall(String serviceName, List<ConnectionFactoryEntry> validEntries, BiFunctionThrowsResourceException<CasualConnection, UUID, T> doCall)
    {
        Exception thrownException = null;

        // Sticky transaction handling
        try
        {
            Optional<T> stickyMaybe = StickyTransactionHandler.handleTransactionSticky(serviceName, validEntries, doCall, TransactionPoolMapper::getInstance);

            if (stickyMaybe.isPresent())
            {
                return stickyMaybe.get();
            }
        }
        catch (Exception e)
        {
            LOG.finest("Failed call for stickied pool with exception, will run failover if applicable");
            thrownException = e;
            if(transactionMarkedForRollback())
            {
                // we should not try any other pool, as the transaction is marked for rollback
                // and would only result in a rollback even for a subsequent call ok call
                throw new CasualResourceException("sticky failed, transaction rolling back - not trying any other pool", thrownException);
            }
        }

        LOG.finest("sticky: valid entries after failed call -> " + validEntries);

        // Normal flow
        for (ConnectionFactoryEntry connectionFactoryEntry : validEntries)
        {
            try (CasualConnection con = connectionFactoryEntry.getConnectionFactory().getConnection())
            {
                T result = doCall.apply(con, UUID.randomUUID());
                LOG.finest("Successful call for connection factory " + connectionFactoryEntry.getJndiName());
                return result;

            }
            catch (Exception e)
            {
                thrownException = e;
                connectionFactoryEntry.invalidate();
                if(transactionMarkedForRollback())
                {
                    // we should not try any other pool, as the transaction is marked for rollback
                    // and would only result in a rollback even for a subsequent call ok call
                    throw new CasualResourceException("Call failed during execution to service=" + serviceName + " on connection=" + connectionFactoryEntry.getJndiName() + " because of a network connection error, retries not possible.", e);
                }
            }
        }
        throw new CasualResourceException("Call failed to all " + validEntries.size() + " available casual connections.", thrownException);
    }

    private static boolean transactionMarkedForRollback()
    {
        TransactionManager tm = CDI.current().select(TransactionManager.class).get();
        int status = 0;
        try
        {
            status = tm.getStatus();
        }
        catch (SystemException e)
        {
            LOG.warning("Failed to get transaction status, assuming not rollback only");
            return false;
        }
        return status == Status.STATUS_MARKED_ROLLBACK;
    }

}