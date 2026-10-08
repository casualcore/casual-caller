/*
 * Copyright (c) 2024 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged;

import se.laz.casual.jca.DomainId;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates topology discovery scheduling for each domain.
 *
 * <p>This class is thread-safe. It coalesces any number of updates received while discovery is scheduled or running
 * into one follow-up discovery.
 */
final class TopologyChangedDoneHandler
{
    private final Map<DomainId, DiscoveryState> discoveries = new ConcurrentHashMap<>();

    /**
     * Records a topology update and indicates whether you need to schedule discovery.
     *
     * @param domainId the domain whose topology changed
     * @return {@code true} when you need to schedule discovery, or {@code false} when an existing discovery handles it
     */
    boolean topologyChanged(DomainId domainId)
    {
        Objects.requireNonNull(domainId, "domainId can not be null");
        DiscoveryState resultingState = discoveries.merge(
                domainId,
                DiscoveryState.SCHEDULED,
                (currentState, scheduledState) -> currentState == DiscoveryState.IDLE
                        ? scheduledState
                        : DiscoveryState.RESCHEDULE_REQUIRED);
        return resultingState == DiscoveryState.SCHEDULED;
    }

    /**
     * Completes discovery and indicates whether an update requires another discovery.
     *
     * @param domainId the domain whose discovery completed
     * @return {@code true} when you need to schedule another discovery, or {@code false} when handling is complete
     */
    boolean topologyChangeHandled(DomainId domainId)
    {
        Objects.requireNonNull(domainId, "domainId can not be null");
        DiscoveryState resultingState = discoveries.merge(
                domainId,
                DiscoveryState.IDLE,
                (currentState, idleState) -> currentState == DiscoveryState.RESCHEDULE_REQUIRED
                        ? DiscoveryState.SCHEDULED
                        : idleState);
        boolean discoveryRequired = resultingState == DiscoveryState.SCHEDULED;
        if (!discoveryRequired)
        {
            // Remove inactive domains without deleting a discovery scheduled concurrently.
            discoveries.remove(domainId, DiscoveryState.IDLE);
        }
        return discoveryRequired;
    }

    /**
     * Clears the scheduled state after the executor rejects a discovery task.
     *
     * @param domainId the domain whose discovery task was rejected
     */
    void schedulingFailed(DomainId domainId)
    {
        Objects.requireNonNull(domainId, "domainId can not be null");
        discoveries.remove(domainId);
    }

}
