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

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Test reverse inbound domains coming and going under load
 */
class ReverseOutboundChurnConcurrencyTest extends Specification
{
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
                [domainA, domainB],
                [domainB],
                [domainB, domainC],
                [domainC],
                [],
                [domainA],
                [domainA, domainC]
        ]

        def numWorkers = 8
        def callsPerWorker = 500

        when:
        def result = runConcurrentChurn(
                activeDomains,
                environment.validator,
                environment.tpCaller,
                environment.lookupService,
                serviceName,
                payload,
                flags,
                topologyRotation,
                numWorkers,
                callsPerWorker
        )

        println("successful calls: ${result.successCalls}")
        println("tpenoentcalls: ${result.tpenoentCalls}")
        println("transientFailers: ${result.transientFailures}")

        then:
        result.finishedInTime
        result.errors.empty
        result.successCalls > 0
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


    // helpers
    def createEnvironment(AtomicReference<List<DomainId>> activeDomains, String serviceName, ServiceReturn<CasualBuffer> okReturn)
    {
        def baseJndi = 'java:/eis/casualReverse'

        def baseConnectionFactory = Mock(CasualConnectionFactory)
        def baseProducer = Mock(ConnectionFactoryProducer)

        baseProducer.getConnectionFactory() >> baseConnectionFactory
        baseProducer.getUniqueName() >> baseJndi

        def baseEntry = ConnectionFactoryEntry.of(baseProducer)

        baseConnectionFactory.getConnection() >> {
            def current = activeDomains.get()

            if (!current)
            {
                throw new ResourceException('No reverse inbound connections available')
            }

            def connection = Mock(CasualConnection)
            connection.isReversePool() >> true
            connection.getPoolDomainIds() >> new ArrayList<>(current)

            connection
        }

        baseConnectionFactory.getConnection(_ as CasualRequestInfo) >> {
            CasualRequestInfo request ->

                def requestedDomain = request.domainId.orElseThrow {new DomainDisconnectedException('DomainId expected')}

                if (!activeDomains.get().contains(requestedDomain))
                {
                    throw new DomainDisconnectedException("Domain $requestedDomain is disconnected")
                }

                def connection = Mock(CasualConnection)

                connection.tpcall(serviceName, _, _, _) >> {
                    if (!activeDomains.get().contains(requestedDomain))
                    {
                        throw new DomainDisconnectedException("Domain $requestedDomain dropped mid-call")
                    }
                    okReturn
                }
                connection
        }

        def finder = Mock(ConnectionFactoryFinder)
        finder.findConnectionFactory(_) >> [baseEntry]

        def topologyChangedHandler = Mock(TopologyChangedHandler)

        def store = new ConnectionFactoryEntryStore(
                finder,
                topologyChangedHandler
        )
        store.initialize()

        def cache = new Cache()

        def repopulator = Mock(CacheRepopulator)

        repopulator.repopulate(_ as ConnectionFactoryEntry) >> {
            ConnectionFactoryEntry entry ->

                if (!entry.valid)
                    return

                def factories = ConnectionFactoriesByPriority.emptyInstance()
                factories.store(0L, [entry])
                factories.addResolvedFactories([entry.jndiName])

                cache.store(serviceName, factories)
        }

        def validator = new ConnectionValidator(
                repopulator,
                store,
                cache
        )

        def transactionManager = Mock(TransactionManager)

        def failoverAlgorithm = new FailoverAlgorithm()
        failoverAlgorithm.setTransactionManager(transactionManager)

        def tpCaller = new TpCallerFailover(failoverAlgorithm)

        def transactionLess = new TransactionLess()
        def lookup = Mock(Lookup)

        lookup.find(serviceName, _, _) >> {
            String svc, List<ConnectionFactoryEntry> entries, TransactionLess tl ->

                def valid = entries.findAll { it.valid }
                def factories = ConnectionFactoriesByPriority.emptyInstance()

                if (valid)
                {
                    factories.store(0L, valid)
                    factories.addResolvedFactories(
                            valid*.jndiName
                    )
                }
                factories
        }

        def lookupService = new ConnectionFactoryLookupService(
                store,
                cache,
                lookup,
                transactionLess
        )

        [
                validator    : validator,
                tpCaller     : tpCaller,
                lookupService: lookupService,
                store        : store
        ]
    }

    private static Map runConcurrentChurn(
            AtomicReference<List<DomainId>> activeDomains,
            ConnectionValidator validator,
            TpCallerFailover tpCaller,
            ConnectionFactoryLookupService lookupService,
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
        def successCalls = new AtomicInteger()
        def tpenoentCalls = new AtomicInteger()
        def transientFailures = new AtomicInteger()
        def errors = new ConcurrentLinkedQueue<Throwable>()

        executor.submit {
            try
            {
                startLatch.await()

                while (running.get())
                {
                    topologyRotation.each { domains ->
                        if (!running.get())
                            return

                        activeDomains.set(domains)
                        validator.validateAllConnections()
                    }
                }
            }
            catch (Throwable t)
            {
                errors.add(t)
            }
        }

        numWorkers.times
                {
                    executor.submit {
                        try
                        {
                            startLatch.await()

                            callsPerWorker.times
                                    {
                                        try
                                        {
                                            def result = tpCaller.tpcall(
                                                    serviceName,
                                                    payload,
                                                    flags,
                                                    lookupService
                                            )

                                            switch (result.errorState)
                                            {
                                                case ErrorState.OK:
                                                    successCalls.incrementAndGet()
                                                    break

                                                case ErrorState.TPENOENT:
                                                    tpenoentCalls.incrementAndGet()
                                                    break

                                                default:
                                                    errors.add(
                                                            new IllegalStateException(
                                                                    "Unexpected error state: " +
                                                                            result.errorState
                                                            )
                                                    )
                                            }
                                        }
                                        catch (CasualResourceException e)
                                        {
                                            /*
                                             * Expected when all candidate backends are
                                             * disconnected while a call is in progress.
                                             */
                                            transientFailures.incrementAndGet()
                                        }
                                        catch (ResourceException e)
                                        {
                                            /*
                                             * ResourceException is also part of the expected
                                             * failure mode for the reverse connection pool.
                                             */
                                            transientFailures.incrementAndGet()
                                        }
                                    }
                        }
                        catch (Throwable t)
                        {
                            errors.add(t)
                        }
                        finally
                        {
                            doneLatch.countDown()
                        }
                    }
                }

        boolean finishedInTime

        try
        {
            startLatch.countDown()
            finishedInTime = doneLatch.await(30, TimeUnit.SECONDS)
        }
        finally
        {
            running.set(false)
            executor.shutdown()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }

        [
                finishedInTime   : finishedInTime,
                successCalls     : successCalls.get(),
                tpenoentCalls    : tpenoentCalls.get(),
                transientFailures: transientFailures.get(),
                errors           : errors
        ]
    }
}

