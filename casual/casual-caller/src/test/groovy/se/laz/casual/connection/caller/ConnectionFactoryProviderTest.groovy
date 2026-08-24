/*
 * Copyright (c) 2022 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.DomainId
import spock.lang.Specification

class ConnectionFactoryProviderTest extends Specification
{
   def 'normal operation, everything is found during deployment - only 1 call to initialize ( 0 calls during test of get)'()
   {
      given:
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry)
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
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry)
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
      def connectionWith = { List<DomainId> domainIds ->
         Mock(CasualConnection) {
            isReversePool() >> true
            getPoolDomainIds() >> domainIds
         }
      }
      def reverseBaseJndiName = 'eis/casualReverse'
      CasualConnectionFactory reverseFactory = Mock(CasualConnectionFactory)
      reverseFactory.getConnection() >>> [connectionWith([domainA]), connectionWith([domainA, domainB])] >> { throw new CasualResourceException('no instances connected', new RuntimeException()) }
      ConnectionFactoryEntry reverseBase = ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
         getUniqueName() >> reverseBaseJndiName
         getConnectionFactory() >> reverseFactory
      })
      ConnectionFactoryEntry normalEntry = ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
         getUniqueName() >> 'eis/casual'
         getConnectionFactory() >> Mock(CasualConnectionFactory) {
            getConnection() >> Mock(CasualConnection) {
               isReversePool() >> false
            }
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
      then: 'their entries are invalidated and purged, the normal entry remains'
      refreshedReverse.added().isEmpty()
      refreshedReverse.purged().size() == 2
      refreshedReverse.purged().every { entry -> entry.isInvalid() }
      instance.get() == [normalEntry]
   }

}
