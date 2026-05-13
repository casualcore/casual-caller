/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import se.laz.casual.jca.DomainId;

import java.util.List;
import java.util.function.Supplier;

public interface TopologyChangedHandler
{
    void setSupplier(Supplier<List<ConnectionFactoryEntry>> connectionFactoryEntrySupplier);
    void topologyChanged(DomainId domainId);
}
