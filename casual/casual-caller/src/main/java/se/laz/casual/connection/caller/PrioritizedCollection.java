/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PrioritizedCollection<T>
{
    private final Map<Long, Set<T>> collection = new ConcurrentHashMap<>();

    public List<Long> getPriorities()
    {
        return collection.keySet().stream().sorted().collect(Collectors.toList());
    }

    public List<T> get(Long priority)
    {
        Objects.requireNonNull(priority, "priority can not be null");
        // note:
        // due to threading
        // thread 1 calls getPriorities to get the priorities
        // thread 2 gets to run and removes the entries
        // thread 1 continues and calls this method using the now invalid indexes
        // -> nullptr when using the value via for instance a predicate
        // thus returning empty list in case of none existence
        Set<T> entries = collection.get(priority);
        return null == entries ? Collections.emptyList() : new ArrayList<>(entries);
    }

    public boolean isEmpty()
    {
        return collection.isEmpty();
    }

    public PrioritizedCollection<T> add(Long priority, T entry)
    {
        Objects.requireNonNull(priority, "priority can not be null");
        Objects.requireNonNull(entry, "entry can not be null");
        Set<T> factoriesForPriority =
                collection.computeIfAbsent(priority, mapPriority -> ConcurrentHashMap.newKeySet());
        factoriesForPriority.add(entry);
        return this;
    }

    public PrioritizedCollection<T> add(Long priority, List<T> entries)
    {
        Objects.requireNonNull(priority, "priority can not be null");
        Objects.requireNonNull(entries, "entries can not be null");
        Set<T> factoriesForPriority =
                collection.computeIfAbsent(priority, mapPriority -> ConcurrentHashMap.newKeySet());
        entries.forEach(entry -> {
            if (null != entry)
            {
                factoriesForPriority.add(entry);
            }
        });
        if(factoriesForPriority.isEmpty())
        {
            collection.remove(priority);
        }
        return this;
    }

    public PrioritizedCollection<T> remove(T entryToRemove)
    {
        Objects.requireNonNull(entryToRemove, "entryToRemove can not be null");
        for (Map.Entry<Long, Set<T>> entry : collection.entrySet())
        {
            entry.getValue().removeIf(cachedEntry -> cachedEntry.equals(entryToRemove));
            if (entry.getValue().isEmpty())
            {
                collection.remove(entry.getKey());
            }
        }
        return this;
    }

}
