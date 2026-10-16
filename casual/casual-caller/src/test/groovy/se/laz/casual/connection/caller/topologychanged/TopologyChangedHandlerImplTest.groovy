/*
 * Copyright (c) 2023 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.topologychanged

import jakarta.enterprise.concurrent.ManagedScheduledExecutorService
import se.laz.casual.connection.caller.CacheRepopulator
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.connection.caller.config.ConfigurationService
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.DomainId
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class TopologyChangedHandlerImplTest extends Specification
{
   def 'task scheduling fails'() {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry) {
         1 * getConnectionFactory() >> Mock(CasualConnectionFactory) {
            1 * getConnection() >> Mock(CasualConnection) {
               1 * getDomainId() >> domainId
            }
         }
         1 * setNeedsDomainDiscovery(true)
      }
      CacheRepopulator cacheRepopulator = Mock(CacheRepopulator){
         0 * repopulate(connectionFactoryEntry)
      }
      TopologyChangedHandlerImpl instance = new TopologyChangedHandlerImpl(cacheRepopulator)
      ManagedScheduledExecutorService managedScheduledExecutorService = Mock(ManagedScheduledExecutorService) {
         schedule(_ as Runnable, ConfigurationService.getInstance().getConfiguration().getTopologyChangeDelayMillis(), TimeUnit.MILLISECONDS) >> {
            throw new RejectedExecutionException()
         }
      }
      instance.setManagedScheduledExecutorService(managedScheduledExecutorService)
      Supplier<List<ConnectionFactoryEntry>> supplier = { [connectionFactoryEntry] }
      instance.setSupplier(supplier)
      when:
      instance.topologyChanged(domainId)
      then:
      noExceptionThrown()
   }

   def 'task scheduling ok'() {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry) {
         1 * getConnectionFactory() >> Mock(CasualConnectionFactory) {
            1 * getConnection() >> Mock(CasualConnection) {
               1 * getDomainId() >> domainId
            }
         }
         0 * setNeedsDomainDiscovery(_)
      }
      Supplier<List<ConnectionFactoryEntry>> supplier = {[connectionFactoryEntry]}
      CacheRepopulator cacheRepopulator = Mock(CacheRepopulator){
         1 * repopulate(connectionFactoryEntry)
      }
      TopologyChangedHandlerImpl instance = new TopologyChangedHandlerImpl(cacheRepopulator)
      instance.setSupplier(supplier)
      CompletableFuture<Void> outerFuture = new CompletableFuture<>()
      ManagedScheduledExecutorService managedScheduledExecutorService = Mock(ManagedScheduledExecutorService) {

         1 * schedule(_ as Runnable, ConfigurationService.getInstance().getConfiguration().getTopologyChangeDelayMillis(), TimeUnit.MILLISECONDS) >> {
            Runnable task, long delay, TimeUnit timeunit ->
               CompletableFuture<Void> innerFuture = CompletableFuture.runAsync (task, Executors.newSingleThreadExecutor())
               innerFuture.whenComplete {value, error ->
                  assert null == error
                  outerFuture.complete(value)
               }
               // note: return value is never used but we need to return the same type
               return Mock(ScheduledFuture)
         }
      }
      instance.setManagedScheduledExecutorService(managedScheduledExecutorService)
      when:
      instance.topologyChanged(domainId)
      outerFuture.join()
      then:
      noExceptionThrown()
   }

   def 'failed discovery does not suppress a later topology update'()
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
      AtomicInteger discoveryCount = new AtomicInteger()
      CacheRepopulator cacheRepopulator = Mock(CacheRepopulator) {
         repopulate(connectionFactoryEntry) >> {
            if (discoveryCount.incrementAndGet() == 1)
            {
               throw new IllegalStateException('Discovery failed')
            }
         }
      }
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

      when:
      instance.topologyChanged(domainId)
      scheduledTasks.remove().run()
      instance.topologyChanged(domainId)
      scheduledTasks.remove().run()

      then:
      scheduleCount.get() == 2
      discoveryCount.get() == 2
      scheduledTasks.isEmpty()
   }
}
