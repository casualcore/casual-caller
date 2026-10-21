/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.api.discovery.DiscoveryReturn;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
            // This discovery also satisfies a fallback request recorded before the lock was acquired.
            connectionFactoryEntry.claimDomainDiscovery();
            repopulateCache(connectionFactoryEntry);
        }
    }

    public void repopulateIfRequired(ConnectionFactoryEntry connectionFactoryEntry)
    {
        if (!connectionFactoryEntry.isDomainDiscoveryRequired())
        {
            return;
        }
        synchronized (repopulateLock)
        {
            // Recheck while holding the discovery lock because several callers can observe the pending request.
            if (!connectionFactoryEntry.claimDomainDiscovery())
            {
                return;
            }
            repopulateCache(connectionFactoryEntry);
        }
    }

    private void repopulateCache(ConnectionFactoryEntry connectionFactoryEntry)
    {
        // Run discovery for all known cache items, to potentially aid load balancing.
        Map<CacheType, List<String>> cachedItems = cache.getAll();
        Optional<DiscoveryReturn> maybeDiscoveryReturn = transactionLess.discover(connectionFactoryEntry, cachedItems);
        maybeDiscoveryReturn.ifPresent(discoveryReturn -> cache.repopulate(discoveryReturn, connectionFactoryEntry));
    }

}
