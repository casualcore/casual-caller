/*
 * Copyright (c) 2017 - 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.inject.Inject;
import se.laz.casual.api.queue.QueueInfo;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ConnectionFactoryLookupService implements ConnectionFactoryLookup
{
    private ConnectionFactoryEntryStore connectionFactoryProvider;
    private Cache cache;
    private Lookup lookup;
    private TransactionLess transactionLess;

    @Inject
    public ConnectionFactoryLookupService(ConnectionFactoryEntryStore connectionFactoryProvider, Cache cache, Lookup lookup,
                                          TransactionLess transactionLess)
    {
        this.connectionFactoryProvider = connectionFactoryProvider;
        this.cache = cache;
        this.lookup = lookup;
        this.transactionLess = transactionLess;
    }

    @Override
    public Optional<ConnectionFactoryEntry> get(QueueInfo qinfo)
    {
        Objects.requireNonNull(qinfo, "qinfo can not be null");
        Optional<ConnectionFactoryEntry> cachedEntry = cache.getSingle(qinfo);
        if (cachedEntry.isPresent())
        {
            return cachedEntry;
        }
        List<ConnectionFactoryEntry> newEntries = lookup.find(qinfo, connectionFactoryProvider.get(), transactionLess);
        if (!newEntries.isEmpty())
        {
            cache.store(qinfo, newEntries);
        }
        return cache.getSingle(qinfo); // May be something or empty depending on if
    }

    @Override
    public List<ConnectionFactoryEntry> get(String serviceName)
    {
        Objects.requireNonNull(serviceName, "serviceName can not be null");
        List<ConnectionFactoryEntry> possibleConnectionFactories = connectionFactoryProvider.get();
        ConnectionFactoriesByPriority cachedEntries = cache.get(serviceName);
        if (!cachedEntries.isEmpty() && cachedEntries.hasCheckedAllValid(possibleConnectionFactories))
        {
            // Using cached entries and no further discovery is appropriate
            return cachedEntries.randomizeWithPriority();
        }

        // Services by lookup. Only lookup against previously unresolved connection factories.
        ConnectionFactoriesByPriority newEntries = lookup.find(serviceName, possibleConnectionFactories
                .stream()
                .filter(entry -> !cache.get(serviceName).isResolved(entry.getJndiName()))
                .toList(), transactionLess);
        if (!newEntries.isEmpty() || newEntries.containsCheckedConnectionFactories())
        {
            cache.store(serviceName, newEntries);
            return cache.get(serviceName).randomizeWithPriority();
        }

        // If we only have a bunch of invalid connection-factories to report it should be done so,
        // because a different error may be reported depending on if the service has no known backend
        // or if none of the known backends are available
        return cachedEntries.isEmpty() ? Collections.emptyList() : cachedEntries.randomizeWithPriority();
    }

    @Override
    public void removeFromServiceCache(String serviceName)
    {
        cache.removeService(serviceName);
    }
}
