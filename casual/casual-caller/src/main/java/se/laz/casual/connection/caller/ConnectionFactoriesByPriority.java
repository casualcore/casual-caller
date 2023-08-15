/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.service.ServiceDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

 public class ConnectionFactoriesByPriority
{
    private final Object connectionFactoryEntryListLock = new Object();
    private final Map<Long, List<ConnectionFactoryEntry>> mapping = new ConcurrentHashMap<>();
    private final Set<String> checkedConnectionFactories = new HashSet<>();

    private ConnectionFactoriesByPriority()
    {
    }

    public List<Long> getOrderedKeys()
    {
        return mapping.keySet().stream().sorted().collect(Collectors.toList());
    }

    public List<ConnectionFactoryEntry> getForPriority(Long priority)
    {
        // note:
        // due to threading
        // thread 1 calls getOrderedKeys to get the priorities
        // thread 2 gets to run and removes the entries
        // thread 1 continues and calls this method using the now invalid indexes
        // -> nullptr when using the value via for instance a predicate
        // thus returning empty list in case of none existence
        synchronized (connectionFactoryEntryListLock)
        {
            List<ConnectionFactoryEntry> entries = mapping.get(priority);
            return null == entries ? Collections.emptyList() : new ArrayList<>(entries);
        }
    }

    public boolean isEmpty()
    {
        return mapping.isEmpty();
    }

    public void addResolvedFactories(Collection<String> resolvedNames)
    {
        checkedConnectionFactories.addAll(resolvedNames);
    }

    public boolean containsCheckedConnectionFactories()
    {
        return !checkedConnectionFactories.isEmpty();
    }

    public Set<String> getCheckedFactoriesForService()
    {
        return Collections.unmodifiableSet(checkedConnectionFactories);
    }

    public void store(List<ServiceDetails> serviceDetails, ConnectionFactoryEntry entry)
    {
        if (!serviceDetails.isEmpty())
        {
            checkedConnectionFactories.add(entry.getJndiName());
            serviceDetails
                    .forEach(discoveryDetails ->
                    {
                        Long priority = discoveryDetails.getHops();

                        // Ensure a list of entries exists for the priority if none have been added previously
                        List<ConnectionFactoryEntry> factoriesForPriority =
                                mapping.computeIfAbsent(priority, mapPriority -> new ArrayList<>());

                        // Add current entry for this given priority for the service.
                        synchronized (connectionFactoryEntryListLock)
                        {
                            if (!factoriesForPriority.contains(entry))
                            {
                                factoriesForPriority.add(entry);
                            }
                        }
                    });
        }
    }
    public void store(Long priority, List<ConnectionFactoryEntry> entries)
    {
        // Ensure priority level exists
        List<ConnectionFactoryEntry> listForPriority =
                mapping.computeIfAbsent(priority, mapPrio -> new ArrayList<>());

        // Add missing connection factories for this service and priority
        for (ConnectionFactoryEntry entry : entries)
        {
            synchronized (connectionFactoryEntryListLock)
            {
                if (null != entry && !listForPriority.contains(entry))
                {
                    listForPriority.add(entry);
                }
            }
        }
        if(isEmpty(listForPriority))
        {
            mapping.remove(priority);
        }
    }

    public boolean isResolved(String entryName)
    {
        return checkedConnectionFactories.contains(entryName);
    }

    public void setResolved(String entryName)
    {
        checkedConnectionFactories.add(entryName);
    }

    public boolean hasCheckedAllValid(List<ConnectionFactoryEntry> entries)
    {
        for (ConnectionFactoryEntry entry : entries)
        {
            if (!checkedConnectionFactories.contains(entry.getJndiName()) && entry.isValid())
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Flatten entries and randomize per priority level. If the same @{@link ConnectionFactoryEntry} appears in multiple
     * priorities only the highest priority entry is returned.
     *
     * @return Flattened and slightly randomized variant of input.
     */
    public List<ConnectionFactoryEntry> randomizeWithPriority()
    {
        List<ConnectionFactoryEntry> entriesRandomOrderByPriority = new ArrayList<>();

        List<Long> orderedKeys = getOrderedKeys();
        for (Long priority : orderedKeys)
        {
            List<ConnectionFactoryEntry> factoriesForPriority = getForPriority(priority);
            Collections.shuffle(factoriesForPriority);

            for (ConnectionFactoryEntry cfe : factoriesForPriority)
            {
                // note:
                // this null check is needed since another thread may have called remove
                // after this method called getOrderedKeys
                // in that case getForPriority will return an ArrayList with a null element
                if (null != cfe && !entriesRandomOrderByPriority.contains(cfe))
                {
                    entriesRandomOrderByPriority.add(cfe);
                }
            }
        }
        return entriesRandomOrderByPriority;
    }

    public static ConnectionFactoriesByPriority of(Map<Long, List<ConnectionFactoryEntry>> entries, Collection<String> resolved)
    {
        Objects.requireNonNull(entries, "Must supply entries to add");
        Objects.requireNonNull(resolved, "Must supply resolved connection factory entries to add");

        ConnectionFactoriesByPriority mapping = new ConnectionFactoriesByPriority();
        for (Map.Entry<Long, List<ConnectionFactoryEntry>> entry : entries.entrySet())
        {
            mapping.store(entry.getKey(), entry.getValue());
        }

        mapping.addResolvedFactories(resolved);

        return mapping;
    }

    public static ConnectionFactoriesByPriority of(Map<Long, List<ConnectionFactoryEntry>> entries)
    {
        return ConnectionFactoriesByPriority.of(entries, Collections.emptyList());
    }

    /**
     * Constructs a new empty instance of @{@link ConnectionFactoriesByPriority}.
     *
     * @return A new, empty, instance
     */
    public static ConnectionFactoriesByPriority emptyInstance()
    {
        return new ConnectionFactoriesByPriority();
    }

    public void remove(ConnectionFactoryEntry connectionFactoryEntry)
    {
        checkedConnectionFactories.remove(connectionFactoryEntry.getJndiName());
        for(Map.Entry<Long, List<ConnectionFactoryEntry>> entry : mapping.entrySet())
        {
            synchronized (connectionFactoryEntryListLock)
            {
                entry.getValue().removeIf(cachedConnectionFactoryEntry -> cachedConnectionFactoryEntry.getJndiName().equals(connectionFactoryEntry.getJndiName()));
            }
            if(isEmpty(entry.getValue()))
            {
                mapping.remove(entry.getKey());
            }
        }
    }

    private boolean isEmpty(List<ConnectionFactoryEntry> entries)
    {
        synchronized (connectionFactoryEntryListLock)
        {
            return entries.isEmpty();
        }
    }
}
