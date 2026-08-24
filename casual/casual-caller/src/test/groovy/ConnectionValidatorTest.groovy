/*
 * Copyright (c) 2023 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
import se.laz.casual.connection.caller.Cache
import se.laz.casual.connection.caller.CacheRepopulator
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.connection.caller.ConnectionFactoryEntryStore
import se.laz.casual.connection.caller.ConnectionValidator
import se.laz.casual.connection.caller.ReverseRefreshResult
import spock.lang.Specification

class ConnectionValidatorTest extends Specification
{
   def 'connection reestablished ok'()
   {
      given:
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry){
         2 * isValid() >>> [false, true]
         1 * validate()
      }
      CacheRepopulator cacheRepopulator = Mock(CacheRepopulator){
         1 * repopulate(entry)
      }
      ConnectionFactoryEntryStore connectionFactoryEntryStore = Mock(ConnectionFactoryEntryStore){
         1 * addConnectionObserver(entry)
         get() >> [entry]
         refreshReverseEntries() >> new ReverseRefreshResult([], [])
      }
      ConnectionValidator instance = new ConnectionValidator(cacheRepopulator, connectionFactoryEntryStore, Mock(Cache))
      when:
      instance.validateAllConnections()
      then:
      noExceptionThrown()
   }

   def 'validation fails'()
   {
      given:
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry){
         1 * isValid() >>> [false, true]
         1 * validate() >> {
            throw new RuntimeException('Bazinga!')
         }
         1 * invalidate()
      }
      CacheRepopulator cacheRepopulator = Mock(CacheRepopulator){
         0 * repopulate(entry)
      }
      ConnectionFactoryEntryStore connectionFactoryEntryStore = Mock(ConnectionFactoryEntryStore){
         0 * addConnectionObserver(entry)
         get() >> [entry]
         refreshReverseEntries() >> new ReverseRefreshResult([], [])
      }
      ConnectionValidator instance = new ConnectionValidator(cacheRepopulator, connectionFactoryEntryStore, Mock(Cache))
      when:
      instance.validateAllConnections()
      then:
      noExceptionThrown()
   }

   def 'reverse pool backed entries, added instances are discovered and observed - purged ones leave the caches'()
   {
      given:
      ConnectionFactoryEntry addedEntry = Mock(ConnectionFactoryEntry){
         isValid() >> true
         1 * validate()
      }
      ConnectionFactoryEntry purgedEntry = Mock(ConnectionFactoryEntry)
      Cache cache = Mock(Cache){
         1 * purge(purgedEntry)
      }
      CacheRepopulator cacheRepopulator = Mock(CacheRepopulator){
         1 * repopulate(addedEntry)
      }
      ConnectionFactoryEntryStore connectionFactoryEntryStore = Mock(ConnectionFactoryEntryStore){
         1 * addConnectionObserver(addedEntry)
         get() >> [addedEntry]
         refreshReverseEntries() >> new ReverseRefreshResult([addedEntry], [purgedEntry])
      }
      ConnectionValidator instance = new ConnectionValidator(cacheRepopulator, connectionFactoryEntryStore, cache)
      when:
      instance.validateAllConnections()
      then:
      noExceptionThrown()
   }
}
