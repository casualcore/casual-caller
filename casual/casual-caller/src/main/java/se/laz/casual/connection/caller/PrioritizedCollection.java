/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
/**
 * Stores values by priority as an immutable value.
 *
 * <p>Update operations return a value and never modify the current instance. The class is thread-safe when its
 * contained values are thread-safe.
 *
 * @param <T> the stored value type
 */
public final class PrioritizedCollection<T>
{
    private static final String PRIORITY_CAN_NOT_BE_NULL = "priority can not be null";
    private final Map<Long, Set<T>> collection;

    /**
     * Creates an empty collection.
     */
    public PrioritizedCollection()
    {
        collection = Map.of();
    }

    private PrioritizedCollection(Map<Long, Set<T>> collection)
    {
        this.collection = Map.copyOf(collection);
    }

    public List<Long> getPriorities()
    {
        return collection.keySet().stream().sorted().toList();
    }

    public List<T> get(Long priority)
    {
        Objects.requireNonNull(priority, PRIORITY_CAN_NOT_BE_NULL);
        Set<T> entries = collection.get(priority);
        return null == entries ? Collections.emptyList() : new ArrayList<>(entries);
    }

    public boolean isEmpty()
    {
        return collection.isEmpty();
    }

    /**
     * Returns a value that contains an entry at the specified priority.
     *
     * @param priority the priority at which to store the entry
     * @param entry the entry to store
     * @return a value containing the existing entries and the supplied entry
     * @throws NullPointerException if {@code priority} or {@code entry} is {@code null}
     */
    public PrioritizedCollection<T> with(Long priority, T entry)
    {
        Objects.requireNonNull(entry, "entry can not be null");
        return with(priority, List.of(entry));
    }

    /**
     * Returns a value that contains entries at the specified priority.
     *
     * <p>This method ignores {@code null} elements to preserve the existing collection contract.
     *
     * @param priority the priority at which to store the entries
     * @param entries the entries to store
     * @return a value containing the existing entries and the supplied entries
     * @throws NullPointerException if {@code priority} or {@code entries} is {@code null}
     */
    public PrioritizedCollection<T> with(Long priority, Collection<? extends T> entries)
    {
        Objects.requireNonNull(priority, PRIORITY_CAN_NOT_BE_NULL);
        Objects.requireNonNull(entries, "entries can not be null");

        Set<T> updatedEntries = new HashSet<>(collection.getOrDefault(priority, Set.of()));
        boolean changed = false;
        for (T entry : entries)
        {
            if (entry != null)
            {
                changed |= updatedEntries.add(entry);
            }
        }
        if (!changed)
        {
            return this;
        }

        Map<Long, Set<T>> updated = new HashMap<>(collection);
        updated.put(priority, Set.copyOf(updatedEntries));
        return new PrioritizedCollection<>(updated);
    }

    /**
     * Returns a value without an entry at any priority.
     *
     * @param entryToRemove the entry to remove
     * @return a value without the supplied entry
     * @throws NullPointerException if {@code entryToRemove} is {@code null}
     */
    public PrioritizedCollection<T> without(T entryToRemove)
    {
        Objects.requireNonNull(entryToRemove, "entryToRemove can not be null");
        Map<Long, Set<T>> updated = new HashMap<>();
        collection.forEach((priority, entries) -> {
            Set<T> remaining = new HashSet<>(entries);
            remaining.remove(entryToRemove);
            if (!remaining.isEmpty())
            {
                updated.put(priority, Set.copyOf(remaining));
            }
        });
        return updated.equals(collection) ? this : new PrioritizedCollection<>(updated);
    }

    /**
     * Returns a value that replaces equal entries with entries from another collection.
     *
     * @param entries the entries to merge
     * @return the merged value
     * @throws NullPointerException if {@code entries} is {@code null}
     */
    public PrioritizedCollection<T> mergeReplacing(PrioritizedCollection<T> entries)
    {
        Objects.requireNonNull(entries, "entries can not be null");
        PrioritizedCollection<T> updated = this;
        Set<T> replacements = new HashSet<>();
        entries.collection.values().forEach(replacements::addAll);
        for (T replacement : replacements)
        {
            updated = updated.without(replacement);
        }
        for (Map.Entry<Long, Set<T>> priority : entries.collection.entrySet())
        {
            updated = updated.with(priority.getKey(), priority.getValue());
        }
        return updated;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        PrioritizedCollection<?> that = (PrioritizedCollection<?>) o;
        return collection.equals(that.collection);
    }

    @Override
    public int hashCode()
    {
        return collection.hashCode();
    }

    @Override
    public String toString()
    {
        return collection.toString();
    }
}
