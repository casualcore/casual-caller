package se.laz.casual.connection.caller

import spock.lang.Specification

class ConnectionFactoriesByPriorityTest extends Specification
{
   def 'removing entries returns a new value and leaves the source unchanged'()
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
      ConnectionFactoriesByPriority instance = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(priority, entries)
      List<Long> prioritiesFromSnapshot = instance.getOrderedKeys()

      when:
      ConnectionFactoriesByPriority updated = instance
              .withoutEntry(entryOne)
              .withoutEntry(entryTwo)
      List<ConnectionFactoryEntry> entriesFromSnapshot = prioritiesFromSnapshot.collectMany {
         instance.getForPriority(it)
      }

      then:
      entriesFromSnapshot.toSet() == entries.toSet()
      instance.getOrderedKeys() == prioritiesFromSnapshot
      !entriesFromSnapshot.contains(null)
      !updated.hasPrioritizedEntries()
   }

   def 'do not store null values'()
   {
      setup:
      def priority = 1L
      ConnectionFactoriesByPriority instance = ConnectionFactoriesByPriority.emptyInstance()

      when:
      ConnectionFactoriesByPriority updated = instance.withEntries(priority, [null])

      then:
      !updated.hasPrioritizedEntries()
   }

   def 'equality is based on prioritized entries and resolved factories'()
   {
      given:
      ConnectionFactoryEntry firstEntry = Mock()
      ConnectionFactoryEntry secondEntry = Mock()

      ConnectionFactoriesByPriority first = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(0L, [firstEntry, secondEntry])
              .withResolvedFactories(['first', 'second'])

      ConnectionFactoriesByPriority equal = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(0L, [secondEntry, firstEntry])
              .withResolvedFactories(['second', 'first'])

      ConnectionFactoriesByPriority differentPriority = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(1L, [firstEntry, secondEntry])
              .withResolvedFactories(['first', 'second'])

      ConnectionFactoriesByPriority differentResolvedFactories = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(0L, [firstEntry, secondEntry])
              .withResolvedFactories(['first'])

      expect:
      first == equal
      first.hashCode() == equal.hashCode()
      first != differentPriority
      first != differentResolvedFactories
   }

   def 'merge combines values without modifying either input'()
   {
      given:
      ConnectionFactoryEntry existingEntry = Mock()
      ConnectionFactoryEntry incomingEntry = Mock()
      ConnectionFactoriesByPriority existing = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(1L, [existingEntry])
              .withResolvedFactory('existing')
      ConnectionFactoriesByPriority incoming = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(2L, [incomingEntry])
              .withResolvedFactory('incoming')

      when:
      ConnectionFactoriesByPriority merged = existing.mergeReplacing(incoming)

      then:
      merged.getForPriority(1L) == [existingEntry]
      merged.getForPriority(2L) == [incomingEntry]
      merged.checkedFactoriesForService == ['existing', 'incoming'] as Set

      and:
      existing.getForPriority(2L).isEmpty()
      existing.checkedFactoriesForService == ['existing'] as Set
      incoming.getForPriority(1L).isEmpty()
      incoming.checkedFactoriesForService == ['incoming'] as Set
   }

   def 'merge replaces an equal entry with the supplied instance'()
   {
      given:
      ConnectionFactoryProducer producer = Mock()
      ConnectionFactoryEntry staleEntry = ConnectionFactoryEntry.of(producer)
      ConnectionFactoryEntry replacementEntry = ConnectionFactoryEntry.of(producer)
      ConnectionFactoriesByPriority existing = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(1L, [staleEntry])
      ConnectionFactoriesByPriority incoming = ConnectionFactoriesByPriority.emptyInstance()
              .withEntries(1L, [replacementEntry])

      when:
      ConnectionFactoriesByPriority merged = existing.mergeReplacing(incoming)

      then:
      merged.getForPriority(1L).first().is(replacementEntry)
   }

}
