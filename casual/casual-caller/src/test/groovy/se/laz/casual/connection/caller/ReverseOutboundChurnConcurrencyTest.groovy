/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

import jakarta.resource.ResourceException
import jakarta.transaction.TransactionManager
import se.laz.casual.api.buffer.CasualBuffer
import se.laz.casual.api.buffer.ServiceReturn
import se.laz.casual.api.buffer.type.OctetBuffer
import se.laz.casual.api.flags.AtmiFlags
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.Flag
import se.laz.casual.api.flags.ServiceReturnState
import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.CasualRequestInfo
import se.laz.casual.jca.DomainId
import se.laz.casual.network.connection.DomainDisconnectedException
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Test reverse inbound domains coming and going under load
 */
class ReverseOutboundChurnConcurrencyTest extends Specification
{
    private static final Logger CALLER_LOGGER = Logger.getLogger('se.laz.casual.connection.caller')
    private static final int CALL_TIMEOUT_SECONDS = 30
    private static final int SIMULATED_CALL_MILLIS = 20
    private static final int TEST_VALIDATION_INTERVAL_MILLIS = 500
    private Level previousLogLevel

    def setup()
    {
        previousLogLevel = CALLER_LOGGER.level
        CALLER_LOGGER.level = Level.SEVERE
    }

    def cleanup()
    {
        CALLER_LOGGER.level = previousLogLevel
    }

    def 'concurrent tpcalls under rapid reverse outbound connection churn and cache invalidation'()
    {
        given:
        def domainA = DomainId.of(UUID.randomUUID())
        def domainB = DomainId.of(UUID.randomUUID())
        def domainC = DomainId.of(UUID.randomUUID())

        def activeDomains = new AtomicReference<List<DomainId>>([domainA, domainB])

        def serviceName = 'counterService'
        def payload = OctetBuffer.of([1, 2, 3] as byte[])
        def flags = Flag.of(AtmiFlags.NOFLAG)
        def okReturn = new ServiceReturn<>(
                payload,
                ServiceReturnState.TPSUCCESS,
                ErrorState.OK,
                0
        )

        def environment = createEnvironment(
                activeDomains,
                serviceName,
                okReturn
        )

        def topologyRotation = [
                [domainB],
                [domainB, domainC],
                [domainC],
                [],
                [domainA],
                [domainA, domainC]
        ]

        def numWorkers = 8
        def callsPerWorker = 100

        when:
        def result = runConcurrentChurn(
                activeDomains,
                environment,
                serviceName,
                payload,
                flags,
                topologyRotation,
                numWorkers,
                callsPerWorker
        )

        then:
        result.finishedInTime
        result.errors.empty
        result.successCalls > 0
        result.validationRuns > 1
        result.successCalls +
                result.tpenoentCalls +
                result.transientFailures == numWorkers * callsPerWorker

        when: 'churn stops and a single domain is stabilized'
        activeDomains.set([domainA])
        environment.validator.validateAllConnections()

        def finalResult = environment.tpCaller.tpcall(
                serviceName,
                payload,
                flags,
                environment.lookupService
        )

        then: 'the final call succeeds and dead domain entries are purged'
        finalResult.errorState == ErrorState.OK

        def finalEntries = environment.store.get()
        finalEntries.size() == 1
        finalEntries[0].jndiName.contains(domainA.id.toString())
        !finalEntries.any { it.jndiName.contains(domainB.id.toString()) }
        !finalEntries.any { it.jndiName.contains(domainC.id.toString()) }
    }


    private TestEnvironment createEnvironment(AtomicReference<List<DomainId>> activeDomains,
                                              String serviceName,
                                              ServiceReturn<CasualBuffer> okReturn)
    {
        def baseJndi = 'java:/eis/casualReverse'
        CasualConnectionFactory connectionFactory = createConnectionFactory(activeDomains, serviceName, okReturn)
        ConnectionFactoryEntryStore store = createEntryStore(baseJndi, connectionFactory)
        Cache cache = new Cache()
        ConnectionValidator validator = new ConnectionValidator(
                new TestCacheRepopulator(cache, serviceName),
                store,
                cache
        )

        new TestEnvironment(
                validator,
                createCaller(),
                createLookupService(store, cache, serviceName),
                store
        )
    }

    private CasualConnectionFactory createConnectionFactory(AtomicReference<List<DomainId>> activeDomains,
                                                            String serviceName,
                                                            ServiceReturn<CasualBuffer> okReturn)
    {
        Mock(CasualConnectionFactory) {
            getConnection() >> Mock(CasualConnection)
            getConnection(_ as CasualRequestInfo) >> {
                CasualRequestInfo request ->
                    createDomainConnection(activeDomains, request, serviceName, okReturn)
            }
            isReverse() >> true
            getDomainIds() >> {new ArrayList(activeDomains.get())}
        }
    }

    private CasualConnection createDomainConnection(AtomicReference<List<DomainId>> activeDomains,
                                                      CasualRequestInfo request,
                                                      String serviceName,
                                                      ServiceReturn<CasualBuffer> okReturn)
    {
        DomainId requestedDomain = request.domainId.orElseThrow {
            new IllegalArgumentException('DomainId expected')
        }
        if (!activeDomains.get().contains(requestedDomain))
        {
            throw new jakarta.resource.spi.ResourceAllocationException("Domain $requestedDomain is disconnected")
        }

        def connection = Mock(CasualConnection)
        connection.tpcall(serviceName, _, _, _) >> {
            Thread.sleep(SIMULATED_CALL_MILLIS)
            if (!activeDomains.get().contains(requestedDomain))
            {
                throw new DomainDisconnectedException("Domain $requestedDomain dropped mid-call")
            }
            okReturn
        }
        connection
    }

    private ConnectionFactoryEntryStore createEntryStore(String baseJndi,
                                                         CasualConnectionFactory connectionFactory)
    {
        def baseProducer = new TestConnectionFactoryProducer(baseJndi, connectionFactory)
        def finder = Mock(ConnectionFactoryFinder)
        finder.findConnectionFactory(_) >> [ConnectionFactoryEntry.of(baseProducer)]

        def store = new ConnectionFactoryEntryStore(
                finder,
                Mock(TopologyChangedHandler)
        )
        store.initialize()
        store
    }

    private TpCallerFailover createCaller()
    {
        def failoverAlgorithm = new FailoverAlgorithm()
        failoverAlgorithm.setTransactionManager(Mock(TransactionManager))
        new TpCallerFailover(failoverAlgorithm)
    }

    private ConnectionFactoryLookupService createLookupService(ConnectionFactoryEntryStore store,
                                                               Cache cache,
                                                               String serviceName)
    {
        def lookup = Mock(Lookup)
        lookup.find(serviceName, _, _) >> {
            String svc, List<ConnectionFactoryEntry> entries, TransactionLess tl ->
                createDiscoveredFactories(entries)
        }
        new ConnectionFactoryLookupService(
                store,
                cache,
                lookup,
                new TransactionLess()
        )
    }

    private static ConnectionFactoriesByPriority createDiscoveredFactories(List<ConnectionFactoryEntry> entries)
    {
        def validEntries = entries.findAll { it.valid }
        def factories = ConnectionFactoriesByPriority.emptyInstance()
        if (validEntries)
        {
            factories = factories
                    .withEntries(0L, validEntries)
                    .withResolvedFactories(validEntries*.jndiName)
        }
        factories
    }

    private static Map runConcurrentChurn(
            AtomicReference<List<DomainId>> activeDomains,
            TestEnvironment environment,
            String serviceName,
            CasualBuffer payload,
            Flag<AtmiFlags> flags,
            List<List<DomainId>> topologyRotation,
            int numWorkers,
            int callsPerWorker)
    {
        def executor = Executors.newFixedThreadPool(numWorkers + 1)
        def startLatch = new CountDownLatch(1)
        def doneLatch = new CountDownLatch(numWorkers)
        def running = new AtomicBoolean(true)
        def statistics = new ChurnStatistics()

        submitTopologyChurn(executor, startLatch, running, activeDomains, topologyRotation,
                environment.validator, statistics)
        submitCallWorkers(executor, startLatch, doneLatch, numWorkers, callsPerWorker,
                environment, serviceName, payload, flags, statistics)

        boolean finishedInTime = false
        try
        {
            finishedInTime = awaitWorkers(startLatch, doneLatch)
        }
        finally
        {
            stopExecutor(executor, running, finishedInTime)
        }
        statistics.snapshot(finishedInTime)
    }

    private static void submitTopologyChurn(ExecutorService executor,
                                            CountDownLatch startLatch,
                                            AtomicBoolean running,
                                            AtomicReference<List<DomainId>> activeDomains,
                                            List<List<DomainId>> topologyRotation,
                                            ConnectionValidator validator,
                                            ChurnStatistics statistics)
    {
        executor.submit {
            try
            {
                startLatch.await()
                rotateTopologies(running, activeDomains, topologyRotation, validator, statistics)
            }
            catch (Throwable t)
            {
                statistics.errors.add(t)
            }
        }
    }

    private static void rotateTopologies(AtomicBoolean running,
                                         AtomicReference<List<DomainId>> activeDomains,
                                         List<List<DomainId>> topologyRotation,
                                         ConnectionValidator validator,
                                         ChurnStatistics statistics)
    {
        int topologyIndex = 0
        while (running.get())
        {
            activeDomains.set(topologyRotation[topologyIndex % topologyRotation.size()])
            validator.validateAllConnections()
            statistics.validationRuns.incrementAndGet()
            topologyIndex++

            // Production validation runs at most every five seconds by default. The test uses a shorter
            // interval so it exercises the same periodic behavior without extending the unit test duration.
            Thread.sleep(TEST_VALIDATION_INTERVAL_MILLIS)
        }
    }

    private static void submitCallWorkers(ExecutorService executor,
                                          CountDownLatch startLatch,
                                          CountDownLatch doneLatch,
                                          int numWorkers,
                                          int callsPerWorker,
                                          TestEnvironment environment,
                                          String serviceName,
                                          CasualBuffer payload,
                                          Flag<AtmiFlags> flags,
                                          ChurnStatistics statistics)
    {
        numWorkers.times {
            executor.submit {
                try
                {
                    runCallWorker(startLatch, callsPerWorker, environment, serviceName, payload, flags, statistics)
                }
                finally
                {
                    doneLatch.countDown()
                }
            }
        }
    }

    private static void runCallWorker(CountDownLatch startLatch,
                                      int callsPerWorker,
                                      TestEnvironment environment,
                                      String serviceName,
                                      CasualBuffer payload,
                                      Flag<AtmiFlags> flags,
                                      ChurnStatistics statistics)
    {
        try
        {
            startLatch.await()
            callsPerWorker.times {
                issueCall(environment, serviceName, payload, flags, statistics)
            }
        }
        catch (Throwable t)
        {
            statistics.errors.add(t)
        }
    }

    private static void issueCall(TestEnvironment environment,
                                  String serviceName,
                                  CasualBuffer payload,
                                  Flag<AtmiFlags> flags,
                                  ChurnStatistics statistics)
    {
        try
        {
            def result = environment.tpCaller.tpcall(serviceName, payload, flags, environment.lookupService)
            statistics.record(result.errorState)
        }
        catch (CasualResourceException | ResourceException ignored)
        {
            // A call can fail while every reverse outbound domain is unavailable or disconnecting.
            statistics.transientFailures.incrementAndGet()
        }
    }

    private static boolean awaitWorkers(CountDownLatch startLatch, CountDownLatch doneLatch)
    {
        startLatch.countDown()
        doneLatch.await(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    private static void stopExecutor(ExecutorService executor,
                                     AtomicBoolean running,
                                     boolean workersFinished)
    {
        running.set(false)
        if (workersFinished)
        {
            executor.shutdown()
        }
        else
        {
            executor.shutdownNow()
        }
        if (!executor.awaitTermination(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        {
            executor.shutdownNow()
            throw new IllegalStateException('Executor did not stop after reverse outbound churn completed')
        }
    }

    private static final class TestEnvironment
    {
        private final ConnectionValidator validator
        private final TpCallerFailover tpCaller
        private final ConnectionFactoryLookupService lookupService
        private final ConnectionFactoryEntryStore store

        private TestEnvironment(ConnectionValidator validator,
                                TpCallerFailover tpCaller,
                                ConnectionFactoryLookupService lookupService,
                                ConnectionFactoryEntryStore store)
        {
            this.validator = validator
            this.tpCaller = tpCaller
            this.lookupService = lookupService
            this.store = store
        }
    }

    private static final class ChurnStatistics
    {
        private final AtomicInteger successCalls = new AtomicInteger()
        private final AtomicInteger tpenoentCalls = new AtomicInteger()
        private final AtomicInteger transientFailures = new AtomicInteger()
        private final AtomicInteger validationRuns = new AtomicInteger()
        private final Set<Throwable> errors = ConcurrentHashMap.newKeySet()

        private void record(ErrorState errorState)
        {
            switch (errorState)
            {
                case ErrorState.OK:
                    successCalls.incrementAndGet()
                    break
                case ErrorState.TPENOENT:
                    tpenoentCalls.incrementAndGet()
                    break
                default:
                    errors.add(new IllegalStateException("Unexpected error state: $errorState"))
            }
        }

        private Map snapshot(boolean finishedInTime)
        {
            [
                    finishedInTime   : finishedInTime,
                    successCalls     : successCalls.get(),
                    tpenoentCalls    : tpenoentCalls.get(),
                    transientFailures: transientFailures.get(),
                    validationRuns    : validationRuns.get(),
                    errors           : errors
            ]
        }
    }

    private static final class TestConnectionFactoryProducer implements ConnectionFactoryProducer
    {
        private final String uniqueName
        private final CasualConnectionFactory connectionFactory

        private TestConnectionFactoryProducer(String uniqueName, CasualConnectionFactory connectionFactory)
        {
            this.uniqueName = uniqueName
            this.connectionFactory = connectionFactory
        }

        @Override
        String getUniqueName()
        {
            uniqueName
        }

        @Override
        CasualConnectionFactory getConnectionFactory()
        {
            connectionFactory
        }
    }

    private static final class TestCacheRepopulator extends CacheRepopulator
    {
        private final Cache cache
        private final String serviceName

        private TestCacheRepopulator(Cache cache, String serviceName)
        {
            this.cache = cache
            this.serviceName = serviceName
        }

        @Override
        void repopulate(ConnectionFactoryEntry entry)
        {
            if (!entry.valid)
            {
                return
            }

            ConnectionFactoriesByPriority factories = ConnectionFactoriesByPriority.emptyInstance()
            factories = factories
                    .withEntries(0L, [entry])
                    .withResolvedFactories([entry.jndiName])
            cache.store(serviceName, factories)
        }
    }
}
