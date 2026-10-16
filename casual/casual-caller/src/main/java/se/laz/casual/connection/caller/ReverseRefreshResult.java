/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import java.util.List;

/**
 * Contains the result of refreshing the domain-pinned entries for reverse outbound connection factories.
 *
 * @param added entries for newly connected domains that require discovery and a connection observer
 * @param purged entries for disconnected domains that require removal from the caches
 */
public record ReverseRefreshResult(List<ConnectionFactoryEntry> added, List<ConnectionFactoryEntry> purged)
{
    public ReverseRefreshResult
    {
        added = List.copyOf(added);
        purged = List.copyOf(purged);
    }
}
