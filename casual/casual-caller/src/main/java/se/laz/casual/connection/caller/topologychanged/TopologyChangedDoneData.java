/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged;

import se.laz.casual.jca.DomainId;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TopologyChangedDoneData
{
    private final Predicate<DomainId> wasUpdatedDuringDiscovery;
    private final Consumer<DomainId> topologyChangeHandledConsumer;
    private final Consumer<DomainId> updatedDuringDiscoveryConsumer;
    private final ScheduleFunction scheduleFunction;

    public static Builder createBuilder()
    {
        return new Builder();
    }

    private TopologyChangedDoneData(Builder builder)
    {
        wasUpdatedDuringDiscovery = builder.wasUpdatedDuringDiscovery;
        topologyChangeHandledConsumer = builder.topologyChangeHandledConsumer;
        updatedDuringDiscoveryConsumer = builder.updatedDuringDiscoveryConsumer;
        scheduleFunction = builder.scheduleFunction;
    }

    public Predicate<DomainId> getWasUpdatedDuringDiscovery()
    {
        return wasUpdatedDuringDiscovery;
    }

    public Consumer<DomainId> getTopologyChangeHandledConsumer()
    {
        return topologyChangeHandledConsumer;
    }

    public Consumer<DomainId> getUpdatedDuringDiscoveryConsumer()
    {
        return updatedDuringDiscoveryConsumer;
    }

    public ScheduleFunction getScheduleFunction()
    {
        return scheduleFunction;
    }

    public static final class Builder
    {
        private Predicate<DomainId> wasUpdatedDuringDiscovery;
        private Consumer<DomainId> topologyChangeHandledConsumer;
        private Consumer<DomainId> updatedDuringDiscoveryConsumer;
        private ScheduleFunction scheduleFunction;

        private Builder()
        {
        }

        public static Builder newBuilder()
        {
            return new Builder();
        }

        public Builder withWasUpdatedDuringDiscovery(Predicate<DomainId> wasUpdatedDuringDiscovery)
        {
            this.wasUpdatedDuringDiscovery = wasUpdatedDuringDiscovery;
            return this;
        }

        public Builder withTopologyChangeHandledConsumer(Consumer<DomainId> topologyChangeHandledConsumer)
        {
            this.topologyChangeHandledConsumer = topologyChangeHandledConsumer;
            return this;
        }

        public Builder withUpdatedDuringDiscoveryConsumer(Consumer<DomainId> updatedDuringDiscoveryConsumer)
        {
            this.updatedDuringDiscoveryConsumer = updatedDuringDiscoveryConsumer;
            return this;
        }

        public Builder withScheduleFunction(ScheduleFunction scheduleFunction)
        {
            this.scheduleFunction = scheduleFunction;
            return this;
        }

        public TopologyChangedDoneData build()
        {
            return new TopologyChangedDoneData(this);
        }
    }
}
