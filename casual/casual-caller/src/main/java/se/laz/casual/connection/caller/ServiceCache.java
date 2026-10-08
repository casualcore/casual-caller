/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.service.ServiceDetails;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceCache
{
    private final Map<String, ConnectionFactoriesByPriority> cacheMap = new ConcurrentHashMap<>();

    public Set<String> getCachedServiceNames()
    {
        return cacheMap.keySet();
    }

    public ConnectionFactoriesByPriority getOrEmpty(String serviceName)
    {
        return cacheMap.getOrDefault(serviceName, ConnectionFactoriesByPriority.emptyInstance());
    }

    public void store(String serviceName, ConnectionFactoriesByPriority entries)
    {
        Objects.requireNonNull(serviceName, "serviceName must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        if (entries.hasPrioritizedEntries())
        {
            cacheMap.merge(serviceName, entries, ConnectionFactoriesByPriority::mergeReplacing);
            return;
        }

        // If discovery finds no providers, entries can still identify the factories checked for this service.
        // To avoid caching an unknown service, merge this metadata only when the service is already cached.
        if (entries.containsCheckedConnectionFactories())
        {
            cacheMap.computeIfPresent(serviceName,
                    (key, current) -> current.mergeReplacing(entries));
        }
    }

    void store(ServiceDetails serviceDetails, ConnectionFactoryEntry entry)
    {
        Objects.requireNonNull(serviceDetails, "serviceDetails must not be null");
        Objects.requireNonNull(entry, "entry must not be null");
        ConnectionFactoriesByPriority discovered = ConnectionFactoriesByPriority.emptyInstance()
                .withServices(List.of(serviceDetails), entry);
        cacheMap.merge(serviceDetails.getName(), discovered, ConnectionFactoriesByPriority::mergeReplacing);
    }

    public void remove(ConnectionFactoryEntry entryToRemove)
    {
        Objects.requireNonNull(entryToRemove, "entryToRemove must not be null");
        for (String serviceName : cacheMap.keySet())
        {
            cacheMap.computeIfPresent(serviceName, (key, current) -> {
                ConnectionFactoriesByPriority updated = current.withoutEntry(entryToRemove);
                return updated.hasPrioritizedEntries() ? updated : null;
            });
        }
    }

    public void clear()
    {
        cacheMap.clear();
    }

    public void remove(String serviceName)
    {
        cacheMap.remove(serviceName);
    }
}
