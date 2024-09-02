/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged

import se.laz.casual.jca.DomainId
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap

class TopologyChangedDoneHandlerTest extends Specification
{
   Set<DomainId> changedDomains = ConcurrentHashMap.newKeySet();
   Set<DomainId> updateRequestDuringDiscovery = ConcurrentHashMap.newKeySet();
   DomainId domainId = DomainId.of(UUID.randomUUID())
   def setup()
   {
      changedDomains.clear()
      updateRequestDuringDiscovery.clear()
   }
   def 'no update during discovery'()
   {
      given:
      changedDomains.add(domainId)
      when:
      ScheduleFunction scheduleFunction = Mock(ScheduleFunction){
         0 * scheduleDiscovery(domainId)
      }
      TopologyChangedDoneHandler.execute(TopologyChangedDoneData.createBuilder()
              .withWasUpdatedDuringDiscovery(updateRequestDuringDiscovery::contains)
              .withUpdatedDuringDiscoveryConsumer(updateRequestDuringDiscovery::remove)
              .withTopologyChangeHandledConsumer(changedDomains::remove)
              .withScheduleFunction(scheduleFunction)
              .build(), domainId);
      then:
      !changedDomains.contains(domainId)
      !updateRequestDuringDiscovery.contains(domainId)
   }

   def 'update during discovery'()
   {
      given:
      changedDomains.add(domainId)
      updateRequestDuringDiscovery.add(domainId)
      when:
      ScheduleFunction scheduleFunction = Mock(ScheduleFunction){
         1 * scheduleDiscovery(domainId)
      }
      TopologyChangedDoneHandler.execute(TopologyChangedDoneData.createBuilder()
              .withWasUpdatedDuringDiscovery(updateRequestDuringDiscovery::contains)
              .withUpdatedDuringDiscoveryConsumer(updateRequestDuringDiscovery::remove)
              .withTopologyChangeHandledConsumer(changedDomains::remove)
              .withScheduleFunction(scheduleFunction)
              .build(), domainId);
      then:
      changedDomains.contains(domainId)
      !updateRequestDuringDiscovery.contains(domainId)
   }

}
