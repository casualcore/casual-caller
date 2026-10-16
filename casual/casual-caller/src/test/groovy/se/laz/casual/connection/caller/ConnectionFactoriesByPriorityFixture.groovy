/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

final class ConnectionFactoriesByPriorityFixture
{
   private ConnectionFactoriesByPriorityFixture()
   {
   }

   static ConnectionFactoriesByPriority createConnectionFactories(
           Map<Long, List<ConnectionFactoryEntry>> entries,
           Collection<String> resolved = [])
   {
      ConnectionFactoriesByPriority result = ConnectionFactoriesByPriority.emptyInstance()
      entries.each { priority, connectionFactories ->
         result = result.withEntries(priority, connectionFactories)
      }
      result.withResolvedFactories(resolved)
   }
}
