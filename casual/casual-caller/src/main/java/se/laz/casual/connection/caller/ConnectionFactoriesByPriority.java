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
import java.util.Objects;
import java.util.Set;

/**
 * Stores connection factory entries by priority as an immutable value.
 *
 * <p>Update operations return a value and never modify the current instance. The contained
 * {@link ConnectionFactoryEntry} instances retain their mutable operational state, but membership, priorities, and
 * resolved-factory metadata never change after construction. You can safely share instances between threads.
 */
public final class ConnectionFactoriesByPriority
{
    private static final ConnectionFactoriesByPriority EMPTY = new ConnectionFactoriesByPriority(
            new PrioritizedCollection<>(),
            Set.of());

    private final PrioritizedCollection<ConnectionFactoryEntry> prioritizedEntries;
    private final Set<String> checkedConnectionFactories;

    private ConnectionFactoriesByPriority(PrioritizedCollection<ConnectionFactoryEntry> prioritizedEntries,
                                          Set<String> checkedConnectionFactories)
    {
        this.prioritizedEntries = Objects.requireNonNull(prioritizedEntries, "prioritizedEntries must not be null");
        this.checkedConnectionFactories = Set.copyOf(Objects.requireNonNull(checkedConnectionFactories, "checkedConnectionFactories must not be null"));
    }

    public List<Long> getOrderedKeys()
    {
        return prioritizedEntries.getPriorities();
    }

    public List<ConnectionFactoryEntry> getForPriority(Long priority)
    {
        Objects.requireNonNull(priority, "priority can not be null");
        return prioritizedEntries.get(priority);
    }

    /**
     * Returns whether this value contains prioritized connection factory entries.
     *
     * <p>A value without prioritized entries can still contain resolved-factory metadata.
     *
     * @return {@code true} when at least one prioritized entry is present
     */
    public boolean hasPrioritizedEntries()
    {
        return !prioritizedEntries.isEmpty();
    }

    /**
     * Returns a value containing the supplied resolved factories.
     *
     * @param resolvedNames the resolved factory names to add
     * @return a value containing the existing and supplied resolved factories
     * @throws NullPointerException if {@code resolvedNames} is {@code null}
     */
    public ConnectionFactoriesByPriority withResolvedFactories(Collection<String> resolvedNames)
    {
        Objects.requireNonNull(resolvedNames, "resolvedNames can not be null");
        if (checkedConnectionFactories.containsAll(resolvedNames))
        {
            return this;
        }
        Set<String> updated = new HashSet<>(checkedConnectionFactories);
        updated.addAll(resolvedNames);
        return new ConnectionFactoriesByPriority(prioritizedEntries, updated);
    }

    /**
     * Returns a value containing a resolved factory.
     *
     * @param entryName the resolved factory name to add
     * @return a value containing the existing resolved factories and the supplied factory
     * @throws NullPointerException if {@code entryName} is {@code null}
     */
    public ConnectionFactoriesByPriority withResolvedFactory(String entryName)
    {
        Objects.requireNonNull(entryName, "entryName can not be null");
        return withResolvedFactories(Set.of(entryName));
    }

    public boolean containsCheckedConnectionFactories()
    {
        return !checkedConnectionFactories.isEmpty();
    }

    public Set<String> getCheckedFactoriesForService()
    {
        return checkedConnectionFactories;
    }

    /**
     * Returns a value containing an entry for each supplied service detail.
     *
     * <p>If an equal entry already exists, this method replaces it with the supplied instance. If
     * {@code serviceDetails} is empty, this method returns the current value unchanged.
     *
     * @param serviceDetails the discovered service details
     * @param entry the connection factory entry that supplied the details
     * @return a value containing the supplied service mappings
     * @throws NullPointerException if {@code serviceDetails} or {@code entry} is {@code null}
     */
    public ConnectionFactoriesByPriority withServices(List<ServiceDetails> serviceDetails,
                                                      ConnectionFactoryEntry entry)
    {
        Objects.requireNonNull(serviceDetails, "serviceDetails can not be null");
        Objects.requireNonNull(entry, "ConnectionFactoryEntry can not be null");
        if (serviceDetails.isEmpty())
        {
            return this;
        }

        PrioritizedCollection<ConnectionFactoryEntry> updatedEntries = prioritizedEntries.without(entry);
        for (ServiceDetails details : serviceDetails)
        {
            updatedEntries = updatedEntries.with(details.getHops(), entry);
        }
        Set<String> updatedCheckedFactories = new HashSet<>(checkedConnectionFactories);
        updatedCheckedFactories.add(entry.getJndiName());
        return new ConnectionFactoriesByPriority(updatedEntries, updatedCheckedFactories);
    }

    /**
     * Returns a value containing entries at the specified priority.
     *
     * @param priority the priority at which to store the entries
     * @param entries the entries to store
     * @return a value containing the existing and supplied entries
     * @throws NullPointerException if {@code priority} or {@code entries} is {@code null}
     */
    public ConnectionFactoriesByPriority withEntries(Long priority,
                                                     Collection<? extends ConnectionFactoryEntry> entries)
    {
        Objects.requireNonNull(priority, "priority can not be null");
        Objects.requireNonNull(entries, "entries can not be null");
        PrioritizedCollection<ConnectionFactoryEntry> updatedEntries = prioritizedEntries.with(priority, entries);
        return updatedEntries == prioritizedEntries
                ? this
                : new ConnectionFactoriesByPriority(updatedEntries, checkedConnectionFactories);
    }

    public boolean isResolved(String entryName)
    {
        Objects.requireNonNull(entryName, "entryName can not be null");
        return checkedConnectionFactories.contains(entryName);
    }

    public boolean hasCheckedAllValid(List<ConnectionFactoryEntry> entries)
    {
        Objects.requireNonNull(entries, "entries can not be null");
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
     * Returns entries ordered by priority and randomized within each priority.
     *
     * <p>If the same {@link ConnectionFactoryEntry} appears at multiple priorities, this method returns it only at its
     * highest priority.
     *
     * @return the flattened entries ordered and randomized by priority
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
                if (null != cfe && !entriesRandomOrderByPriority.contains(cfe))
                {
                    entriesRandomOrderByPriority.add(cfe);
                }
            }
        }
        return entriesRandomOrderByPriority;
    }

    /**
     * Returns the empty value.
     *
     * @return the empty value
     */
    public static ConnectionFactoriesByPriority emptyInstance()
    {
        return EMPTY;
    }

    ConnectionFactoriesByPriority mergeReplacing(ConnectionFactoriesByPriority entries)
    {
        Objects.requireNonNull(entries, "entries must not be null");
        PrioritizedCollection<ConnectionFactoryEntry> updatedEntries =
                prioritizedEntries.mergeReplacing(entries.prioritizedEntries);
        Set<String> updatedCheckedFactories = new HashSet<>(checkedConnectionFactories);
        updatedCheckedFactories.addAll(entries.checkedConnectionFactories);
        return new ConnectionFactoriesByPriority(updatedEntries, updatedCheckedFactories);
    }

    /**
     * Returns a value without an entry or its resolved-factory metadata.
     *
     * @param entry the entry to remove
     * @return a value without the supplied entry
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    public ConnectionFactoriesByPriority withoutEntry(ConnectionFactoryEntry entry)
    {
        Objects.requireNonNull(entry, "entry must not be null");
        PrioritizedCollection<ConnectionFactoryEntry> updatedEntries = prioritizedEntries.without(entry);
        Set<String> updatedCheckedFactories = new HashSet<>(checkedConnectionFactories);
        updatedCheckedFactories.remove(entry.getJndiName());
        return new ConnectionFactoriesByPriority(updatedEntries, updatedCheckedFactories);
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
        ConnectionFactoriesByPriority that = (ConnectionFactoriesByPriority) o;
        return prioritizedEntries.equals(that.prioritizedEntries)
                && checkedConnectionFactories.equals(that.checkedConnectionFactories);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(prioritizedEntries, checkedConnectionFactories);
    }

    @Override
    public String toString()
    {
        return "ConnectionFactoriesByPriority{" +
                "prioritizedEntries=" + prioritizedEntries +
                ", checkedConnectionFactories=" + checkedConnectionFactories +
                '}';
    }
}
