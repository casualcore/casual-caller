/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.api.discovery.DiscoveryReturn;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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
        // note:
        // We only ever want one pool at a time do handle domain discovery.
        // This so that we do not end up with a scenario where we have multiple discoveries in flight
        // and the known state of the world, cache, is currently very small when another discovery is issued
        synchronized (repopulateLock)
        {
            Map<CacheType, List<String>> cachedItems = cache.getAll();
            cache.purge(connectionFactoryEntry);
            Optional<DiscoveryReturn> maybeDiscoveryReturn = transactionLess.discover(connectionFactoryEntry, cachedItems);
            maybeDiscoveryReturn.ifPresent(discoveryReturn -> cache.repopulate(discoveryReturn, connectionFactoryEntry));
        }
    }

}
