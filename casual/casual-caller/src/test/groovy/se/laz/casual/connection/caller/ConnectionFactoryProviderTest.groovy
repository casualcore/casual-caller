/*
 * Copyright (c) 2022 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.DomainId
import spock.lang.Specification

class ConnectionFactoryProviderTest extends Specification
{
   def 'normal operation, everything is found during deployment - only 1 call to initialize ( 0 calls during test of get)'()
   {
      given:
      CasualConnectionFactory normalFactory = Mock(CasualConnectionFactory) {
         isReverse() >> false
      }
      ConnectionFactoryEntry entry = ConnectionFactoryEntry.of(
              Mock(ConnectionFactoryProducer) {
                 getUniqueName() >> 'eis/casual'
                 getConnectionFactory() >> normalFactory
              })
      ConnectionFactoryFinder connectionFactoryFinder = Mock(ConnectionFactoryFinder)
      connectionFactoryFinder.findConnectionFactory(_) >>> [[entry]]
      // spying to verify the interaction
      ConnectionFactoryEntryStore instance = Spy(ConnectionFactoryEntryStore, constructorArgs: [connectionFactoryFinder, Mock(TopologyChangedHandler)]) {
         1 * initialize()
      }
      instance.setConnectionObserverHandler(Mock(ConnectionObserverHandler))
      // @PostConstruct
      instance.initialize()
      when:
      List<ConnectionFactoryEntry> result = instance.get()
      then:
      result.size() == 1
   }

   def 'abnormal wls operation, nothing is found during deployment - 2 calls to initialize ( 1 calls during test of get)'()
   {
      given:
      CasualConnectionFactory normalFactory = Mock(CasualConnectionFactory) {
         isReverse() >> false
      }
      ConnectionFactoryEntry entry = ConnectionFactoryEntry.of(
              Mock(ConnectionFactoryProducer) {
                 getUniqueName() >> 'eis/casual'
                 getConnectionFactory() >> normalFactory
              })
      ConnectionFactoryFinder connectionFactoryFinder = Mock(ConnectionFactoryFinder)
      connectionFactoryFinder.findConnectionFactory(_) >>> [[], [entry]]
      // spying to verify the interactions
      ConnectionFactoryEntryStore instance = Spy(ConnectionFactoryEntryStore, constructorArgs: [connectionFactoryFinder, Mock(TopologyChangedHandler)]) {
         2 * initialize()
      }
      instance.setConnectionObserverHandler(Mock(ConnectionObserverHandler))
      // @PostConstruct
      instance.initialize()
      when:
      List<ConnectionFactoryEntry> result = instance.get()
      then:
      result.size() == 1
   }

   def 'reverse pool backed entries, the base entry is never served'()
   {
      given:
      DomainId domainA = DomainId.of(UUID.randomUUID())
      DomainId domainB = DomainId.of(UUID.randomUUID())
      println "domainA: ${domainA.getId()}"
      println "domainB: ${domainB.getId()}"
      def reverseBaseJndiName = 'eis/casualReverse'
      CasualConnectionFactory reverseFactory = Mock(CasualConnectionFactory){
         isReverse() >> true
         getDomainIds() >>> [[domainA], [domainA, domainB], []]
      }
      ConnectionFactoryEntry reverseBase = ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
         getUniqueName() >> reverseBaseJndiName
         getConnectionFactory() >> reverseFactory
      })
      ConnectionFactoryEntry normalEntry = ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
         getUniqueName() >> 'eis/casual'
         getConnectionFactory() >> Mock(CasualConnectionFactory) {
            isReverse() >> false
         }
      })
      ConnectionFactoryFinder connectionFactoryFinder = Mock(ConnectionFactoryFinder) {
         findConnectionFactory(_) >> [normalEntry, reverseBase]
      }
      ConnectionFactoryEntryStore instance = new ConnectionFactoryEntryStore(connectionFactoryFinder, Mock(TopologyChangedHandler))
      instance.setConnectionObserverHandler(Mock(ConnectionObserverHandler))
      when: 'domain A is available'
      instance.initialize()
      then:
      instance.get().size() == 2
      !instance.get().contains(reverseBase)
      instance.get().contains(normalEntry)
      instance.get().forEach ({ entry -> println("${entry.getJndiName()}")})
      when: 'domainB available as well'
      ReverseRefreshResult refreshedReverse = instance.refreshReverseEntries()
      then:
      instance.get().size() == 3
      !instance.get().contains(reverseBase)
      instance.get().contains(normalEntry)
      refreshedReverse.added().size() == 1
      refreshedReverse.added().get(0).getJndiName() == "${reverseBaseJndiName}[${domainB.getId()}]"
      instance.get().forEach ({ entry -> println("${entry.getJndiName()}")})

      when: 'all instances are gone - due to exception when calling getConnection on the reverse base pool'
      refreshedReverse = instance.refreshReverseEntries()
      then: 'reverse domains gone, the normal entry remains'
      refreshedReverse.added().isEmpty()
      refreshedReverse.purged().size() == 2
      refreshedReverse.purged().every { entry -> entry.isInvalid() }
      instance.get() == [normalEntry]
   }

   def 'reverse base is not served when no domains are connected at startup'()
   {
      given:
      CasualConnectionFactory reverseFactory = Mock(CasualConnectionFactory) {
         getConnection() >> {
            throw new jakarta.resource.ResourceException(
                    'No reverse outbound connections available')
         }
         isReverse() >> true
         getDomainIds() >> Collections.emptyList()
      }
      ConnectionFactoryEntry reverseBase = ConnectionFactoryEntry.of(
              Mock(ConnectionFactoryProducer) {
                 getUniqueName() >> 'eis/casualReverse'
                 getConnectionFactory() >> reverseFactory
              })
      CasualConnectionFactory normalFactory = Mock(CasualConnectionFactory) {
         isReverse() >> false
      }
      ConnectionFactoryEntry normalEntry = ConnectionFactoryEntry.of(
              Mock(ConnectionFactoryProducer) {
                 getUniqueName() >> 'eis/casual'
                 getConnectionFactory() >> normalFactory
              })
      ConnectionFactoryFinder finder = Mock(ConnectionFactoryFinder) {
         findConnectionFactory(_) >> [normalEntry, reverseBase]
      }
      ConnectionFactoryEntryStore store = new ConnectionFactoryEntryStore(
              finder, Mock(TopologyChangedHandler))
      store.setConnectionObserverHandler(Mock(ConnectionObserverHandler))

      when:
      store.initialize()
      List<ConnectionFactoryEntry> entries = store.get()

      then:
      entries == [normalEntry]
   }


}
