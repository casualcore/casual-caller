package se.laz.casual.connection.caller.conversation

import se.laz.casual.api.Conversation
import se.laz.casual.api.conversation.TpConnectReturn
import se.laz.casual.api.flags.AtmiFlags
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.Flag
import se.laz.casual.connection.caller.CasualResourceException
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.network.connection.CasualConnectionException
import spock.lang.Specification

class ConversationFailoverTest extends Specification
{
   def 'ok path, connection is automatically closed'()
   {
      given:
      def serviceName = 'chatty'
      def data = null
      def flags = Flag.of(AtmiFlags.NOFLAG)
      def conversation = Mock(Conversation) {
         1 * close()
      }
      def tpConnectReturn = TpConnectReturn.of(conversation)
      def connection = Mock(CasualConnection){
         1 * tpconnect(serviceName, data, flags) >> tpConnectReturn
         1 * close()
      }
      def connectionFactory = Mock(CasualConnectionFactory){
         1 * getConnection() >> connection
      }
      def connectionFactoryEntry = Mock(ConnectionFactoryEntry){
         1 * getConnectionFactory() >> connectionFactory
      }
      def validEntries = [connectionFactoryEntry]
      def doCall = { con ->
         con.tpconnect(serviceName, data, flags)
      }
      when:
      try(TpConnectReturn connectReturn = ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall))
      {
         assert connectReturn.getErrorState() == ErrorState.OK
         ConversationImpl impl = connectReturn.getConversation().get()
         assert impl.conversation == conversation
         assert impl.connection == connection
      }
      then:
      noExceptionThrown()
   }

   def 'only one valid connection factory and it throws'()
   {
      given:
      def serviceName = 'chatty'
      def data = null
      def flags = Flag.of(AtmiFlags.NOFLAG)
      def connectionFactory = Mock(CasualConnectionFactory){
         1 * getConnection() >> {
            throw new CasualConnectionException("Bazinga!")
         }
      }
      def connectionFactoryEntry = Mock(ConnectionFactoryEntry){
         1 * getConnectionFactory() >> connectionFactory
         1 * invalidate()
         1 * getJndiName() >> "jndi-foo"
      }
      def validEntries = [connectionFactoryEntry]
      def doCall = { con ->
         con.tpconnect(serviceName, data, flags)
      }
      when:
      try(TpConnectReturn connectReturn = ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall))
      {}
      then:
      thrown(CasualResourceException)
   }

   def 'ok - one connection factory throws, the second works as expected'()
   {
      given:
      def serviceName = 'chatty'
      def data = null
      def flags = Flag.of(AtmiFlags.NOFLAG)
      def conversation = Mock(Conversation) {
         1 * close()
      }
      def tpConnectReturn = TpConnectReturn.of(conversation)
      def connection = Mock(CasualConnection){
         1 * tpconnect(serviceName, data, flags) >> tpConnectReturn
         1 * close()
      }
      def connectionFactory = Mock(CasualConnectionFactory){
         1 * getConnection() >> connection
      }
      def connectionFactoryEntry = Mock(ConnectionFactoryEntry){
         1 * getConnectionFactory() >> connectionFactory
      }
      def failedConnectionFactory = Mock(CasualConnectionFactory){
         1 * getConnection() >> {
            throw new jakarta.resource.ResourceException('Bazinga!')
         }
      }
      def failedConnectionFactoryEntry = Mock(ConnectionFactoryEntry){
         1 * getConnectionFactory() >> failedConnectionFactory
         1 * invalidate()
      }
      def validEntries = [failedConnectionFactoryEntry, connectionFactoryEntry]
      def doCall = { con ->
         con.tpconnect(serviceName, data, flags)
      }
      when:
      try(TpConnectReturn connectReturn = ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall))
      {
         assert connectReturn.getErrorState() == ErrorState.OK
         ConversationImpl impl = connectReturn.getConversation().get()
         assert impl.conversation == conversation
         assert impl.connection == connection
      }
      then:
      noExceptionThrown()
   }
}
