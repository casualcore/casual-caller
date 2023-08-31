import se.laz.casual.connection.caller.CacheRepopulator
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.connection.caller.ConnectionFactoryEntryStore
import se.laz.casual.connection.caller.ConnectionValidator
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
      }
      ConnectionValidator instance = new ConnectionValidator(cacheRepopulator, connectionFactoryEntryStore)
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
      }
      ConnectionValidator instance = new ConnectionValidator(cacheRepopulator, connectionFactoryEntryStore)
      when:
      instance.validateAllConnections()
      then:
      noExceptionThrown()
   }
}
