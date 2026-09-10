/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnectionFactory
import se.laz.casual.jca.ConnectionObserver
import spock.lang.Specification

class ConnectionObserverHandlerTest extends Specification
{
    def 'allocation failure invalidates the entry'()
    {
        given:
        CasualConnectionFactory factory = Mock(CasualConnectionFactory)
        ConnectionFactoryEntry entry = entryFor(factory)
        ConnectionObserver observer = Mock(ConnectionObserver)
        ConnectionObserverHandler handler = ConnectionObserverHandler.of()

        when:
        handler.addObserver(entry, observer)

        then:
        1 * factory.getConnection() >> {
            throw new ResourceException('Connection unavailable')
        }
        noExceptionThrown()
        entry.isInvalid()
    }

    def 'invalid entry skips observer registration without allocating'()
    {
        given:
        CasualConnectionFactory factory = Mock(CasualConnectionFactory)
        ConnectionFactoryEntry entry = entryFor(factory)
        entry.invalidate()
        ConnectionObserverHandler handler = ConnectionObserverHandler.of()

        when:
        handler.addObserver(entry, Mock(ConnectionObserver))

        then:
        0 * factory.getConnection()
        entry.isInvalid()
    }

    private ConnectionFactoryEntry entryFor(CasualConnectionFactory factory)
    {
        ConnectionFactoryEntry.of(Stub(ConnectionFactoryProducer) {
            getUniqueName() >> 'eis/reverse[domain-a]'
            getConnectionFactory() >> factory
        })
    }
}

