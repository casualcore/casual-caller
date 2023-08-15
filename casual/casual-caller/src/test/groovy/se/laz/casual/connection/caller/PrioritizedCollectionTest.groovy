/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import spock.lang.Specification

class PrioritizedCollectionTest extends Specification
{
   def 'testing test'()
   {
      given:
      PrioritizedCollection<String> prioritizedCollection = new PrioritizedCollection<>()
      def valueOne = 'a'
      def valueTwo = 'b'
      def valueThree = 'c'
      def priorityOne = 0L
      def priorityTwo = 1L
      def priorityThree = 2L
      when:
      prioritizedCollection.add(priorityOne, [valueOne, valueThree])
      prioritizedCollection.add(priorityTwo, [valueTwo, valueOne])
      def atPriorityOne = prioritizedCollection.get(priorityOne)
      def atPriorityTwo = prioritizedCollection.get(priorityTwo)
      def atPriorityThree = prioritizedCollection.get(priorityThree)
      then:
      atPriorityOne.sort() == [valueOne, valueThree].sort()
      atPriorityTwo.sort() == [valueTwo, valueOne].sort()
      atPriorityThree.isEmpty()
   }
}
