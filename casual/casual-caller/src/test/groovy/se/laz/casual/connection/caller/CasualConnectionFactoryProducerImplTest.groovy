package se.laz.casual.connection.caller


import spock.lang.Specification

class CasualConnectionFactoryProducerImplTest extends Specification
{
   def 'failed construction'()
   {
      when:
      ConnectionFactoryProducerImpl.of(null)
      then:
      thrown(NullPointerException)
   }

   def 'ok construction'()
   {
      given:
      def jndiName = 'foo'
      when:
      def producer = ConnectionFactoryProducerImpl.of(jndiName)
      then:
      producer.getUniqueName() == jndiName
   }
}
