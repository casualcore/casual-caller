/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.connection.caller.config.ConfigurationService
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.DomainId
import spock.lang.Specification

import javax.enterprise.concurrent.ManagedScheduledExecutorService
import java.util.concurrent.*
import java.util.function.Supplier

class TopologyChangedHandlerTest extends Specification
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
      TopologyChangedHandler instance = new TopologyChangedHandler(cacheRepopulator)
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
      TopologyChangedHandler instance = new TopologyChangedHandler(cacheRepopulator)
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
}
