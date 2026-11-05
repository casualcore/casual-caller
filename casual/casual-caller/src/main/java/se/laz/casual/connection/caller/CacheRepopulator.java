/*
 * Copyright (c) 2023 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import se.laz.casual.api.discovery.DiscoveryReturn;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CacheRepopulator
{
    private Cache cache;
    private TransactionLess transactionLess;
    private final Object repopulateLock = new Object();
    // WLS - no arg constructor
    public CacheRepopulator()
    {}

    @Inject
    public CacheRepopulator(Cache cache, TransactionLess transactionLess)
    {
        this.cache = cache;
        this.transactionLess = transactionLess;
    }

    public void repopulate(ConnectionFactoryEntry connectionFactoryEntry)
    {
        synchronized (repopulateLock)
        {
            repopulateCache(connectionFactoryEntry);
        }
    }

    private void repopulateCache(ConnectionFactoryEntry connectionFactoryEntry)
    {
        // Run discovery for all known cache items, to potentially aid load balancing.
        Map<CacheType, List<String>> cachedItems = cache.getAll();

        if( noCachedItems( cachedItems ) )
        {
            return;
        }

        Optional<DiscoveryReturn> maybeDiscoveryReturn = transactionLess.discover(connectionFactoryEntry, cachedItems);
        maybeDiscoveryReturn.ifPresent(discoveryReturn -> cache.repopulate(discoveryReturn, connectionFactoryEntry));
    }

    private boolean noCachedItems( Map<CacheType, List<String>> cachedItems )
    {
        int items = 0;
        for( List<String> values : cachedItems.values() )
        {
            items += values.size();
        }
        return items == 0;
    }

}
