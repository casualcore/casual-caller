/*
 * Copyright (c) 2021 - 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.connection.caller.functions.FunctionNoArg;
import se.laz.casual.connection.caller.functions.FunctionThrowsResourceException;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.network.connection.CasualConnectionException;
import se.laz.casual.network.connection.DomainDisconnectedException;

import javax.resource.ResourceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
            FunctionThrowsResourceException<ServiceReturn<CasualBuffer>> doCall,
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

    public CompletableFuture<Optional<ServiceReturn<CasualBuffer>>> tpacallWithFailover(
            String serviceName,
            ConnectionFactoryLookup lookup,
            FunctionThrowsResourceException<CompletableFuture<Optional<ServiceReturn<CasualBuffer>>>> doCall,
            FunctionNoArg<CompletableFuture<Optional<ServiceReturn<CasualBuffer>>>> doTpenoent)
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

    private <T> T issueCall(String serviceName, List<ConnectionFactoryEntry> validEntries, FunctionThrowsResourceException<T> doCall)
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
                return doCall.apply(con, UUID.randomUUID());
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

}