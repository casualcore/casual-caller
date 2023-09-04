/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.api.discovery.DiscoveryReturn;
import se.laz.casual.jca.CasualConnection;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

@ApplicationScoped
public class CacheRepopulator
{
    private static final Logger LOG = Logger.getLogger(CacheRepopulator.class.getName());
    private Cache cache;
    private final Object repopulateLock = new Object();
    // WLS - no arg constructor
    public CacheRepopulator()
    {}

    @Inject
    public CacheRepopulator(Cache cache)
    {
        this.cache = cache;
    }

    public void repopulate(ConnectionFactoryEntry connectionFactoryEntry)
    {
        // note:
        // We only ever want one pool at a time do handle domain discovery.
        // This so that we do not end up with a scenario where we have multiple discoveries in flight
        // and the known state of the world, cache, is currently very small when another discovery is issued
        synchronized (repopulateLock)
        {
            Map<CacheType, List<String>> cachedItems = cache.getAll();
            cache.purge(connectionFactoryEntry);
            Optional<DiscoveryReturn> maybeDiscoveryReturn = issueDiscovery(cachedItems, connectionFactoryEntry);
            maybeDiscoveryReturn.ifPresent(discoveryReturn -> cache.repopulate(discoveryReturn, connectionFactoryEntry));
        }
    }

    private Optional<DiscoveryReturn> issueDiscovery(Map<CacheType, List<String>> cachedItems, ConnectionFactoryEntry connectionFactoryEntry)
    {
        try(CasualConnection connection = connectionFactoryEntry.getConnectionFactory().getConnection())
        {
            LOG.finest(() -> "domain discovery for all known services/queues will be issued for " + connectionFactoryEntry );
            LOG.finest(() -> "all known services/queues being, services: " + cachedItems.get(CacheType.SERVICE) + " queues: " + cachedItems.get(CacheType.QUEUE));
            DiscoveryReturn discoveryReturn = connection.discover(UUID.randomUUID(),
                    cachedItems.get(CacheType.SERVICE),
                    cachedItems.get(CacheType.QUEUE));
            LOG.finest(() -> "discovery returned: " + discoveryReturn);
            return Optional.of(discoveryReturn);
        }
        catch (Exception e)
        {
            LOG.warning(() -> "failed domain discovery for: " + connectionFactoryEntry + " -> " + e);
            LOG.warning(() -> "services: " + cachedItems.get(CacheType.SERVICE) + " queues: " + cachedItems.get(CacheType.QUEUE));
        }
        return Optional.empty();
    }

}
