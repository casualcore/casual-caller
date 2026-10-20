/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class FailedDomainDiscoveryHandlerConcurrencyTest extends Specification
{
   private static final int CALLER_COUNT = 16
   private static final long TIMEOUT_SECONDS = 5

   def 'concurrent callers that observe one pending request perform one discovery'()
   {
      given:
      AtomicBoolean pendingDiscovery = new AtomicBoolean(true)
      ConnectionFactoryEntry entry = Mock(ConnectionFactoryEntry)
      Map<CacheType, List<String>> cachedItems = new EnumMap<>(CacheType.class)
      cachedItems.put(CacheType.SERVICE, [])
      cachedItems.put(CacheType.QUEUE, [])
      Cache cache = Mock(Cache)
      TransactionLess transactionLess = Mock(TransactionLess)
      CacheRepopulator repopulator = new CacheRepopulator(cache, transactionLess)
      ConnectionFactoryEntryStore store = Mock(ConnectionFactoryEntryStore)
      FailedDomainDiscoveryHandler handler = new FailedDomainDiscoveryHandler(repopulator, store)
      ExecutorService executor = Executors.newFixedThreadPool(CALLER_COUNT)
      CyclicBarrier start = new CyclicBarrier(CALLER_COUNT + 1)
      List<Future<Void>> calls = []

      when:
      CALLER_COUNT.times {
         calls.add(executor.submit({
            start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            handler.issueDomainDiscoveryAndRepopulateCache()
            null
         } as Callable<Void>))
      }
      start.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      calls.each { it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS) }

      then:
      CALLER_COUNT * store.get() >> [entry]
      // Every caller crosses the fast check before the claim at the synchronized discovery boundary.
      CALLER_COUNT * entry.isDomainDiscoveryRequired() >> true
      CALLER_COUNT * entry.claimDomainDiscovery() >> { pendingDiscovery.getAndSet(false) }
      1 * cache.getAll() >> cachedItems
      0 * cache.purge(_)
      1 * transactionLess.discover(entry, cachedItems) >> Optional.empty()
      0 * cache.repopulate(_, _)

      cleanup:
      executor.shutdownNow()
      assert executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)
   }
}
