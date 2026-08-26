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
        DomainId domainA = DomainId.of(UUID.randomUUID())
        DomainId domainB = DomainId.of(UUID.randomUUID())
        DomainId domainC = DomainId.of(UUID.randomUUID())

        AtomicReference<List<DomainId>> activeDomains = new AtomicReference<>([domainA, domainB])
        String baseJndi = "java:/eis/casualReverse"
        String serviceName = "counterService"

        CasualBuffer payload = OctetBuffer.of([1, 2, 3] as byte[])
        Flag<AtmiFlags> flags = Flag.of(AtmiFlags.NOFLAG)
        ServiceReturn<CasualBuffer> okReturn = new ServiceReturn<>(payload, ServiceReturnState.TPSUCCESS, ErrorState.OK, 0)

        CasualConnectionFactory baseConnectionFactory = Mock(CasualConnectionFactory)
        ConnectionFactoryProducer baseProducer = Mock(ConnectionFactoryProducer)
        baseProducer.getConnectionFactory() >> baseConnectionFactory
        baseProducer.getUniqueName() >> baseJndi
        ConnectionFactoryEntry baseEntry = ConnectionFactoryEntry.of(baseProducer)

        baseConnectionFactory.getConnection() >> {
            List<DomainId> current = Optional.of(activeDomains.get()).orElseThrow(() -> new ResourceException("No reverse inbound connections available"))
            CasualConnection baseConn = Mock(CasualConnection)
            baseConn.isReversePool() >> true
            baseConn.getPoolDomainIds() >> new ArrayList<>(current)
            return baseConn
        }

        baseConnectionFactory.getConnection(_ as CasualRequestInfo) >> { CasualRequestInfo cri ->
            DomainId requestedDomain = cri.getDomainId().orElseThrow(() -> new DomainDisconnectedException("DomainId expected"))
            List<DomainId> current = activeDomains.get()
            if (!current.contains(requestedDomain))
            {
                throw new DomainDisconnectedException("Domain " + requestedDomain + " is disconnected")
            }
            CasualConnection conn = Mock(CasualConnection)
            conn.tpcall(serviceName, _, _, _) >> {
                if (!activeDomains.get().contains(requestedDomain))
                {
                    throw new DomainDisconnectedException("Domain " + requestedDomain + " dropped mid-call")
                }
                return okReturn
            }
            return conn
        }

        ConnectionFactoryFinder finder = Mock(ConnectionFactoryFinder)
        finder.findConnectionFactory(_) >> [baseEntry]

        TopologyChangedHandler topologyChangedHandler = Mock(TopologyChangedHandler)
        ConnectionFactoryEntryStore store = new ConnectionFactoryEntryStore(finder, topologyChangedHandler)
        store.initialize()

        Cache cache = new Cache()

        CacheRepopulator repopulator = Mock(CacheRepopulator)
        repopulator.repopulate(_ as ConnectionFactoryEntry) >> { ConnectionFactoryEntry entry ->
            if (entry.isValid())
            {
                ConnectionFactoriesByPriority p = ConnectionFactoriesByPriority.emptyInstance()
                p.store(0L, [entry])
                p.addResolvedFactories([entry.getJndiName()])
                cache.store(serviceName, p)
            }
        }

        ConnectionValidator validator = new ConnectionValidator(repopulator, store, cache)
        TransactionManager transactionManager = Mock(TransactionManager)
        FailoverAlgorithm failoverAlgorithm = new FailoverAlgorithm()
        failoverAlgorithm.setTransactionManager(transactionManager)
        TpCallerFailover tpCaller = new TpCallerFailover(failoverAlgorithm)

        TransactionLess transactionLess = new TransactionLess()
        Lookup lookup = Mock(Lookup)
        lookup.find(serviceName, _, _) >> { String svc, List<ConnectionFactoryEntry> entries, TransactionLess tl ->
            List<ConnectionFactoryEntry> valid = entries.findAll { it.isValid() }
            ConnectionFactoriesByPriority p = ConnectionFactoriesByPriority.emptyInstance()
            if (!valid.isEmpty())
            {
                p.store(0L, valid)
                p.addResolvedFactories(valid.collect { it.getJndiName() })
            }
            return p
        }

        ConnectionFactoryLookupService lookupService = new ConnectionFactoryLookupService(store, cache, lookup, transactionLess)

        int numWorkers = 8
        int callsPerWorker = 500
        def executor = Executors.newFixedThreadPool(numWorkers + 1)
        CountDownLatch startLatch = new CountDownLatch(1)
        CountDownLatch doneLatch = new CountDownLatch(numWorkers)

        AtomicBoolean running = new AtomicBoolean(true)
        AtomicInteger successCalls = new AtomicInteger(0)
        AtomicInteger tpenoentCalls = new AtomicInteger(0)
        AtomicInteger transientFailures = new AtomicInteger(0)
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>()

        executor.submit({
            startLatch.await()
            List<List<DomainId>> rotation = [
                    [domainA, domainB],
                    [domainB],
                    [domainB, domainC],
                    [domainC],
                    [],                  // total outage window - all domains gone
                    [domainA],
                    [domainA, domainC]
            ]
            int index = 0
            while (running.get())
            {
                activeDomains.set(rotation[index])
                validator.validateAllConnections()
                index = (index + 1) % rotation.size()
            }
        } as Runnable)

        for (int i = 0; i < numWorkers; i++)
        {
            executor.submit({
                try
                {
                    startLatch.await()
                    for (int j = 0; j < callsPerWorker; j++)
                    {
                        try
                        {
                            ServiceReturn<CasualBuffer> result = tpCaller.tpcall(
                                    serviceName,
                                    payload,
                                    flags,
                                    lookupService
                            )
                            if (result.getErrorState() == ErrorState.OK)
                            {
                                successCalls.incrementAndGet()
                            }
                            else if (result.getErrorState() == ErrorState.TPENOENT)
                            {
                                tpenoentCalls.incrementAndGet()
                            }
                            else
                            {
                                errors.add(new IllegalStateException("Unexpected error state: " + result.getErrorState()))
                            }
                        }
                        catch (CasualResourceException | ResourceException e)
                        {
                            // expected transient failure when all attempted candidate backends severed mid-call
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
            } as Runnable)
        }

        when:
        startLatch.countDown()
        boolean finishedInTime = doneLatch.await(30, TimeUnit.SECONDS)
        running.set(false)
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        println("successful calls: ${successCalls.get()}")
        println("tpenoentcalls: ${tpenoentCalls.get()}")
        println("transientFailers: ${transientFailures.get()}")

        then:
        finishedInTime
        errors.isEmpty()
        successCalls.get() > 0
        (successCalls.get() + tpenoentCalls.get() + transientFailures.get()) == (numWorkers * callsPerWorker)

        when: 'churn stops and a single domain is stabilized'
        activeDomains.set([domainA])
        validator.validateAllConnections()

        ServiceReturn<CasualBuffer> finalResult = tpCaller.tpcall(
                serviceName,
                payload,
                flags,
                lookupService
        )

        then: 'the final call succeeds and dead domain entries are purged'
        finalResult.getErrorState() == ErrorState.OK
        List<ConnectionFactoryEntry> finalEntries = store.get()
        finalEntries.size() == 1
        finalEntries.get(0).getJndiName().contains(domainA.getId().toString())
        !finalEntries.any { it.getJndiName().contains(domainB.getId().toString()) }
        !finalEntries.any { it.getJndiName().contains(domainC.getId().toString()) }
    }
}
