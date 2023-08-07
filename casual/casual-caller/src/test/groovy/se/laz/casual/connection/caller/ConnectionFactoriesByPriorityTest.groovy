package se.laz.casual.connection.caller

import spock.lang.Specification

class ConnectionFactoriesByPriorityTest extends Specification
{
   def 'nullptr possible'()
   {
      given:
      def priority = 1L
      def jndiName = 'jndiFoo'
      def entryOne = Mock(ConnectionFactoryEntry){
         getJndiName() >>{
            jndiName
         }
      }
      def entryTwo = Mock(ConnectionFactoryEntry){
         getJndiName() >>{
            jndiName
         }
      }
      def entries = [entryOne, entryTwo]
      ConnectionFactoriesByPriority instance = ConnectionFactoriesByPriority.of([1L : entries])
      when:
      def storedEntries = instance.getForPriority(priority)
      then:
      storedEntries == entries
      when:
      // note, imagine this being run in two different threads - then this may happen
      // thread 1
      def priorities = instance.getOrderedKeys()
      // thread 2
      instance.remove(entryOne)
      instance.remove(entryTwo)
      // back in  thread 1
      priorities.each {storedEntries = instance.getForPriority(it)}
      then:
      thrown(NullPointerException)
   }

   def 'do not store null values'()
   {
      setup:
      def priority = 1L
      ConnectionFactoriesByPriority instance = new ConnectionFactoriesByPriority()
      when:
      instance.store(priority, [null])
      then:
      instance.isEmpty()
   }

}
