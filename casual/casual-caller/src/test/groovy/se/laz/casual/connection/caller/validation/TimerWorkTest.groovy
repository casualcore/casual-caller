/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.validation

import se.laz.casual.connection.caller.ConnectionValidator
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TimerWorkTest extends Specification
{
    def 'overlapping call does not get executed'()
    {
        given:
        AtomicInteger counter = new AtomicInteger(0)
        CompletableFuture<Boolean> barrier = new CompletableFuture()
        CompletableFuture<Boolean> testWaitCondition = new CompletableFuture()
        ConnectionValidator validator = Mock(ConnectionValidator){
            2 * validateAllConnections() >> {
                if(counter.compareAndSet(0,1)) {
                    // only on first call
                    barrier.join()
                } else if(counter.compareAndSet(1,2)){
                    testWaitCondition.complete(true)
                }
            }
        }
        ExecutorService executorService = Executors.newCachedThreadPool()
        TimerWork work = new TimerWork(validator)
        when:
        // hits the barrier - ie works takes a long time
        executorService.submit ({work.work()})
        // next call would otherwise overlap
        // since the previous call is stuck on the barrier
        // instead it just returns and validateAllConnections is not called
        executorService.submit ({work.work()})
        then:
        noExceptionThrown()
        when:
        // makes the first work complete
        barrier.complete(true)
        // the next call also fine - also releases the wait condition
        // unfortunately, we do need the sleep here since we need the method to exit before
        // we submit the next unit of work, else that would also just return as it still be busy
        sleep(500)
        executorService.submit ({work.work()})
        testWaitCondition.join()
        then:
        noExceptionThrown()
    }
}
