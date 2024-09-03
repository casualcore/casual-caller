/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged;

import se.laz.casual.jca.DomainId;

@FunctionalInterface
public interface ScheduleFunction
{
    void scheduleDiscovery(DomainId domainId);
}
