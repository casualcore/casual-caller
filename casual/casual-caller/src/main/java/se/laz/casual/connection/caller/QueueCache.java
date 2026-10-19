/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.api.queue.QueueInfo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class QueueCache
{
    private static final Logger LOG = Logger.getLogger(QueueCache.class.getName());

    private final Map<String, List<ConnectionFactoryEntry>> cacheMap = new ConcurrentHashMap<>();
    private final Map<String, ConnectionFactoryEntry> stickies = new ConcurrentHashMap<>();

    public Set<String> getCachedQueueNames()
    {
        return cacheMap.keySet();
    }

    public List<ConnectionFactoryEntry> getAll(QueueInfo queueInfo)
    {
        return cacheMap.getOrDefault(queueInfo.getQueueName(), List.of());
    }

    public Optional<ConnectionFactoryEntry> getOrEmpty(QueueInfo queueInfo)
    {
        String queueName = queueInfo.getQueueName();
        List<ConnectionFactoryEntry> currentEntries = cacheMap.getOrDefault(queueName, List.of());
        ConnectionFactoryEntry sticky = stickies.get(queueName);
        if (sticky != null && sticky.isValid() && currentEntries.contains(sticky))
        {
            return Optional.of(sticky);
        }
        if (sticky != null)
        {
            stickies.remove(queueName, sticky);
        }

        List<ConnectionFactoryEntry> cachedForQueue = currentEntries.stream()
                .filter(ConnectionFactoryEntry::isValid)
                .toList();

        if (cachedForQueue.isEmpty())
        {
            return Optional.empty();
        }

        // We never expect more than one source for a queue. Just pick the first one and stick to it.
        ConnectionFactoryEntry selectedFactory = cachedForQueue.get(0);
        ConnectionFactoryEntry selectedSticky = stickies.compute(queueName, (key, current) ->
                current == null || !current.isValid() ? selectedFactory : current);

        if (cachedForQueue.size() > 1)
        {
            LOG.info(() -> "Found multiple (" + cachedForQueue.size() + ") sources for queue '" + queueName
                    + "', selecting and setting sticky for CasualConnectionFactory=" + selectedSticky);
        }

        return Optional.of(selectedSticky);
    }

    public void store(QueueInfo queueInfo, List<ConnectionFactoryEntry> entries)
    {
        Objects.requireNonNull(queueInfo, "queueInfo must not be null");
        Objects.requireNonNull(entries, "entries must not be null");
        cacheMap.put(queueInfo.getQueueName(), List.copyOf(entries));
    }

    public void remove(ConnectionFactoryEntry entryToRemove)
    {
        Objects.requireNonNull(entryToRemove, "entryToRemove must not be null");

        for (String queueName : stickies.keySet())
        {
            stickies.computeIfPresent(queueName, (key, current) ->
                    sameFactory(current, entryToRemove) ? null : current);
        }

        for (String queueName : cacheMap.keySet())
        {
            cacheMap.computeIfPresent(queueName, (key, currentEntries) -> {
                List<ConnectionFactoryEntry> remaining = currentEntries.stream()
                        .filter(entry -> !sameFactory(entry, entryToRemove))
                        .toList();
                return remaining.isEmpty() ? null : remaining;
            });
        }
    }

    private static boolean sameFactory(ConnectionFactoryEntry first, ConnectionFactoryEntry second)
    {
        return Objects.equals(first.getJndiName(), second.getJndiName());
    }

    public void clear()
    {
        cacheMap.clear();
        stickies.clear();
    }
}
