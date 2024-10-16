/*
 * Copyright (c) 2021 - 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.queue.DequeueReturn;
import se.laz.casual.api.queue.EnqueueReturn;
import se.laz.casual.api.queue.MessageSelector;
import se.laz.casual.api.queue.QueueInfo;
import se.laz.casual.api.queue.QueueMessage;
import se.laz.casual.api.service.ServiceDetails;
import se.laz.casual.jca.CasualConnection;

import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.resource.ResourceException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Remote(CasualCaller.class)
@Stateless
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class CasualCallerImpl implements CasualCaller
{
    private TpCaller tpCaller = new TpCallerFailover();
    private ConnectionFactoryLookup lookup;
    private TransactionLess transactionLess;
    private FailedDomainDiscoveryHandler failedDomainDiscoveryHandler;

    // NOP constructor needed for WLS
    public CasualCallerImpl()
    {}

    @Inject
    public CasualCallerImpl(ConnectionFactoryLookup lookup, ConnectionFactoryEntryStore connectionFactoryProvider, TransactionLess transactionLess, FailedDomainDiscoveryHandler failedDomainDiscoveryHandler)
    {
        this.lookup = lookup;
        this.transactionLess = transactionLess;
        this.failedDomainDiscoveryHandler = failedDomainDiscoveryHandler;
        List<ConnectionFactoryEntry> possibleEntries = connectionFactoryProvider.get();
        if(possibleEntries.isEmpty())
        {
            throw new CasualCallerException("No connection factories available, casual caller is not usable");
        }
    }

    @Override
    public ServiceReturn<CasualBuffer> tpcall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags)
    {
        failedDomainDiscoveryHandler.issueDomainDiscoveryAndRepopulateCache();
        return flags.isSet(AtmiFlags.TPNOTRAN) ? transactionLess.tpcall(() -> tpCaller.tpcall(serviceName, data, flags, lookup)) : tpCaller.tpcall(serviceName, data, flags, lookup);
    }

    @Override
    public ServiceReturn<CasualBuffer> tpcall(String s, CasualBuffer casualBuffer, Flag<AtmiFlags> flag, UUID execution)
    {
        throw new UnsupportedOperationException("Casual caller does not support tpcall with execution");
    }

    @Override
    public CompletableFuture<Optional<ServiceReturn<CasualBuffer>>> tpacall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags)
    {
        failedDomainDiscoveryHandler.issueDomainDiscoveryAndRepopulateCache();
        return flags.isSet(AtmiFlags.TPNOTRAN) ? transactionLess.tpacall(() -> tpCaller.tpacall(serviceName, data, flags, lookup)) : tpCaller.tpacall(serviceName, data, flags, lookup);
    }

    @Override
    public CompletableFuture<Optional<ServiceReturn<CasualBuffer>>> tpacall(String s, CasualBuffer casualBuffer, Flag<AtmiFlags> flag, UUID uuid)
    {
        throw new UnsupportedOperationException("Casual caller does not support tpacall with execution");
    }

    @Override
    public boolean serviceExists(String serviceName)
    {
        return !lookup.get(serviceName).isEmpty();
    }

    @Override
    public List<ServiceDetails> serviceDetails(String serviceName)
    {
        throw new CasualCallerException("CasualCaller does not support serviceDetails. Please use CasualServiceCaller::serviceDetails to get specific service details.");
    }

    @Override
    public EnqueueReturn enqueue(QueueInfo qinfo, QueueMessage msg)
    {
        failedDomainDiscoveryHandler.issueDomainDiscoveryAndRepopulateCache();
        Optional<ConnectionFactoryEntry> entry = lookup.get(qinfo);

        if (!entry.isPresent())
        {
            return EnqueueReturn.createBuilder().withErrorState(ErrorState.TPENOENT).build();
        }

        try(CasualConnection connection = entry.get().getConnectionFactory().getConnection())
        {
            return connection.enqueue(qinfo, msg);
        }
        catch (ResourceException e)
        {
            throw new CasualResourceException(e);
        }
    }

    @Override
    public DequeueReturn dequeue(QueueInfo qinfo, MessageSelector selector)
    {
        failedDomainDiscoveryHandler.issueDomainDiscoveryAndRepopulateCache();
        Optional<ConnectionFactoryEntry> entry = lookup.get(qinfo);

        if (!entry.isPresent())
        {
            return DequeueReturn.createBuilder().withErrorState(ErrorState.TPENOENT).build();
        }

        try(CasualConnection connection = entry.get().getConnectionFactory().getConnection())
        {
            return connection.dequeue(qinfo, selector);
        }
        catch (ResourceException e)
        {
            throw new CasualResourceException(e);
        }
    }

    @Override
    public boolean queueExists(QueueInfo qinfo)
    {
        return lookup.get(qinfo).isPresent();
    }

}
