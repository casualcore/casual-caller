/*
 * Copyright (c) 2023 - 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

import jakarta.resource.ResourceException
import jakarta.resource.spi.ResourceAllocationException
import jakarta.transaction.Status
import jakarta.transaction.SystemException
import jakarta.transaction.TransactionManager
import se.laz.casual.api.buffer.CasualBuffer
import se.laz.casual.api.buffer.ServiceReturn
import se.laz.casual.api.buffer.type.ServiceBuffer
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.Flag
import se.laz.casual.api.flags.ServiceReturnState
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.network.connection.CasualConnectionException
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class FailoverAlgorithmTest extends Specification
{
   @Shared
   FailoverAlgorithm failoverAlgorithm = new FailoverAlgorithm()
   @Shared
   ServiceReturn<CasualBuffer> serviceReturnSuccess = new ServiceReturn<>(ServiceBuffer.empty(), ServiceReturnState.TPSUCCESS, ErrorState.OK, 0L)
   @Shared
   ServiceReturn<CasualBuffer> serviceReturnTpenoent = new ServiceReturn<>(ServiceBuffer.empty(), ServiceReturnState.TPFAIL, ErrorState.TPENOENT, 0L)
   @Shared
   TransactionManager transactionManager = Mock(TransactionManager){
      getStatus() >> Status.STATUS_ACTIVE
   }

   def setup()
   {
      failoverAlgorithm.setTransactionManager(transactionManager)
   }

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
              {con,execution -> con.tpcall(service, ServiceBuffer.empty(), Flag.of(), execution)},
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
              {con, execution -> con.tpcall(service, ServiceBuffer.empty(), Flag.of(), execution)},
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
              {con, execution -> con.tpcall(service, ServiceBuffer.empty(), Flag.of(), execution)},
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
              {con, execution -> con.tpcall(service1, ServiceBuffer.empty(), Flag.of(), execution)},
              {serviceReturnTpenoent})

      ServiceReturn<CasualBuffer> response2 = failoverAlgorithm.tpcallWithFailover(
              service2,
              lookup,
              {con, execution -> con.tpcall(service2, ServiceBuffer.empty(), Flag.of(), execution)},
              {serviceReturnTpenoent})

      then:
      response1.errorState == ErrorState.OK
      response2.errorState == ErrorState.OK

      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 1
      TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().poolName() == pool1name
   }

   // it should fail hard and retry will then distpach all calls to another pool ( if available)
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
              {con, execution -> con.tpcall(service1, ServiceBuffer.empty(), Flag.of(), execution)},
              {serviceReturnTpenoent})

      then:
      response1.errorState == ErrorState.OK
      TransactionPoolMapper.getInstance().getNumberOfTrackedTransactions() == 1
      TransactionPoolMapper.getInstance().getStickyInformationForCurrentTransaction().poolName() == pool1name
   }

   def 'invocation or close failure never tries another factory: sticky=#sticky, closeFailure=#closeFailure'()
   {
      given:
      if (sticky) { enableTransactionStickyForTest() }
      def failure = new CasualConnectionException('Domain disconnected')
      def connection = Mock(CasualConnection)
      def factory = Mock(CasualConnectionFactory)
      def entry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getJndiName() >> 'eis/first'
         getConnectionFactory() >> factory
      }
      def nextFactory = Mock(CasualConnectionFactory)
      def nextEntry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getConnectionFactory() >> nextFactory
      }
      def lookup = Mock(ConnectionFactoryLookup) {
         get('service1') >> [entry, nextEntry]
      }

      when:
      failoverAlgorithm.tpcallWithFailover('service1', lookup,
              { con, execution -> con.tpcall('service1', ServiceBuffer.empty(), Flag.of(), execution) },
              { serviceReturnTpenoent })

      then:
      1 * factory.getConnection() >> connection
      0 * factory.isDomainDisconnecting()
      1 * connection.tpcall(*_) >> {
         if (!closeFailure) { throw failure }
         serviceReturnSuccess
      }
      1 * connection.close() >> { if (closeFailure) { throw failure } }
      1 * entry.invalidate()
      0 * nextFactory.getConnection()
      def error = thrown(CasualResourceException)
      error.cause.is(failure)

      where:
      sticky | closeFailure
      false  | false
      true   | false
      false  | true
      true   | true
   }

   def 'acquisition failure retries only with an eligible transaction: sticky=#sticky, status=#status'()
   {
      given:
      if (sticky) { enableTransactionStickyForTest() }
      def tm = Mock(TransactionManager) { getStatus() >> status }
      failoverAlgorithm.setTransactionManager(tm)
      def failure = new ResourceAllocationException('Domain disconnecting')
      def factory = Mock(CasualConnectionFactory)
      def entry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getJndiName() >> 'eis/first'
         getConnectionFactory() >> factory
      }
      def connection = Mock(CasualConnection)
      def nextFactory = Mock(CasualConnectionFactory)
      def nextEntry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getConnectionFactory() >> nextFactory
      }
      def lookup = Mock(ConnectionFactoryLookup) { get('service1') >> [entry, nextEntry] }

      when:
      def result
      CasualResourceException failureResult
      try {
         result = failoverAlgorithm.tpcallWithFailover('service1', lookup,
                 { con, execution -> con.tpcall('service1', ServiceBuffer.empty(), Flag.of(), execution) },
                 { serviceReturnTpenoent })
      } catch (CasualResourceException e) { failureResult = e }

      then:
      1 * factory.getConnection() >> { throw failure }
      1 * entry.invalidate()
      (retry ? 1 : 0) * nextFactory.getConnection() >> connection
      (retry ? 1 : 0) * connection.tpcall(*_) >> serviceReturnSuccess
      (retry ? 1 : 0) * connection.close()
      retry ? result.is(serviceReturnSuccess) : failureResult.cause.is(failure)

      where:
      sticky | status                      | retry
      false  | Status.STATUS_ACTIVE        | true
      true   | Status.STATUS_ACTIVE        | true
      false  | Status.STATUS_NO_TRANSACTION| true
      false  | Status.STATUS_MARKED_ROLLBACK| false
      true   | Status.STATUS_MARKED_ROLLBACK| false
      false  | Status.STATUS_COMMITTING    | false
      true   | Status.STATUS_UNKNOWN       | false
   }

   def 'asynchronous completion failure is returned without failover: sticky=#sticky'()
   {
      given:
      if (sticky) { enableTransactionStickyForTest() }
      def future = new CompletableFuture<Optional<ServiceReturn<CasualBuffer>>>()
      def connection = Mock(CasualConnection)
      def factory = Mock(CasualConnectionFactory) { getConnection() >> connection }
      def entry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getJndiName() >> 'eis/first'
         getConnectionFactory() >> factory
      }
      def nextFactory = Mock(CasualConnectionFactory)
      def nextEntry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getConnectionFactory() >> nextFactory
      }
      def lookup = Mock(ConnectionFactoryLookup) { get('service1') >> [entry, nextEntry] }
      def failure = new IllegalStateException('Connection lost')

      when:
      def result = failoverAlgorithm.tpacallWithFailover('service1', lookup,
              { con, execution -> future }, { throw new AssertionError('Unexpected TPENOENT') })
      future.completeExceptionally(failure)
      result.join()

      then:
      1 * connection.close()
      0 * nextFactory.getConnection()
      def error = thrown(CompletionException)
      error.cause.is(failure)

      where:
      sticky << [false, true]
   }


   def 'unreadable transaction status prevents acquisition retry: sticky=#sticky'()
   {
      given:
      if (sticky) { enableTransactionStickyForTest() }
      def statusFailure = new SystemException('Status unavailable')
      failoverAlgorithm.setTransactionManager(Mock(TransactionManager) {
         getStatus() >> { throw statusFailure }
      })
      def factory = Mock(CasualConnectionFactory)
      def entry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getJndiName() >> 'eis/first'
         getConnectionFactory() >> factory
      }
      def next = Mock(ConnectionFactoryEntry) { isValid() >> true }
      def lookup = Mock(ConnectionFactoryLookup) { get('service1') >> [entry, next] }

      when:
      failoverAlgorithm.tpcallWithFailover('service1', lookup,
              { con, execution -> throw new AssertionError('No connection acquired') },
              { serviceReturnTpenoent })

      then:
      1 * factory.getConnection() >> { throw new ResourceException('Allocation failed') }
      1 * entry.invalidate()
      0 * next.getConnectionFactory()
      def failure = thrown(CasualResourceException)
      failure.cause.is(statusFailure)

      where:
      sticky << [false, true]
   }

   def 'unexpected acquisition exception propagates without retry: sticky=#sticky'()
   {
      given:
      if (sticky) { enableTransactionStickyForTest() }
      def acquisitionFailure = new IllegalStateException('Unexpected allocation failure')
      def factory = Mock(CasualConnectionFactory)
      def entry = Mock(ConnectionFactoryEntry) {
         isValid() >> true
         getJndiName() >> 'eis/first'
         getConnectionFactory() >> factory
      }
      def next = Mock(ConnectionFactoryEntry) { isValid() >> true }
      def lookup = Mock(ConnectionFactoryLookup) { get('service1') >> [entry, next] }

      when:
      failoverAlgorithm.tpcallWithFailover('service1', lookup,
              { con, execution -> throw new AssertionError('No connection acquired') },
              { serviceReturnTpenoent })

      then:
      1 * factory.getConnection() >> { throw acquisitionFailure }
      0 * next.getConnectionFactory()
      def failure = thrown(IllegalStateException)
      failure.is(acquisitionFailure)

      where:
      sticky << [false, true]
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
