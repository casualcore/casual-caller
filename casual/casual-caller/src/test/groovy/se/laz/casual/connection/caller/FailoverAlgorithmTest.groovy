/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

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

import jakarta.resource.ResourceException
import jakarta.transaction.Status
import jakarta.transaction.TransactionManager

class FailoverAlgorithmTest extends Specification
{
   @Shared
   FailoverAlgorithm failoverAlgorithm = new FailoverAlgorithm()
   @Shared
   ServiceReturn<CasualBuffer> serviceReturnSuccess = new ServiceReturn<>(ServiceBuffer.empty(), ServiceReturnState.TPSUCCESS, ErrorState.OK, 0L)
   @Shared
   ServiceReturn<CasualBuffer> serviceReturnTpenoent = new ServiceReturn<>(ServiceBuffer.empty(), ServiceReturnState.TPFAIL, ErrorState.TPENOENT, 0L)

   def cleanup()
   {
      TransactionPoolMapper.resetForTest()
   }

   def 'called for service with no valid pools, results in tpenoent'()
   {
      setup:
      def lookup = Mock(ConnectionFactoryLookup)
      def service = "service1"
      1 * lookup.get(service) >> []

      when:
      ServiceReturn<CasualBuffer> response = failoverAlgorithm.tpcallWithFailover(
              service,
              lookup,
              {con -> con.tpcall(service, ServiceBuffer.empty(), Flag.of())},
              {serviceReturnTpenoent})

      then:
      response.serviceReturnState == ServiceReturnState.TPFAIL
      response.errorState == ErrorState.TPENOENT
   }

   def 'failover: nothing fails, only tpcall with first ConnectionFactoryEntry'()
   {
      setup:
      def pool1name = "eis/pool-one"
      def entry1 = getFactoryMockServiceReturn(pool1name, serviceReturnSuccess)
      def pool2name = "eis/pool-two"
      def entry2 = getFactoryMockServiceReturn(pool2name, serviceReturnSuccess, 0)

      def connectionFactoryEntries = [entry1, entry2]

      def lookup = Mock(ConnectionFactoryLookup)
      def service = "service1"
      1 * lookup.get(service) >> {
         connectionFactoryEntries
      }

      when:
      ServiceReturn<CasualBuffer> response = failoverAlgorithm.tpcallWithFailover(
              service,
              lookup,
              {con -> con.tpcall(service, ServiceBuffer.empty(), Flag.of())},
              {serviceReturnTpenoent})

      then:
      response.errorState == ErrorState.OK
      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 0 // because stickies disabled
   }

   def 'failover: if attempt to call service on first pool fails with recoverable error, try next pool'()
   {
      setup:
      def pool1name = "eis/pool-one"
      def entry1 = getFactoryMockThrowsOnConnection(pool1name)
      def pool2name = "eis/pool-two"
      def entry2 = getFactoryMockThrowsOnConnection(pool2name)
      def pool3name = "eis/pool-three"
      def entry3 = getFactoryMockServiceReturn(pool3name, serviceReturnSuccess)

      def connectionFactoryEntries = [entry1, entry2, entry3]

      def lookup = Mock(ConnectionFactoryLookup)
      def service = "service1"
      1 * lookup.get(service) >> {
         connectionFactoryEntries
      }

      when:
      ServiceReturn<CasualBuffer> response = failoverAlgorithm.tpcallWithFailover(
              service,
              lookup,
              {con -> con.tpcall(service, ServiceBuffer.empty(), Flag.of())},
              {serviceReturnTpenoent})

      then:
      response.errorState == ErrorState.OK
      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 0 // because stickies disabled
   }

   def 'stickies: transaction sticky enabled but call not in transaction, should use first connection factory'()
   {
      setup:
      def transactionManager = Mock(TransactionManager)
      TransactionPoolMapper.getInstance().setActiveForTest(true)
      TransactionPoolMapper.getInstance().setTransactionManager(transactionManager)

      def pool1name = "eis/pool-one"
      def entry1 = getFactoryMockServiceReturn(pool1name, serviceReturnSuccess)

      def connectionFactoryEntries = [entry1]

      def lookup = Mock(ConnectionFactoryLookup)
      def service = "service1"
      1 * lookup.get(service) >> {
         connectionFactoryEntries
      }

      when:
      ServiceReturn<CasualBuffer> response = failoverAlgorithm.tpcallWithFailover(
              service,
              lookup,
              {con -> con.tpcall(service, ServiceBuffer.empty(), Flag.of())},
              {serviceReturnTpenoent})

      then:
      response.serviceReturnState == ServiceReturnState.TPSUCCESS
      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 0
   }

   def 'stickies: second call for service not in stickied pool, should be ok, call goes to other pool where second service exists'()
   {
      setup:
      enableTransactionStickyForTest()

      def pool1name = "eis/pool-one"
      def entry1 = getFactoryMockServiceReturn(pool1name, serviceReturnSuccess)

      def pool2name = "eis/pool-two"
      def entry2 = getFactoryMockServiceReturn(pool2name, serviceReturnSuccess)

      def entriesSvc1 = [entry1]
      def entriesSvc2 = [entry2]
      def lookup = Mock(ConnectionFactoryLookup)
      def service1 = "service1"
      def service2 = "service2"
      1 * lookup.get(service1) >> {
         entriesSvc1
      }
      1 * lookup.get(service2) >> {
         entriesSvc2
      }

      when:
      ServiceReturn<CasualBuffer> response1 = failoverAlgorithm.tpcallWithFailover(
              service1,
              lookup,
              {con -> con.tpcall(service1, ServiceBuffer.empty(), Flag.of())},
              {serviceReturnTpenoent})

      ServiceReturn<CasualBuffer> response2 = failoverAlgorithm.tpcallWithFailover(
              service2,
              lookup,
              {con -> con.tpcall(service2, ServiceBuffer.empty(), Flag.of())},
              {serviceReturnTpenoent})

      then:
      response1.errorState == ErrorState.OK
      response2.errorState == ErrorState.OK

      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 1
      TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction() == pool1name
   }

   def 'stickies, failover: when calling stickied service failover is possible to other non-stickied pool'()
   {
      setup:
      enableTransactionStickyForTest()

      def pool1name = "eis/pool-one"
      def entryFails = getFactoryMockThrowsOnConnection(pool1name)

      def pool2name = "eis/pool-two"
      def entryFailover = getFactoryMockServiceReturn(pool2name, serviceReturnSuccess)

      def entriesSvc1 = [entryFails, entryFailover]

      def lookup = Mock(ConnectionFactoryLookup)
      def service1 = "service1"
      1 * lookup.get(service1) >> {
         entriesSvc1
      }

      when:
      ServiceReturn<CasualBuffer> response1 = failoverAlgorithm.tpcallWithFailover(
              service1,
              lookup,
              {con -> con.tpcall(service1, ServiceBuffer.empty(), Flag.of())},
              {serviceReturnTpenoent})

      then:
      response1.errorState == ErrorState.OK
      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 1
      TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction() == pool1name
   }

   private ConnectionFactoryEntry getFactoryMockServiceReturn(String jndiName, ServiceReturn<CasualBuffer> expectedReturn)
   {
      getFactoryMockServiceReturn(jndiName, expectedReturn, 1L)
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

   private ConnectionFactoryEntry getFactoryMockThrowsOnConnection(String jndiName)
   {
      CasualConnectionFactory connectionFactory = Mock(CasualConnectionFactory)
      1 * connectionFactory.getConnection() >> {
         throw new ResourceException("Some resource failure.")
      }

      ConnectionFactoryEntry connectionFactoryEntry = Mock(ConnectionFactoryEntry)
      connectionFactoryEntry.isValid() >> true
      connectionFactoryEntry.isInvalid() >> false
      connectionFactoryEntry.getJndiName() >> jndiName
      connectionFactoryEntry.getConnectionFactory() >> connectionFactory

      return connectionFactoryEntry
   }

   private void enableTransactionStickyForTest()
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