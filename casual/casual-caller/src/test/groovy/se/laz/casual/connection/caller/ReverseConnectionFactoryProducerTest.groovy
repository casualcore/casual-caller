/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.CasualRequestInfo
import se.laz.casual.jca.DomainId
import spock.lang.Specification


class ReverseConnectionFactoryProducerTest extends Specification
{
   def 'unique name is the base name with the domain id appended'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ConnectionFactoryEntry base = ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
         getUniqueName() >> 'eis/casualReverse'
      })
      when:
      ReverseConnectionFactoryProducer producer = ReverseConnectionFactoryProducer.of(base, domainId)
      then:
      producer.getUniqueName() == "eis/casualReverse[${domainId.getId()}]"
      producer.getDomainId() == domainId
   }

   def 'connections are always requested pinned to the instance domain id'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      DomainId differingDomainId = DomainId.of(UUID.randomUUID())
      CasualConnection connection = Mock(CasualConnection)
      CasualConnectionFactory baseFactory = Mock(CasualConnectionFactory) {
         3 * getConnection(CasualRequestInfo.of(domainId)) >> connection
      }
      ConnectionFactoryEntry base = ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
         getUniqueName() >> 'eis/casualReverse'
         getConnectionFactory() >> baseFactory
      })
      ReverseConnectionFactoryProducer producer = ReverseConnectionFactoryProducer.of(base, domainId)
      CasualConnectionFactory pinned = producer.getConnectionFactory()

      expect: 'connection is requested with pinned domain id'
      pinned.getConnection() == connection

      and: 'also when asked without any request info'
      pinned.getConnection(null) == connection

      and: 'also when asked with matching request info'
      pinned.getConnection(CasualRequestInfo.of(domainId)) == connection

      when: 'asked with differing request info'
      pinned.getConnection(CasualRequestInfo.of(differingDomainId))

      then: 'IllegalArgumentException is thrown'
      thrown(IllegalArgumentException)
   }

   def 'producers for the same base and domain id are equal, differing domain ids are not'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ConnectionFactoryEntry base = ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
         getUniqueName() >> 'eis/casualReverse'
      })
      expect: 'equals explicitly since groovy == uses compareTo for Comparable and all producers share the default priority'
      ReverseConnectionFactoryProducer.of(base, domainId).equals(ReverseConnectionFactoryProducer.of(base, domainId))
      !ReverseConnectionFactoryProducer.of(base, domainId).equals(ReverseConnectionFactoryProducer.of(base, DomainId.of(UUID.randomUUID())))
   }
}
