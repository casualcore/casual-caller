/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.info

import se.laz.casual.connection.caller.Cache
import se.laz.casual.connection.caller.ConnectionFactoriesByPriority
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.connection.caller.ConnectionFactoryLookup
import spock.lang.Shared
import spock.lang.Specification

class CasualInfoImplTest extends Specification
{
   private String serviceName = "test"

   @Shared
   Cache cache
   @Shared
   ConnectionFactoryLookup connectionFactoryLookup
   @Shared
   CasualInfo casualInfo

   def setup()
   {
      cache =  Mock(Cache)
      connectionFactoryLookup = Mock(ConnectionFactoryLookup)
      casualInfo = new CasualInfoImpl(cache, connectionFactoryLookup)
   }

   def 'test getServices'()
   {
      setup:
      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry) {
         1 * getJndiName() >> { return "jndi" }
         1 * isValid() >> { return true }
      }

      ConnectionFactoriesByPriority connectionFactoriesByPriority = Mock(ConnectionFactoriesByPriority) {
         1 * getOrderedKeys() >> { List.of(Long.valueOf(0))}
         1 * getForPriority(0) >> {
            return List.of(connectionFactoryEntry)
         }
      }
      cache.getServices() >> { List.of(serviceName)}
      cache.get(serviceName) >> connectionFactoriesByPriority

      when:
      def services = casualInfo.getServices()

      then:
      services.size() == 1
      services.get(0).name == serviceName
      services.get(0).jndiName == "jndi"
      services.get(0).hops == 0
      services.get(0).valid
   }
}
