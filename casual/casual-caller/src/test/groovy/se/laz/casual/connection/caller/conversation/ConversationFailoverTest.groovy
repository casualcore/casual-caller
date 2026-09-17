package se.laz.casual.connection.caller.conversation

import se.laz.casual.api.Conversation
import se.laz.casual.api.conversation.TpConnectReturn
import se.laz.casual.api.flags.AtmiFlags
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.Flag
import se.laz.casual.connection.caller.CasualCallerException
import se.laz.casual.connection.caller.CasualResourceException
import se.laz.casual.connection.caller.ConnectionFactoryEntry
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.network.connection.CasualConnectionException
import spock.lang.Specification

class ConversationFailoverTest extends Specification
{
   def 'ok - connection and conversation are automatically closed'()
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
      try(TpConnectReturn connectReturn = ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall, { true }))
      {
         assert connectReturn.getErrorState() == ErrorState.OK
         ConversationImpl impl = connectReturn.getConversation().get()
         assert impl.conversation == conversation
         assert impl.connection == connection
      }
      then:
      noExceptionThrown()
   }

   def 'unexpected acquisition failure propagates'()
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
         0 * invalidate()
      }
      def validEntries = [connectionFactoryEntry]
      def doCall = { con ->
         con.tpconnect(serviceName, data, flags)
      }
      when:
      try(TpConnectReturn connectReturn = ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall, { true }))
      {}
      then:
      thrown(CasualConnectionException)
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
      try(TpConnectReturn connectReturn = ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall, { true }))
      {
         assert connectReturn.getErrorState() == ErrorState.OK
         ConversationImpl impl = connectReturn.getConversation().get()
         assert impl.conversation == conversation
         assert impl.connection == connection
      }
      then:
      noExceptionThrown()
   }

   def 'ErrorState.OK but missing conversation'()
   {
      given:
      def serviceName = 'chatty'
      def data = null
      def flags = Flag.of(AtmiFlags.NOFLAG)
      def tpConnectReturn = TpConnectReturn.of(ErrorState.OK)
      def connection = Mock(CasualConnection){
         1 * tpconnect(serviceName, data, flags) >> tpConnectReturn
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
      ConversationFailover.tpconnectWithFailover(serviceName, validEntries, doCall)
      then:
      def failure = thrown(CasualResourceException)
      failure.cause instanceof CasualCallerException
      1 * connection.close()
      1 * connectionFactoryEntry.invalidate()
   }
   def 'conversation invocation failure closes the handle and never retries'()
   {
      given:
      def connection = Mock(CasualConnection)
      def factory = Mock(CasualConnectionFactory)
      def entry = Mock(ConnectionFactoryEntry)
      def next = Mock(ConnectionFactoryEntry)
      def failure = new jakarta.resource.ResourceException('Invocation failed')
      def closeFailure = new IllegalStateException('Close failed')

      when:
      ConversationFailover.tpconnectWithFailover('chatty', [entry, next],
              { con -> throw failure }, { throw new AssertionError('Must not consider retry') })

      then:
      1 * entry.getConnectionFactory() >> factory
      1 * factory.getConnection() >> connection
      1 * connection.close() >> { throw closeFailure }
      1 * entry.invalidate()
      0 * next.getConnectionFactory()
      def thrownFailure = thrown(CasualResourceException)
      thrownFailure.cause.is(failure)
      failure.suppressed.toList() == [closeFailure]
   }

   def 'failed acquisition does not retry when transaction disallows it'()
   {
      given:
      def factory = Mock(CasualConnectionFactory)
      def entry = Mock(ConnectionFactoryEntry)
      def next = Mock(ConnectionFactoryEntry)
      def failure = new jakarta.resource.ResourceException('Domain unavailable')

      when:
      ConversationFailover.tpconnectWithFailover('chatty', [entry, next],
              { con -> throw new AssertionError('No connection acquired') }, { false })

      then:
      1 * entry.getConnectionFactory() >> factory
      1 * factory.getConnection() >> { throw failure }
      1 * entry.invalidate()
      0 * next.getConnectionFactory()
      def thrownFailure = thrown(CasualResourceException)
      thrownFailure.cause.is(failure)
   }

   def 'unsuccessful conversation response closes the handle without retry'()
   {
      given:
      def connection = Mock(CasualConnection)
      def factory = Mock(CasualConnectionFactory)
      def entry = Mock(ConnectionFactoryEntry)
      def next = Mock(ConnectionFactoryEntry)
      def response = TpConnectReturn.of(ErrorState.TPESVCFAIL)

      when:
      def result = ConversationFailover.tpconnectWithFailover('chatty', [entry, next],
              { con -> response }, { throw new AssertionError('Must not consider retry') })

      then:
      1 * entry.getConnectionFactory() >> factory
      1 * factory.getConnection() >> connection
      1 * connection.close()
      0 * next.getConnectionFactory()
      result.is(response)
   }

}
