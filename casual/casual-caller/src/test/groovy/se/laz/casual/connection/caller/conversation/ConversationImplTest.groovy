package se.laz.casual.connection.caller.conversation

import se.laz.casual.api.Conversation
import se.laz.casual.api.buffer.CasualBuffer
import se.laz.casual.api.buffer.ConversationReturn
import se.laz.casual.api.conversation.Duplex
import se.laz.casual.jca.CasualConnection
import spock.lang.Specification

class ConversationImplTest extends Specification
{
   def'test all interactions'()
   {
      given:
      def connection = Mock(CasualConnection){
         1 * close()
      }
      def isSending = true
      def isReceiving = !isSending
      def buffer = Mock(CasualBuffer)
      def handoverControl = false
      def userCode = 42
      def isDirectionSwitched = false
      def conversationReturn = ConversationReturn<CasualBuffer>.of(Mock(CasualBuffer), userCode, Duplex.SEND)
      def conversation = Mock(Conversation){
         1 * tpdiscon()
         1 * tprecv() >> conversationReturn
         1 * it.isSending() >> isSending
         1 * it.isReceiving() >> isReceiving
         1 * tpsend(buffer, handoverControl)
         1 * tpsend(buffer, handoverControl, userCode)
         1 * it.isDirectionSwitched() >> isDirectionSwitched
         1 * close()
      }
      when:
      try(Conversation theConversation = ConversationImpl.of(connection, conversation))
      {
         assert theConversation.tprecv() == conversationReturn
         assert theConversation.isSending() == isSending
         assert theConversation.isReceiving() == isReceiving
         theConversation.tpsend(buffer, handoverControl)
         theConversation.tpsend(buffer, handoverControl, userCode)
         assert theConversation.isDirectionSwitched() == isDirectionSwitched
         theConversation.tpdiscon()
      }
      then:
      noExceptionThrown()
   }
}
