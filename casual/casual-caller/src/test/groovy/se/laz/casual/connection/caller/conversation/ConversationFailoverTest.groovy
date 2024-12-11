package se.laz.casual.connection.caller.conversation

import se.laz.casual.api.Conversation
import se.laz.casual.api.conversation.TpConnectReturn
import se.laz.casual.api.flags.AtmiFlags
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.Flag
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
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
}
