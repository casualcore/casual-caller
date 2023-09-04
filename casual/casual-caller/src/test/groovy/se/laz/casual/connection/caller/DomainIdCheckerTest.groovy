/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.DomainId
import se.laz.casual.network.connection.CasualConnectionException
import spock.lang.Specification

class DomainIdCheckerTest extends Specification
{
   def 'same domainId'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry){
         getConnectionFactory() >> {
            Mock(CasualConnectionFactory){
               getConnection() >> Mock(CasualConnection){
                  getDomainId() >> domainId
               }
            }
         }
      }
      when:
      boolean isSameDomainId = DomainIdChecker.isSameDomain(domainId, entry)
      then:
      isSameDomainId
   }

   def 'not the same domainId'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry){
         getConnectionFactory() >> {
            Mock(CasualConnectionFactory){
               getConnection() >> Mock(CasualConnection){
                  getDomainId() >> DomainId.of(UUID.randomUUID())
               }
            }
         }
      }
      when:
      boolean isSameDomainId = DomainIdChecker.isSameDomain(domainId, entry)
      then:
      !isSameDomainId
   }

   def 'casual jca throws'()
   {
      given:
      DomainId domainId = DomainId.of(UUID.randomUUID())
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry){
         getConnectionFactory() >> {
            Mock(CasualConnectionFactory){
               getConnection() >> Mock(CasualConnection){
                  throw new CasualConnectionException()
               }
            }
         }
      }
      when:
      boolean isSameDomainId = DomainIdChecker.isSameDomain(domainId, entry)
      then:
      !isSameDomainId
   }

}
