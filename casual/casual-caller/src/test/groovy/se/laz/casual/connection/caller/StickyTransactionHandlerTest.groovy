/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import jakarta.transaction.Status
import jakarta.transaction.TransactionManager
import se.laz.casual.api.buffer.CasualBuffer
import se.laz.casual.api.buffer.ServiceReturn
import se.laz.casual.api.buffer.type.ServiceBuffer
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.Flag
import se.laz.casual.api.flags.ServiceReturnState
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import spock.lang.Shared
import spock.lang.Specification

class StickyTransactionHandlerTest extends Specification
{
   @Shared
   ServiceReturn<CasualBuffer> serviceReturnSuccess = new ServiceReturn<>(ServiceBuffer.empty(), ServiceReturnState.TPSUCCESS, ErrorState.OK, 0L)

   def cleanup()
   {
      TransactionPoolMapper.resetForTest()
   }

   def 'sticky not active'()
   {
      given:
      TransactionPoolMapper mapper = Mock(TransactionPoolMapper){
         isPoolMappingActive() >> false
      }
      when:
      Optional result = StickyTransactionHandler.handleTransactionSticky('serviceOne', [], {con, execution -> }, () -> mapper)
      then:
      result.isEmpty()
   }

   def '2 calls in the same transaction, sticky - should use the same connection and execution'()
   {
      given:
      enableTransactionStickyForTest()
      def poolName = "eis/pool-one"
      def expectedNumberOfCalls = 2
      def connectionFactory = getFactoryMockServiceReturn(poolName, serviceReturnSuccess, expectedNumberOfCalls)
      def factories = [connectionFactory]
      def factoriesSubsequentCall = [connectionFactory]
      def service1 = "service1"
      def service2 = "service2"
      when:
      Optional<ServiceReturn<CasualBuffer>> response = StickyTransactionHandler.handleTransactionSticky(service1,
              factories,
              {con, execution -> con.tpcall(service1, ServiceBuffer.empty(), Flag.of(), execution)},
              {TransactionPoolMapper.getInstance()})
      then:
      response.get().errorState == ErrorState.OK
      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 1
      TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().poolName() == poolName
      when:
      UUID sharedExecution = TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().execution()
      Optional<ServiceReturn<CasualBuffer>> response2 = StickyTransactionHandler.handleTransactionSticky(service2,
              factoriesSubsequentCall,
              {con, execution -> con.tpcall(service2, ServiceBuffer.empty(), Flag.of(), execution)},
              {TransactionPoolMapper.getInstance()})
      then:
      response2.get().errorState == ErrorState.OK
      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 1
      TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().poolName() == poolName
      TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().execution() == sharedExecution
   }

   private ConnectionFactoryEntry getFactoryMockServiceReturn(String jndiName, ServiceReturn<CasualBuffer> expectedReturn, long expectedCalls)
   {
      CasualConnection connection = Mock(CasualConnection)
      expectedCalls * connection.tpcall(*_) >> expectedReturn

      CasualConnectionFactory connectionFactory = Mock(CasualConnectionFactory)
      expectedCalls * connectionFactory.getConnection() >> connection

      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry)
      connectionFactoryEntry.isValid() >> true
      connectionFactoryEntry.isInvalid() >> false
      connectionFactoryEntry.getJndiName() >> jndiName
      connectionFactoryEntry.getConnectionFactory() >> connectionFactory

      return connectionFactoryEntry
   }

   def enableTransactionStickyForTest()
   {
      def transactionManager = Mock(TransactionManager)
      def transaction = new TransactionImpl(Status.STATUS_ACTIVE)
      transactionManager.getTransaction() >> {
         transaction
      }
      TransactionPoolMapper.getInstance().setActiveForTest(true)
      TransactionPoolMapper.getInstance().setTransactionManager(transactionManager)
   }

}
