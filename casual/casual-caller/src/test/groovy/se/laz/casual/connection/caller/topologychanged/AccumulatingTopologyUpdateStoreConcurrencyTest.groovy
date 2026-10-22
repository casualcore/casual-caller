/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.topologychanged

import se.laz.casual.jca.DomainId
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AccumulatingTopologyUpdateStoreConcurrencyTest extends Specification
{
    @Shared
    DomainId id1 = DomainId.of( UUID.randomUUID(  ) )
    @Shared
    DomainId id2 = DomainId.of( UUID.randomUUID(  ) )

    @Shared
    ExecutorService executorService

    AccumulatingTopologyUpdateStore instance

    long delay = 20L

    def setupSpec()
    {
        executorService = Executors.newFixedThreadPool( 4 )
    }

    def setup()
    {
        instance = new AccumulatingTopologyUpdateStore( delay )
    }

    def cleanupSpec()
    {
        executorService.shutdownNow(  )
    }

    def "Add multiple concurrent requests, groups up within the same delay window. Come out in FIFO order."()
    {
        given:
        CountDownLatch populated1 = new CountDownLatch( 1 )
        CountDownLatch populated2 = new CountDownLatch( 1 )
        AtomicBoolean stop = new AtomicBoolean( false )

        executorService.submit( ()-> {
            while( !stop.get(  ) )
            {
                instance.put( id1  )
                populated1.countDown(  )
            }
        } )
        executorService.submit( ()-> {
            populated1.await()
            while( !stop.get(  ) )
            {
                instance.put( id2  )
                populated2.countDown(  )
            }
        } )

        when:
        List<DomainId> found = new ArrayList<>()

        populated2.await( 1, TimeUnit.SECONDS )
        stop.set( true )
        found.add( instance.take(  ) )
        found.add( instance.take(  ) )

        then:
        found == [id1,id2]
        instance.size(  ) == 0
    }

    def "take blocks until it recieves first message after delay of #delayMillis."()
    {
        given:
        CountDownLatch take = new CountDownLatch( 1 )
        CountDownLatch taken = new CountDownLatch( 1 )

        instance = new AccumulatingTopologyUpdateStore( delayMillis )

        Future<DomainId> future = executorService.submit( {
                take.countDown()
                DomainId id = instance.take(  )
                taken.countDown(  )
                return id;
        } as Callable<DomainId> )

        when:
        take.await()
        long start = System.currentTimeMillis(  )
        instance.put( id1 )
        taken.await()
        long end = System.currentTimeMillis(  )
        DomainId actual = future.get(  )

        then:
        end - start >= delayMillis
        actual == id1

        where:
        delayMillis << [
                20,50,100,1000
        ]
    }

    def "Multiple domains incoming after taken are still there for next delay window."()
    {
        given:
        int delayMillis = 100
        instance = new  AccumulatingTopologyUpdateStore( delayMillis )
        CountDownLatch start = new CountDownLatch( 1 )

        when:
        Future<Integer> future = executorService.submit( {
            start.countDown(  )
            long init = System.currentTimeMillis(  )
            int count = 0
            while( (System.currentTimeMillis(  ) - init) < (5*delayMillis)  )
            {
                instance.put( id1 )
                instance.put( id2 )
                count += 2
            }
            return count
        } as Callable<Integer> )

        then:
        start.await( 1, TimeUnit.SECONDS )
        5.times{
            assert instance.take( ) == id1
            assert instance.take( ) == id2
        }
        future.get() >= 10
    }

    def "Add lots of domains from 2 threads, whilst reading from 1 thread, still only grouped and unique domains."()
    {
        given:
        int delayMillis = 100
        instance = new  AccumulatingTopologyUpdateStore( delayMillis )
        CountDownLatch start = new CountDownLatch( 1 )
        AtomicBoolean stop = new AtomicBoolean( false )
        int iterations = 5


        Set<DomainId> incoming = ConcurrentHashMap.newKeySet()
        count.times {
            incoming.add( DomainId.of( UUID.randomUUID(  ) ) )
        }

        when:
        Future<List<Set<DomainId>>> resultFuture = executorService.submit( {
            start.countDown(  )
            List<Set<DomainId>> all = new ArrayList<>()
            iterations.times {
                Set<DomainId> found = new HashSet<>()
                count.times {
                    found.add( instance.take() )
                }
                all.add( found )
            }
            return all
        } as Callable<Set<DomainId>> )

        Callable<Integer> producerTask = new Callable<Integer>(){
            @Override
            Integer call() throws Exception
            {
                long init = System.currentTimeMillis(  )
                int i = 0;
                start.await()
                while( (System.currentTimeMillis(  ) - init) < (iterations*delayMillis)  )
                {
                    for( DomainId id: incoming )
                    {
                        instance.put( id )
                    }
                    i++
                }
                return i
            }
        }

        Future<Integer> future = executorService.submit( producerTask )

        Future<Integer> future2 = executorService.submit( producerTask )

        int count1 = future.get( 1, TimeUnit.SECONDS )
        int count2 = future2.get( 1, TimeUnit.SECONDS )
        List<Set<DomainId>> actual = resultFuture.get( 1, TimeUnit.SECONDS )

        then:
        count1 >= iterations * incoming.size( )
        count2 >= iterations * incoming.size( )
        actual.size(  ) == iterations

        for( Set<DomainId> batch: actual )
        {
            assert batch == incoming
        }

        where:
        count << [
                10,20,50,100,200
        ]
    }


}
