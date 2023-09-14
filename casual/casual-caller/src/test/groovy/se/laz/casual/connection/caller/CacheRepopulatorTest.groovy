/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import jakarta.resource.ResourceException;
import se.laz.casual.api.discovery.DiscoveryReturn
import se.laz.casual.api.queue.QueueDetails
import se.laz.casual.api.service.ServiceDetails
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.network.messages.domain.TransactionType
import spock.lang.Specification

class CacheRepopulatorTest extends Specification
{
   def 'invalid connection when running discovery'()
   {
      given:
      ConnectionFactoryProducer producer = Mock(ConnectionFactoryProducer){
         getConnectionFactory() >> {
            Mock(CasualConnectionFactory){
               getConnection() >> {
                  throw new ResourceException();
               }
            }
         }
      }
      ConnectionFactoryEntry entry = ConnectionFactoryEntry.of(producer)
      Cache cache = Mock(Cache){
         1 * getAll() >> {
            Map<CacheType, List<String>> entries = new EnumMap<>(CacheType.class)
            entries.put(CacheType.SERVICE, [])
            entries.put(CacheType.QUEUE, [])
            return entries
         }
         1 * purge(entry)
         0 * repopulate(_, entry)
      }
      CacheRepopulator instance = new CacheRepopulator(cache, new TransactionLess())
      when:
      instance.repopulate(entry)
      then:
      noExceptionThrown()
   }

   def 'valid connection when running discovery'()
   {
      given:
      def queueName = 'The fastest queue in the world'
      def serviceName = 'A shiny service'
      DiscoveryReturn discoveryReturn = DiscoveryReturn.createBuilder()
              .addQueueDetails(QueueDetails.of(queueName, 10L))
              .addServiceDetails(ServiceDetails.createBuilder()
                      .withName(serviceName)
                      .withTimeout(0)
                      .withHops(2)
                      .withCategory('Diamonds')
                      .withTransactionType(TransactionType.JOIN)
                      .build())
              .build()
      CasualConnection connection = Mock(CasualConnection) {
         1 * discover(_, _, _) >> discoveryReturn
      }
      ConnectionFactoryProducer producer = Mock(ConnectionFactoryProducer){
         getConnectionFactory() >> {
            Mock(CasualConnectionFactory){
               getConnection() >> connection
            }
         }
      }
      ConnectionFactoryEntry entry = ConnectionFactoryEntry.of(producer)
      Cache cache = Mock(Cache){
         1 * getAll() >> {
            Map<CacheType, List<String>> entries = new EnumMap<>(CacheType.class)
            entries.put(CacheType.SERVICE, [serviceName])
            entries.put(CacheType.QUEUE, [queueName])
            return entries
         }
         1 * purge(entry)
         1 * repopulate(discoveryReturn, entry)
      }
      CacheRepopulator instance = new CacheRepopulator(cache, new TransactionLess())
      when:
      instance.repopulate(entry)
      then:
      entry.isValid()
   }
}
