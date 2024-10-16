package se.laz.casual.connection.caller

import se.laz.casual.api.buffer.CasualBuffer
import se.laz.casual.api.buffer.ServiceReturn
import se.laz.casual.api.flags.AtmiFlags
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.Flag
import se.laz.casual.api.flags.ServiceReturnState
import se.laz.casual.api.queue.*
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import spock.lang.Specification

import javax.resource.ResourceException
import javax.resource.spi.EISSystemException
import java.util.concurrent.CompletableFuture

class CasualCallerImplTest extends Specification
{
    ConnectionFactoryLookup lookup
    CasualCallerImpl instance
    ConnectionFactoryEntryStore connectionFactoryProvider
    CasualConnectionFactory fallBackConnectionFactory
    ConnectionFactoryEntry fallBackEntry
    TransactionLess transactionLess

    def setup()
    {
        fallBackConnectionFactory = Mock(CasualConnectionFactory)
        def fallbackConnection = Mock(CasualConnection)
        fallbackConnection.tpcall('does not exist', _, _) >> {
            ServiceReturn<CasualBuffer> serviceReturn = new ServiceReturn<CasualBuffer>(Mock(CasualBuffer), ServiceReturnState.TPFAIL, ErrorState.TPENOENT, 0)
            return serviceReturn
        }
        fallBackConnectionFactory.getConnection() >> fallbackConnection
        def fallbackProducer = Mock(ConnectionFactoryProducer) {
           getConnectionFactory() >> {
              fallbackConnection
           }
           getJndiName() >> {
              'fallback-jndi'
           }
        }
        fallBackEntry = ConnectionFactoryEntry.of(fallbackProducer)
        lookup = Mock(ConnectionFactoryLookup)
        connectionFactoryProvider = Mock(ConnectionFactoryEntryStore)
        connectionFactoryProvider.get() >> {
            [fallBackEntry]
        }
        transactionLess = new TransactionLess()
        instance = new CasualCallerImpl(lookup, connectionFactoryProvider, transactionLess, Mock(FailedDomainDiscoveryHandler))
    }

    def 'construction, no entries found - should throw'()
    {
        given:
        ConnectionFactoryEntryStore provider = Mock(ConnectionFactoryEntryStore)
        provider.get() >> []
        when:
        new CasualCallerImpl(lookup, provider, new TransactionLess(), Mock(FailedDomainDiscoveryHandler))
        then:
        thrown(CasualCallerException)
    }

    def 'tpcall fail getting connection from connection factory'()
    {
        given:
        def serviceName = 'echo'
        def connectionFactory = Mock(CasualConnectionFactory)
        connectionFactory.getConnection() >> {
            throw new EISSystemException("oopsie")
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(serviceName) >> {
            entries
        }
        when:
        instance.tpcall(serviceName, Mock(CasualBuffer), Flag.of(AtmiFlags.NOFLAG))
        then:
        def e = thrown(CasualResourceException)
        e.getCause() instanceof ResourceException
    }

    def 'tpcall fail, TPENOENT - no such service handled'()
    {
        given:
        def serviceName = 'does not exist'
        def connectionFactory = Mock(CasualConnectionFactory)
        connectionFactory.getConnection() >> {
            throw new EISSystemException("oopsie")
        }
        lookup.get(serviceName) >> {
            []
        }
        when:
        ServiceReturn<CasualBuffer> result = instance.tpcall(serviceName, Mock(CasualBuffer), Flag.of(AtmiFlags.NOFLAG))
        then:
        result.errorState == ErrorState.TPENOENT
        result.serviceReturnState == ServiceReturnState.TPFAIL
    }


    def 'tpacall fail getting connection from connection factory'()
    {
        given:
        def serviceName = 'echo'
        def connectionFactory = Mock(CasualConnectionFactory)
        connectionFactory.getConnection() >> {
            throw new EISSystemException("oopsie")
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(serviceName) >> {
            entries
        }
        when:
        instance.tpacall(serviceName, Mock(CasualBuffer), Flag.of(AtmiFlags.NOFLAG))
        then:
        def e = thrown(CasualResourceException)
        e.getCause() instanceof ResourceException
    }

    def 'tpcall cache has service, but its connection factory is currently invalid'()
    {
        given:
        def serviceName = 'someservice'
        def connectionFactoryEntry = Mock(ConnectionFactoryEntry)
        connectionFactoryEntry.isValid() >> false
        connectionFactoryEntry.isInvalid() >> true
        def entries = [connectionFactoryEntry]
        lookup.get(serviceName) >> {
            entries
        }
        when:
        def reply = instance.tpcall(serviceName, Mock(CasualBuffer), Flag.of(AtmiFlags.NOFLAG))
        then:
        reply.getServiceReturnState() == ServiceReturnState.TPFAIL
        reply.getErrorState() == ErrorState.TPENOENT
    }


    def 'dequeue fail getting connection from connection factory'()
    {
        given:
        def queueInfo = QueueInfo.of("bar.foo")
        def messageSelector = MessageSelector.of()
        def connectionFactory = Mock(CasualConnectionFactory)
        connectionFactory.getConnection() >> {
            throw new EISSystemException("oopsie")
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(queueInfo) >> {
            entries
        }
        when:
        instance.dequeue(queueInfo, messageSelector)
        then:
        def e = thrown(CasualResourceException)
        e.getCause() instanceof ResourceException
    }

    def 'enqueue fail getting connection from connection factory'()
    {
        given:
        def queueInfo = QueueInfo.of("bar.foo")
        def queueMessage = QueueMessage.of(Mock(CasualBuffer))
        def connectionFactory = Mock(CasualConnectionFactory)
        connectionFactory.getConnection() >> {
            throw new EISSystemException("oopsie")
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(queueInfo) >> {
            entries
        }
        when:
        instance.enqueue(queueInfo, queueMessage)
        then:
        def e = thrown(CasualResourceException)
        e.getCause() instanceof ResourceException
    }

    def 'tpcall ok'()
    {
        given:
        def serviceName = 'echo'
        def connectionFactory = Mock(CasualConnectionFactory)
        def serviceReturn = createServiceReturn(Mock(CasualBuffer))
        def callingBuffer = Mock(CasualBuffer)
        def flags = Flag.of(AtmiFlags.NOFLAG)
        connectionFactory.getConnection() >> {
            def connection = Mock(CasualConnection)
            1 * connection.tpcall(serviceName, callingBuffer, flags, _ as UUID) >> serviceReturn
            return connection
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(serviceName) >> {
            entries
        }
        when:
        def actual = instance.tpcall(serviceName, callingBuffer, flags)
        then:
        actual == serviceReturn
    }

    def 'tpacall ok'()
    {
        given:
        def serviceName = 'echo'
        def connectionFactory = Mock(CasualConnectionFactory)
        def future = new CompletableFuture()
        def callingBuffer = Mock(CasualBuffer)
        def flags = Flag.of(AtmiFlags.NOFLAG)
        connectionFactory.getConnection() >> {
            def connection = Mock(CasualConnection)
            1 * connection.tpacall(serviceName, callingBuffer, flags, _ as UUID) >> future
            return connection
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(serviceName) >> {
            entries
        }
        when:
        def actual = instance.tpacall(serviceName, callingBuffer, flags)
        then:
        actual == future
    }

    def 'tpcall, in transaction'()
    {
       given:
       TransactionLess transactionLess = Mock(TransactionLess) {
          0 * tpcall(_)
       }
       def caller = new CasualCallerImpl(lookup, connectionFactoryProvider, transactionLess, Mock(FailedDomainDiscoveryHandler))
       caller.tpCaller = Mock(TpCallerFailover)
       when:
       caller.tpcall("foo", Mock(CasualBuffer), Flag.of(AtmiFlags.NOFLAG))
       then:
       noExceptionThrown()
    }

   def 'TPNOTRAN tpcall, no transaction'()
   {
      given:
      TransactionLess transactionLess = Mock(TransactionLess) {
         1 * tpcall(_)
      }
      def caller = new CasualCallerImpl(lookup, connectionFactoryProvider, transactionLess, Mock(FailedDomainDiscoveryHandler))
      caller.tpCaller = Mock(TpCallerFailover)
      when:
      caller.tpcall("foo", Mock(CasualBuffer), Flag.of(AtmiFlags.TPNOTRAN))
      then:
      noExceptionThrown()
   }


   def 'tpacall in transaction'()
   {
      given:
      TransactionLess transactionLess = Mock(TransactionLess) {
         0 * tpacall(_)
      }
      def caller = new CasualCallerImpl(lookup, connectionFactoryProvider, transactionLess, Mock(FailedDomainDiscoveryHandler))
      caller.tpCaller = Mock(TpCallerFailover)
      when:
      caller.tpacall("foo", Mock(CasualBuffer), Flag.of(AtmiFlags.NOFLAG))
      then:
      noExceptionThrown()
   }

   def 'TPNOTRAN tpacall no transaction'()
   {
      given:
      TransactionLess transactionLess = Mock(TransactionLess) {
         1 * tpacall(_)
      }
      def caller = new CasualCallerImpl(lookup, connectionFactoryProvider, transactionLess, Mock(FailedDomainDiscoveryHandler))
      caller.tpCaller = Mock(TpCallerFailover)
      when:
      caller.tpacall("foo", Mock(CasualBuffer), Flag.of(AtmiFlags.TPNOTRAN))
      then:
      noExceptionThrown()
   }

    def 'enqueue ok'()
    {
        given:
        def queueInfo = QueueInfo.of("bar.foo")
        def queueMessage = QueueMessage.of(Mock(CasualBuffer))
        def connectionFactory = Mock(CasualConnectionFactory)
        def uuid = UUID.randomUUID()
        connectionFactory.getConnection() >> {
            def connection = Mock(CasualConnection)
            1 * connection.enqueue(queueInfo, queueMessage) >> EnqueueReturn.createBuilder().withErrorState(ErrorState.OK).withId(uuid).build()
            return connection
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(queueInfo) >> {
            entries
        }
        when:
        EnqueueReturn actual = instance.enqueue(queueInfo, queueMessage)
        then:
        actual == EnqueueReturn.createBuilder().withErrorState(ErrorState.OK).withId(uuid).build()
    }

    def 'dequeue ok'()
    {
        given:
        def queueInfo = QueueInfo.of("bar.foo")
        def messageSelector = MessageSelector.of()
        def queueMessage = QueueMessage.of(Mock(CasualBuffer))
        def connectionFactory = Mock(CasualConnectionFactory)
        connectionFactory.getConnection() >> {
            def connection = Mock(CasualConnection)
            1 * connection.dequeue(queueInfo, messageSelector) >> DequeueReturn.createBuilder().withErrorState(ErrorState.OK).withQueueMessage(queueMessage).build()
            return connection
        }
        def producer = Mock(ConnectionFactoryProducer){
           getConnectionFactory() >> {
              connectionFactory
           }
           getJndiName() >> {
              'someJndiName'
           }
        }
        def entries = [ConnectionFactoryEntry.of(producer)]
        lookup.get(queueInfo) >> {
            entries
        }
        when:
        def actual = instance.dequeue(queueInfo, messageSelector)
        then:
        actual == DequeueReturn.createBuilder().withErrorState(ErrorState.OK).withQueueMessage(queueMessage).build()
    }

    ServiceReturn<CasualBuffer> createServiceReturn(CasualBuffer casualBuffer)
    {
        new ServiceReturn<CasualBuffer>(casualBuffer, ServiceReturnState.TPSUCCESS, ErrorState.OK, 0)
    }
}
