/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

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
        for (Long priority : entries.getOrderedKeys())
        {
            storeServiceWithPriority(serviceName, priority, entries.getForPriority(priority));
        }

        // Guard against service lookups that only contain checked services list for a service that is unknown
        // We do not want to store unknown services
        cacheMap.computeIfPresent(serviceName, (name, existing) -> {
            existing.addResolvedFactories(entries.getCheckedFactoriesForService());
            return existing;
        });
    }

    private void storeServiceWithPriority(String serviceName, Long priority, List<ConnectionFactoryEntry> entries)
    {
        Objects.requireNonNull(serviceName, "serviceName can not be null");
        Objects.requireNonNull(serviceName, "priority can not be null");
        Objects.requireNonNull(entries, "entries can not be null");

        // Ensure service exists
        ConnectionFactoriesByPriority mapForService =
                cacheMap.computeIfAbsent(serviceName, mapServiceName -> ConnectionFactoriesByPriority.emptyInstance());

        mapForService.store(priority, entries);
    }

    public void remove(ConnectionFactoryEntry connectionFactoryEntry)
    {
        for (Map.Entry<String, ConnectionFactoriesByPriority> cachedEntry : cacheMap.entrySet())
        {
            cachedEntry.getValue().remove(connectionFactoryEntry);
            if(cachedEntry.getValue().isEmpty())
            {
                cacheMap.remove(cachedEntry.getKey());
            }
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
