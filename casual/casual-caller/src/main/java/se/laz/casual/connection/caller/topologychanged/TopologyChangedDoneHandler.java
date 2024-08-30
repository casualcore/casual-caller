/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged;

import se.laz.casual.jca.DomainId;

import java.util.Objects;

public final class TopologyChangedDoneHandler
{
    private TopologyChangedDoneHandler()
    {}
    public static void execute(TopologyChangedDoneData changedData, DomainId domainId)
    {
        Objects.requireNonNull(changedData, "changedData can not be null");
        Objects.requireNonNull(domainId, "domainId can not be null");
        if(changedData.getWasUpdatedDuringDiscovery().test(domainId))
        {
            // at least one, topology update while domain discovery was running thus we schedule another domain discovery
            changedData.getUpdatedDuringDiscoveryConsumer().accept(domainId);
            changedData.getScheduleFunction().scheduleDiscovery(domainId);
        }
        else
        {
            // got no topology updates while discovery was running, we are done
            changedData.getTopologyChangeHandledConsumer().accept(domainId);
        }
    }
}
