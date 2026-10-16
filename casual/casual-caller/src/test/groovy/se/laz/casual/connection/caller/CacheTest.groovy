/*
 * Copyright (c) 2017 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

import se.laz.casual.api.discovery.DiscoveryReturn
import se.laz.casual.api.queue.QueueDetails
import se.laz.casual.api.queue.QueueInfo
import se.laz.casual.api.service.ServiceDetails
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.network.ProtocolVersion
import se.laz.casual.network.messages.domain.TransactionType
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

import static se.laz.casual.connection.caller.ConnectionFactoriesByPriorityFixture.createConnectionFactories

class CacheTest extends Specification
{
   @Shared
   Cache instance
   @Shared
   def connectionFactoryOne = Mock(CasualConnectionFactory)
   @Shared
   def jndiNameOne = 'eis/CasualConnectionFactory'
   @Shared
   def connectionFactoryTwo = Mock(CasualConnectionFactory)
   @Shared
   def jndiNameTwo = 'eis/AnotherCasualConnectionFactory'
   @Shared
   ConnectionFactoryProducer producerOne = {
      def mock = Mock(ConnectionFactoryProducer)
      mock.getConnectionFactory() >> {
         connectionFactoryOne
      }
      mock.getUniqueName() >> {
         jndiNameOne
      }
      return mock
   }()
   @Shared
   ConnectionFactoryProducer producerTwo = {
      def mock = Mock(ConnectionFactoryProducer)
      mock.getConnectionFactory() >> {
         connectionFactoryTwo
      }
      mock.getUniqueName() >> {
         jndiNameTwo
      }
      return mock
   }()
   @Shared
   def cacheEntryOne = ConnectionFactoryEntry.of(producerOne)
   @Shared
   def cacheEntryTwo = ConnectionFactoryEntry.of(producerTwo)
   @Shared
   def serviceName = 'casual.test.echo'
   @Shared
   def serviceNameOnlyFromConnectionFactoryOne = 'flash.gordon'
   @Shared
   def queueNameOnlyFromConnectionFactoryOne = 'nifty.queue'
   @Shared
   def qInfo = QueueInfo.of('space1.agrajag')
   @Shared
   def qInfoList = [QueueInfo.of('hairy.otter'), QueueInfo.of('drunken.monkey')]
   @Shared
   def serviceNames = ['casual.rollback', 'casually.casual']
   @Shared
   def priority = 17L
   @Shared
   def lowerPriority = priority - 1
   @Shared
   def allServiceNames = ([serviceName, serviceNameOnlyFromConnectionFactoryOne] + serviceNames).stream()
                                                                                                 .distinct()
                                                                                                 .sorted()
                                                                                                 .collect(Collectors.toList())
   @Shared
   def allQueueNames = qInfoList.stream()
                                .map({v -> v.getQueueName()})
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList())

   def setup()
   {
      instance = new Cache()
      qInfoList.forEach({ q -> instance.store(q, [cacheEntryOne, cacheEntryTwo]) })
      serviceNames.forEach({ s -> instance.store(s, createConnectionFactories([(priority): [cacheEntryTwo]])) })
      instance.store(serviceName, createConnectionFactories([(priority): [cacheEntryOne, cacheEntryTwo]]))
      instance.store(serviceNameOnlyFromConnectionFactoryOne, createConnectionFactories([(priority): [cacheEntryOne]]))
   }

   def 'store null cache entry'()
   {
      when:
      instance.store(serviceName, null)
      then:
      thrown(NullPointerException)
   }

   def 'set, get and remove service'()
   {
      given:
      def anotherServiceName = 'anotherServiceName'
      when:
      instance.store(anotherServiceName, createConnectionFactories([(priority): [cacheEntryOne, cacheEntryTwo]]))
      def entries = instance.get(anotherServiceName)
      then:
      entries.getForPriority(priority).size() == 2
      when:
      instance.removeService(anotherServiceName)
      entries = instance.get(anotherServiceName)
      then:
      !entries.hasPrioritizedEntries()
   }

   def 'store service providers at different priorities'()
   {
      given:
      def anotherServiceName = 'service-with-different-priorities'

      when:
      instance.store(anotherServiceName, createConnectionFactories([(lowerPriority): [cacheEntryOne]]))
      instance.store(anotherServiceName, createConnectionFactories([(priority): [cacheEntryTwo]]))
      ConnectionFactoriesByPriority entries = instance.get(anotherServiceName)

      then:
      entries.orderedKeys == [lowerPriority, priority]
      entries.getForPriority(lowerPriority) == [cacheEntryOne]
      entries.getForPriority(priority) == [cacheEntryTwo]
      entries.randomizeWithPriority() == [cacheEntryOne, cacheEntryTwo]
   }

   def 'service updates do not mutate a previously published snapshot'()
   {
      given:
      String snapshotService = 'snapshot.service'
      instance.store(snapshotService, createConnectionFactories([(priority): [cacheEntryOne]]))
      ConnectionFactoriesByPriority publishedSnapshot = instance.get(snapshotService)

      when:
      instance.store(snapshotService, createConnectionFactories([(priority): [cacheEntryTwo]]))
      instance.purge(cacheEntryOne)

      then:
      publishedSnapshot.randomizeWithPriority() == [cacheEntryOne]
      instance.get(snapshotService).randomizeWithPriority() == [cacheEntryTwo]
   }

   def 'new equal factory instance replaces stale cached instance'()
   {
      given:
      String reconnectedService = 'reconnected.service'
      ConnectionFactoryEntry staleEntry = createEqualEntry('same-domain')
      ConnectionFactoryEntry reconnectedEntry = createEqualEntry('same-domain')
      instance.store(reconnectedService, createConnectionFactories([(priority): [staleEntry]]))
      staleEntry.invalidate()

      when:
      instance.store(reconnectedService, createConnectionFactories([(priority): [reconnectedEntry]]))

      then:
      List<ConnectionFactoryEntry> cachedEntries = instance.get(reconnectedService).randomizeWithPriority()
      cachedEntries.size() == 1
      cachedEntries[0].is(reconnectedEntry)
      cachedEntries[0].isValid()
   }

   def 'repopulation replaces stale equal factory instance'()
   {
      given:
      String repopulatedService = 'repopulated.service'
      DiscoveryReturn discoveryReturn = Mock(DiscoveryReturn) {
         getServiceDetails() >> [toServiceDetails(repopulatedService)]
         getQueueDetails() >> []
      }
      ConnectionFactoryEntry staleEntry = createEqualEntry('same-domain')
      ConnectionFactoryEntry reconnectedEntry = createEqualEntry('same-domain')
      instance.repopulate(discoveryReturn, staleEntry)
      staleEntry.invalidate()

      when:
      instance.repopulate(discoveryReturn, reconnectedEntry)

      then:
      List<ConnectionFactoryEntry> cachedEntries = instance.get(repopulatedService).randomizeWithPriority()
      cachedEntries.size() == 1
      cachedEntries[0].is(reconnectedEntry)
      cachedEntries[0].isValid()
   }

   def 'get missing service entry'()
   {
      when:
      def entries = instance.get('does-not-exist')
      then:
      !entries.hasPrioritizedEntries()
   }

   def 'resolved factory metadata updates known services but does not create unknown services'()
   {
      given:
      String resolvedFactory = 'resolved-factory'
      ConnectionFactoriesByPriority metadata = ConnectionFactoriesByPriority.emptyInstance()
              .withResolvedFactory(resolvedFactory)

      when:
      instance.store(serviceName, metadata)
      instance.store('unknown-service', metadata)

      then:
      instance.get(serviceName).isResolved(resolvedFactory)
      !instance.services.contains('unknown-service')
   }

   def 'storing a value without providers or metadata does nothing'()
   {
      given:
      ConnectionFactoriesByPriority cached = instance.get(serviceName)

      when:
      instance.store(serviceName, ConnectionFactoriesByPriority.emptyInstance())
      instance.store('unknown-service', ConnectionFactoriesByPriority.emptyInstance())

      then:
      instance.get(serviceName).is(cached)
      !instance.services.contains('unknown-service')
   }

   def 'store queue null cache entry'()
   {
      when:
      instance.store(qInfo, null)
      then:
      thrown(NullPointerException)
   }

   def 'set and get queue'()
   {
      when:
      instance.store(qInfo, [cacheEntryOne])
      def entries = instance.getSingle(qInfo)
      then:
      entries.isPresent()
   }

   def 'set and get queue but the connection factory is invalid'()
   {
      given:
      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry){
         isValid() >> false
      }
      when:
      instance.store(qInfo, [connectionFactoryEntry])
      def entry = instance.getSingle(qInfo)
      then:
      !entry.isPresent()
   }

   def 'queue sticky is discarded when its connection factory becomes invalid'()
   {
      given:
      boolean valid = true
      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry) {
         isValid() >> { valid }
      }
      instance.store(qInfo, [connectionFactoryEntry])

      expect:
      instance.getSingle(qInfo).orElseThrow().is(connectionFactoryEntry)

      when:
      valid = false

      then:
      instance.getSingle(qInfo).isEmpty()
   }

   def 'queue cache stores an immutable snapshot of the supplied entries'()
   {
      given:
      List<ConnectionFactoryEntry> entries = [cacheEntryOne]

      when:
      instance.store(qInfo, entries)
      entries.clear()

      then:
      instance.get(qInfo) == [cacheEntryOne]
   }

   def 'queue cache updates do not mutate a previously published snapshot'()
   {
      given:
      instance.store(qInfo, [cacheEntryOne])
      List<ConnectionFactoryEntry> publishedSnapshot = instance.get(qInfo)

      when:
      instance.purge(cacheEntryOne)

      then:
      publishedSnapshot == [cacheEntryOne]
      instance.get(qInfo).isEmpty()
   }

   def 'get missing queue entry'()
   {
      given:
      def qinfoNotStored = QueueInfo.of("abc.Ford Prefect")
      when:
      def entries = instance.getSingle(qinfoNotStored)
      then:
      !entries.isPresent()
   }

   def 'get service with only one provider'()
   {
      when:
      def connectionFactoryByPriority = instance.get(serviceNameOnlyFromConnectionFactoryOne)
      then:
      connectionFactoryByPriority.getForPriority(priority).size() == 1
      connectionFactoryByPriority.getForPriority(priority).get(0) == cacheEntryOne
   }

   def 'getAll queues and services'()
   {
      when:
      def allEntries = instance.getAll()
      then:
      allEntries.get(CacheType.SERVICE).stream().distinct().sorted().collect(Collectors.toList()) == allServiceNames
      allEntries.get(CacheType.QUEUE).stream().distinct().sorted().collect(Collectors.toList()) == allQueueNames
   }


   def 'cache purge and repopulation via DiscoveryReturn - services'()
   {
      given:
      def discoveryReturn = Mock(DiscoveryReturn){
         getQueueDetails() >> {
            []
         }
         getServiceDetails() >> {
            [toServiceDetails(serviceNameOnlyFromConnectionFactoryOne), toServiceDetails(serviceName), serviceNames.stream()
                    .map({toServiceDetails(it)})
                    .collect(Collectors.toList())].flatten()
         }
      }
      when:
      instance.purge(cacheEntryOne)
      def afterPurge = instance.get(serviceNameOnlyFromConnectionFactoryOne)
      then:
      !afterPurge.hasPrioritizedEntries()
      when:
      instance.repopulate(discoveryReturn, cacheEntryOne)
      def afterRepopulate = instance.get(serviceNameOnlyFromConnectionFactoryOne)
      then:
      afterRepopulate.getForPriority(priority).size() == 1
      afterRepopulate.getForPriority(priority).get(0) == cacheEntryOne
      when: //get entry services by 2 connections
      def moreThanOneConnection = instance.get(serviceName)
      then:
      moreThanOneConnection.getForPriority(priority).size() == 2
   }

   def 'cache purge and repopulation via DiscoveryReturn - queues'()
   {
      given:
      def discoveryReturn = Mock(DiscoveryReturn){
         getQueueDetails() >> {
            [qInfoList.stream()
                     .map({item -> toQueueDetails(item.getQueueName())})
                     .collect(Collectors.toList()),
             toQueueDetails(queueNameOnlyFromConnectionFactoryOne)].flatten()
         }
         getServiceDetails() >> {
            []
         }
      }
      println "queue details: ${discoveryReturn.getQueueDetails()}"
      println "first qname: ${qInfoList.get(0)}"
      def queueInfoOnlyConnectionOne = QueueInfo.of(queueNameOnlyFromConnectionFactoryOne)
      instance.store(queueInfoOnlyConnectionOne, [cacheEntryOne])
      when:
      def beforePurge = instance.get(queueInfoOnlyConnectionOne)
      then:
      beforePurge.size() == 1
      when: // get queue served from 2 places
      beforePurge = instance.get(qInfoList.get(0))
      then:
      beforePurge.size() == 2
      when:
      instance.purge(cacheEntryOne)
      def afterPurge = instance.get(queueInfoOnlyConnectionOne)
      then:
      afterPurge.isEmpty()
      when:
      afterPurge = instance.get(qInfoList.get(0))
      then:
      afterPurge.size() == 1
      when:
      instance.repopulate(discoveryReturn, cacheEntryOne)
      def afterRepopulate = instance.get(queueInfoOnlyConnectionOne)
      then:
      afterRepopulate.size() == 1
   }

   def 'using Arrays.asList does not throw UnsupportedOperationException when remove is used'()
   {
      given:
      def queueInfoOnlyConnectionOne = QueueInfo.of(queueNameOnlyFromConnectionFactoryOne)
      instance.store(queueInfoOnlyConnectionOne, Arrays.asList(cacheEntryOne))
      when:
      instance.purge(cacheEntryOne)
      then:
      noExceptionThrown()
   }

   QueueDetails toQueueDetails(String name)
   {
      QueueDetails.createBuilder()
              .withProtocolVersion(ProtocolVersion.VERSION_1_2)
              .withName(name)
              .withRetries(0)
              .build()
   }

   ServiceDetails toServiceDetails(name)
   {
      return ServiceDetails.createBuilder().withName(name)
              .withHops(priority)
              .withCategory('foo')
              .withTransactionType(TransactionType.AUTOMATIC)
              .build()
   }

   private static ConnectionFactoryEntry createEqualEntry(String uniqueName)
   {
      ConnectionFactoryEntry.of(ConnectionFactoryProducerImpl.of(uniqueName))
   }
}
