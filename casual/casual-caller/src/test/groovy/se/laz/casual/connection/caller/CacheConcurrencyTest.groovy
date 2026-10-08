/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnectionFactory
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CacheConcurrencyTest extends Specification
{
   private static final long TIMEOUT_SECONDS = 5
   private static final long PRIORITY = 0L

   def 'concurrent service store survives purge of the previously cached factory'()
   {
      given:
      String serviceName = 'concurrent.service'
      ConnectionFactoryEntry existingEntry = createEntry('existing')
      BlockingHashProducer newProducer = new BlockingHashProducer('new')
      ConnectionFactoryEntry newEntry = ConnectionFactoryEntry.of(newProducer)
      ConnectionFactoriesByPriority newEntries = entriesFor(newEntry)
      ServiceCache cache = new ServiceCache()
      cache.store(serviceName, entriesFor(existingEntry))
      ExecutorService executor = Executors.newFixedThreadPool(2)
      CountDownLatch removalStarted = new CountDownLatch(1)
      newProducer.pauseNextHashCode()

      when:
      // Pause the store while it merges the new entry into the cached immutable value.
      def storeFuture = executor.submit {
         cache.store(serviceName, newEntries)
      }
      assert newProducer.awaitPausedHashCode()
      // Start the purge while the store owns the service key. The purge must wait for the merged value.
      def removeFuture = executor.submit {
         removalStarted.countDown()
         cache.remove(existingEntry)
      }
      assert removalStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      assert !removeFuture.isDone()
      // Let the store publish its value. The purge then removes the old entry from that new value.
      newProducer.resumePausedHashCode()
      storeFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      removeFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

      then:
      cache.getOrEmpty(serviceName).randomizeWithPriority() == [newEntry]

      cleanup:
      newProducer.resumePausedHashCode()
      executor.shutdownNow()
      assert executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)
   }

   def 'concurrent service stores preserve both updates'()
   {
      given:
      String serviceName = 'concurrent.service'
      ConnectionFactoryEntry existingEntry = createEntry('existing')
      BlockingHashProducer firstProducer = new BlockingHashProducer('first')
      ConnectionFactoryEntry firstEntry = ConnectionFactoryEntry.of(firstProducer)
      ConnectionFactoriesByPriority firstEntries = entriesFor(firstEntry)
      ConnectionFactoryEntry secondEntry = createEntry('second')
      ServiceCache cache = new ServiceCache()
      cache.store(serviceName, entriesFor(existingEntry))
      ExecutorService executor = Executors.newFixedThreadPool(2)
      CountDownLatch secondStoreStarted = new CountDownLatch(1)
      firstProducer.pauseNextHashCode()

      when:
      // Pause the first store while it merges its entry into the cached immutable value.
      def firstStore = executor.submit {
         cache.store(serviceName, firstEntries)
      }
      assert firstProducer.awaitPausedHashCode()
      // Start another store for the same service key. It must wait for the first merged value.
      def secondStore = executor.submit {
         secondStoreStarted.countDown()
         cache.store(serviceName, entriesFor(secondEntry))
      }
      assert secondStoreStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      assert !secondStore.isDone()
      // Let both stores finish in sequence so the second merge includes the first store's entry.
      firstProducer.resumePausedHashCode()
      firstStore.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      secondStore.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)

      then:
      cache.getOrEmpty(serviceName).randomizeWithPriority().toSet() ==
              [existingEntry, firstEntry, secondEntry] as Set

      cleanup:
      firstProducer.resumePausedHashCode()
      executor.shutdownNow()
      assert executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)
   }

   private static ConnectionFactoryEntry createEntry(String uniqueName)
   {
      ConnectionFactoryEntry.of(ConnectionFactoryProducerImpl.of(uniqueName))
   }

   private static ConnectionFactoriesByPriority entriesFor(ConnectionFactoryEntry entry)
   {
      ConnectionFactoriesByPriority entries = ConnectionFactoriesByPriority.emptyInstance()
      entries.withEntries(PRIORITY, [entry])
   }

   /**
    * Pauses an immutable cache-value merge while {@code ConcurrentHashMap} owns the service key update.
    *
    * <p>The merge copies entries into a set and therefore invokes {@link #hashCode()}. Pausing that invocation lets
    * a test start a competing cache operation at a deterministic point.
    */
   private static final class BlockingHashProducer implements ConnectionFactoryProducer
   {
      private final String uniqueName
      private final CountDownLatch hashCodePaused = new CountDownLatch(1)
      private final CountDownLatch hashCodeMayResume = new CountDownLatch(1)
      private volatile boolean hashCodeShouldPause

      private BlockingHashProducer(String uniqueName)
      {
         this.uniqueName = uniqueName
      }

      void pauseNextHashCode()
      {
         hashCodeShouldPause = true
      }

      boolean awaitPausedHashCode()
      {
         hashCodePaused.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      }

      void resumePausedHashCode()
      {
         hashCodeShouldPause = false
         hashCodeMayResume.countDown()
      }

      @Override
      String getUniqueName()
      {
         uniqueName
      }

      @Override
      CasualConnectionFactory getConnectionFactory()
      {
         throw new AssertionError('The test must not request a connection factory')
      }

      @Override
      boolean equals(Object other)
      {
         if (this.is(other))
         {
            return true
         }
         if (!(other instanceof BlockingHashProducer))
         {
            return false
         }
         BlockingHashProducer that = (BlockingHashProducer) other
         uniqueName == that.uniqueName
      }

      @Override
      int hashCode()
      {
         if (hashCodeShouldPause)
         {
            hashCodePaused.countDown()
            try
            {
               if (!hashCodeMayResume.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
               {
                  throw new AssertionError('Timed out while waiting to resume hashCode')
               }
            }
            catch (InterruptedException e)
            {
               Thread.currentThread().interrupt()
               throw new AssertionError('Interrupted while waiting to resume hashCode', e)
            }
         }
         uniqueName.hashCode()
      }
   }
}
