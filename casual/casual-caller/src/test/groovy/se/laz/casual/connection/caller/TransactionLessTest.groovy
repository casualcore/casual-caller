/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnectionFactory
import spock.lang.Specification
import spock.lang.Unroll

class TransactionLessTest extends Specification
{
    @Unroll
    def '#operation skips a disconnecting domain without allocating'()
    {
        given:
        CasualConnectionFactory factory = Mock(CasualConnectionFactory)
        ConnectionFactoryEntry entry =
                ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
                    getUniqueName() >> 'eis/disconnecting'
                    getConnectionFactory() >> factory
                })
        TransactionLess transactionLess = new TransactionLess()

        when:
        def result = invocation(transactionLess, entry)

        then:
        1 * factory.isDomainDisconnecting() >> true
        0 * factory.getConnection()
        entry.isInvalid()
        result == expected

        where:
        operation        | invocation | expected
        'service lookup' | { TransactionLess tl, ConnectionFactoryEntry item ->
            tl.serviceDetails(item, { connection ->
                throw new AssertionError('Service lookup must not run')
            })
        } | []
        'queue lookup' | { TransactionLess tl, ConnectionFactoryEntry item ->
            tl.queueExists(item, { connection ->
                throw new AssertionError('Queue lookup must not run')
            })
        } | false
        'domain discovery' | { TransactionLess tl, ConnectionFactoryEntry item ->
            tl.discover(item, [
                    (CacheType.SERVICE): ['service1'],
                    (CacheType.QUEUE): ['queue1']
            ])
        } | Optional.empty()
    }
}
