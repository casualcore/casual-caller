/*
 * Copyright (c) 2021 - 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.resource.ResourceException;
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
import se.laz.casual.jca.CasualConnectionFactory;

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
    private TransactionManager transactionManager;

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
        return ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall, this::transactionAllowsRetry);
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
        catch (ResourceException e)
        {
            LOG.finest("Sticky connection acquisition failed");
            thrownException = e;
            if(!transactionAllowsRetry())
            {
                // Retry only while the transaction permits new work.
                throw new CasualResourceException("Sticky connection acquisition failed; transaction does not permit retry.", thrownException);
            }
        }

        LOG.finest(() -> "sticky: valid entries after failed call -> " + validEntries);

        // Normal flow
        for (ConnectionFactoryEntry connectionFactoryEntry : validEntries)
        {
            CasualConnectionFactory connectionFactory = connectionFactoryEntry.getConnectionFactory();
            final CasualConnection connection;
            try
            {
                // enlists resource in transaction
                connection = connectionFactory.getConnection();
            }
            catch (ResourceException e)
            {
                thrownException = e;
                connectionFactoryEntry.invalidate();
                if (!transactionAllowsRetry())
                {
                    throw new CasualResourceException("Connection acquisition failed; transaction does not permit retry.", e);
                }
                continue;
            }
            try (connection)
            {
                return doCall.apply(connection, UUID.randomUUID());
            }
            catch (Exception e)
            {
                connectionFactoryEntry.invalidate();
                throw new CasualResourceException("Service invocation failed for service=" + serviceName
                        + " on connection=" + connectionFactoryEntry.getJndiName() + "; no retry is attempted.", e);
            }
        }
        throw new CasualResourceException("Call failed to all " + validEntries.size() + " available casual connections.", thrownException);
    }

    void setTransactionManager(TransactionManager transactionManager)
    {
        this.transactionManager = transactionManager;
    }

    TransactionManager getTransactionManager()
    {
        if(transactionManager != null)
        {
            return transactionManager;
        }
        return CDI.current().select(TransactionManager.class).get();
    }

    private boolean transactionAllowsRetry()
    {
        TransactionManager tm = getTransactionManager();
        final int status;
        try
        {
            status = tm.getStatus();
        }
        catch (SystemException e)
        {
            throw new CasualResourceException("Cannot determine transaction status; no retry is attempted.", e);
        }
        return status == Status.STATUS_ACTIVE || status == Status.STATUS_NO_TRANSACTION;
    }

}