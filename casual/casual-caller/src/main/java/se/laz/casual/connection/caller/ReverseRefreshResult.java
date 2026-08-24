/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import java.util.List;

/**
 * The result of refreshing the per instance entries of reverse pool backed connection factories.
 * added - entries for newly connected instances, they need domain discovery and a connection observer.
 * purged - entries that must be purged from the caches: entries of instances that are gone as well as
 * base entries that were just classified as reverse pool backed and are no longer served directly.
 */
public record ReverseRefreshResult(List<ConnectionFactoryEntry> added, List<ConnectionFactoryEntry> purged)
{
    public ReverseRefreshResult
    {
        added = List.copyOf(added);
        purged = List.copyOf(purged);
    }
}
