/*
 * Copyright (c) 2024 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged

import se.laz.casual.jca.DomainId
import spock.lang.Specification

class TopologyChangedDoneHandlerTest extends Specification
{
   DomainId domainId = DomainId.of(UUID.randomUUID())

   def 'no update during discovery completes topology handling'()
   {
      given:
      TopologyChangedDoneHandler instance = new TopologyChangedDoneHandler()

      expect:
      instance.topologyChanged(domainId)
      !instance.topologyChangeHandled(domainId)
      instance.topologyChanged(domainId)
   }

   def 'updates during discovery are coalesced into one follow-up discovery'()
   {
      given:
      TopologyChangedDoneHandler instance = new TopologyChangedDoneHandler()

      expect: 'the first update schedules discovery'
      instance.topologyChanged(domainId)

      and: 'subsequent updates only request a follow-up'
      !instance.topologyChanged(domainId)
      !instance.topologyChanged(domainId)

      and: 'completion schedules one follow-up and the follow-up completes handling'
      instance.topologyChangeHandled(domainId)
      !instance.topologyChangeHandled(domainId)
   }

   def 'scheduling failure permits a later topology update to schedule discovery'()
   {
      given:
      TopologyChangedDoneHandler instance = new TopologyChangedDoneHandler()
      assert instance.topologyChanged(domainId)

      when:
      instance.schedulingFailed(domainId)

      then:
      instance.topologyChanged(domainId)
   }
}
