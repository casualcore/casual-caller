/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import spock.lang.Specification

class ConnectionFactoryEntryTest extends Specification
{
    def 'known shutdown invalidates without allocating a connection'()
    {
        given:
        CasualConnectionFactory factory = Mock(CasualConnectionFactory)
        ConnectionFactoryEntry entry = entryFor(factory)

        when:
        entry.validate()

        then:
        1 * factory.isDomainDisconnecting() >> true
        0 * factory.getConnection()
        entry.isInvalid()
    }

    def 'shutdown during allocation prevents revalidation'()
    {
        given:
        CasualConnectionFactory factory = Mock(CasualConnectionFactory)
        CasualConnection connection = Mock(CasualConnection)
        ConnectionFactoryEntry entry = entryFor(factory)
        entry.invalidate()

        when:
        entry.validate()

        then:
        2 * factory.isDomainDisconnecting() >>> [false, true]
        1 * factory.getConnection() >> connection
        1 * connection.close()
        entry.isInvalid()
    }

    def 'successful validation restores an invalid entry'()
    {
        given:
        CasualConnectionFactory factory = Mock(CasualConnectionFactory)
        CasualConnection connection = Mock(CasualConnection)
        ConnectionFactoryEntry entry = entryFor(factory)
        entry.invalidate()

        when:
        entry.validate()

        then:
        2 * factory.isDomainDisconnecting() >> false
        1 * factory.getConnection() >> connection
        1 * connection.close()
        entry.isValid()
    }

    private ConnectionFactoryEntry entryFor(CasualConnectionFactory factory)
    {
        ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
            getUniqueName() >> 'eis/test'
            getConnectionFactory() >> factory
        })
    }
}

