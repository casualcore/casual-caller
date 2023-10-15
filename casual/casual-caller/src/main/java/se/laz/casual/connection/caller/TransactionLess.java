/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;

import se.laz.casual.api.discovery.DiscoveryReturn;
import se.laz.casual.api.service.ServiceDetails;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.api.conversation.TpConnectReturn;

import jakarta.resource.ResourceException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class TransactionLess
{
    private static final Logger LOG = Logger.getLogger(TransactionLess.class.getName());
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public ServiceReturn<CasualBuffer> tpcall(Supplier<ServiceReturn<CasualBuffer>> supplier)
    {
        return supplier.get();
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public CompletableFuture<ServiceReturn<CasualBuffer>> tpacall(Supplier<CompletableFuture<ServiceReturn<CasualBuffer>>> supplier)
    {
        return supplier.get();
    }

   @Transactional(Transactional.TxType.NOT_SUPPORTED)
   public List<ServiceDetails> serviceDetails(ConnectionFactoryEntry connectionFactoryEntry, Function<CasualConnection, List<ServiceDetails>> fetchFunction) throws ResourceException
   {
      try (CasualConnection connection = connectionFactoryEntry.getConnectionFactory().getConnection())
      {
         return fetchFunction.apply(connection);
      }
   }

   @Transactional(Transactional.TxType.NOT_SUPPORTED)
   public boolean queueExists(ConnectionFactoryEntry connectionFactoryEntry, Lookup.PredicateThrowsResourceException<CasualConnection> predicate) throws ResourceException
   {
      try (CasualConnection connection = connectionFactoryEntry.getConnectionFactory().getConnection())
      {
         return predicate.test(connection);
      }
   }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public Optional<DiscoveryReturn> discover(ConnectionFactoryEntry connectionFactoryEntry, Map<CacheType, List<String>> cachedItems)
    {
        try(CasualConnection connection = connectionFactoryEntry.getConnectionFactory().getConnection())
        {
            LOG.finest(() -> "domain discovery for all known services/queues will be issued for " + connectionFactoryEntry );
            LOG.finest(() -> "all known services/queues being, services: " + cachedItems.get(CacheType.SERVICE) + " queues: " + cachedItems.get(CacheType.QUEUE));
            return Optional.of(connection.discover(UUID.randomUUID(),
                    cachedItems.get(CacheType.SERVICE),
                    cachedItems.get(CacheType.QUEUE)));
        }
        catch (ResourceException e)
        {
            connectionFactoryEntry.invalidate();
            LOG.warning(() -> "failed domain discovery for: " + connectionFactoryEntry + " -> " + e);
            LOG.warning(() -> "services: " + cachedItems.get(CacheType.SERVICE) + " queues: " + cachedItems.get(CacheType.QUEUE));
        }
        return Optional.empty();
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public TpConnectReturn tpconnect(Supplier<TpConnectReturn> supplier)
    {
        return supplier.get();
    }



}
