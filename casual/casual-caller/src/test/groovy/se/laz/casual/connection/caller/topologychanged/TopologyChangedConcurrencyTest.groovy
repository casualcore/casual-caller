/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged

import jakarta.enterprise.concurrent.ManagedScheduledExecutorService
import se.laz.casual.connection.caller.CacheRepopulator
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.DomainId
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TopologyChangedConcurrencyTest extends Specification
{
   def 'topology update arriving while discovery completion is handled is not lost'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ExecutorService executor = Executors.newFixedThreadPool(2)
      def schedulingDecisions = []

      when:
      100.times {
         TopologyChangedDoneHandler instance = new TopologyChangedDoneHandler()
         assert instance.topologyChanged(domainId)
         CyclicBarrier start = new CyclicBarrier(3)
         // Race a new update against completion. True means the update must schedule the next discovery.
         def updateFuture = executor.submit({
            start.await(5, TimeUnit.SECONDS)
            instance.topologyChanged(domainId)
         } as Callable<Boolean>)
         // Complete the current discovery concurrently. True means a pending update requires a follow-up discovery.
         def completionFuture = executor.submit({
            start.await(5, TimeUnit.SECONDS)
            instance.topologyChangeHandled(domainId)
         } as Callable<Boolean>)

         start.await(5, TimeUnit.SECONDS)
         boolean updateSchedulesDiscovery = updateFuture.get(5, TimeUnit.SECONDS)
         boolean completionSchedulesDiscovery = completionFuture.get(5, TimeUnit.SECONDS)
         schedulingDecisions.add([updateSchedulesDiscovery, completionSchedulesDiscovery].count(true))
      }

      then:
      schedulingDecisions.every { decisionCount -> decisionCount == 1 }

      cleanup:
      executor.shutdownNow()
      executor.awaitTermination(5, TimeUnit.SECONDS)
   }

   def 'topology update swarm is coalesced into one follow-up discovery'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      CasualConnection connection = Mock(CasualConnection) {
         getDomainId() >> domainId
      }
      CasualConnectionFactory connectionFactory = Mock(CasualConnectionFactory) {
         getConnection() >> connection
      }
      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry) {
         getConnectionFactory() >> connectionFactory
      }
      CacheRepopulator cacheRepopulator = Mock(CacheRepopulator)
      TopologyChangedHandlerImpl instance = new TopologyChangedHandlerImpl(cacheRepopulator)
      instance.setSupplier({ [connectionFactoryEntry] })
      Queue<Runnable> scheduledTasks = new ConcurrentLinkedQueue<>()
      AtomicInteger scheduleCount = new AtomicInteger()
      ManagedScheduledExecutorService executor = Mock(ManagedScheduledExecutorService) {
         schedule(_ as Runnable, _, TimeUnit.MILLISECONDS) >> {
            Runnable task, long delay, TimeUnit unit ->
               scheduleCount.incrementAndGet()
               scheduledTasks.add(task)
               Mock(ScheduledFuture)
         }
      }
      instance.setManagedScheduledExecutorService(executor)

      when: 'many updates arrive before the first discovery runs'
      instance.topologyChanged(domainId)
      100.times {
         instance.topologyChanged(domainId)
      }

      then: 'only the initial discovery is scheduled'
      scheduleCount.get() == 1
      scheduledTasks.size() == 1
      0 * cacheRepopulator.repopulate(_)

      when: 'the initial discovery completes'
      scheduledTasks.remove().run()

      then: 'all additional updates produce one follow-up discovery'
      scheduleCount.get() == 2
      scheduledTasks.size() == 1
      1 * cacheRepopulator.repopulate(connectionFactoryEntry)

      when: 'the follow-up discovery completes without another update'
      scheduledTasks.remove().run()

      then:
      scheduleCount.get() == 2
      scheduledTasks.isEmpty()
      1 * cacheRepopulator.repopulate(connectionFactoryEntry)
   }
}
