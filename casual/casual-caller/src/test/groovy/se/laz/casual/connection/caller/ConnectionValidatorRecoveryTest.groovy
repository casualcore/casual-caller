/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller

import se.laz.casual.jca.CasualConnection
import se.laz.casual.jca.CasualConnectionFactory
import spock.lang.Specification

class ConnectionValidatorRecoveryTest extends Specification
{
    def 'failed discovery does not block another domain and retries next tick'()
    {
        given:
        CasualConnectionFactory failingFactory = Mock(CasualConnectionFactory)
        CasualConnectionFactory healthyFactory = Mock(CasualConnectionFactory)
        CasualConnection recoveredConnection = Mock(CasualConnection)
        CasualConnection healthyConnection = Mock(CasualConnection)

        ConnectionFactoryEntry failingEntry =
                entryFor('domain-a', failingFactory)
        ConnectionFactoryEntry healthyEntry =
                entryFor('domain-b', healthyFactory)

        ConnectionFactoryEntryStore store = Mock(ConnectionFactoryEntryStore)
        CacheRepopulator repopulator = Mock(CacheRepopulator)
        ConnectionValidator validator =
                new ConnectionValidator(repopulator, store, Mock(Cache))

        store.get() >> [failingEntry, healthyEntry]
        failingFactory.isDomainDisconnecting() >> false
        healthyFactory.isDomainDisconnecting() >> false
        healthyFactory.getConnection() >> healthyConnection

        when: 'discovery fails and the domain remains unavailable this tick'
        validator.validateAllConnections()

        then:
        1 * store.refreshReverseEntries() >>
                new ReverseRefreshResult([failingEntry, healthyEntry], [])
        1 * repopulator.repopulate(failingEntry) >> {
            throw new IllegalStateException('Discovery failed')
        }
        1 * failingFactory.getConnection() >> {
            throw new ResourceException('Domain unavailable')
        }
        0 * store.addConnectionObserver(failingEntry)

        1 * repopulator.repopulate(healthyEntry)
        1 * store.addConnectionObserver(healthyEntry)

        noExceptionThrown()
        failingEntry.isInvalid()
        healthyEntry.isValid()

        when: 'the domain becomes available on the next tick'
        validator.validateAllConnections()

        then:
        1 * store.refreshReverseEntries() >>
                new ReverseRefreshResult([], [])
        1 * failingFactory.getConnection() >> recoveredConnection
        1 * recoveredConnection.close()
        1 * repopulator.repopulate(failingEntry)
        1 * store.addConnectionObserver(failingEntry)

        0 * repopulator.repopulate(healthyEntry)
        0 * store.addConnectionObserver(healthyEntry)

        noExceptionThrown()
        failingEntry.isValid()
        healthyEntry.isValid()
    }

    private ConnectionFactoryEntry entryFor(
            String name, CasualConnectionFactory factory)
    {
        ConnectionFactoryEntry.of(Mock(ConnectionFactoryProducer) {
            getUniqueName() >> "eis/reverse[$name]"
            getConnectionFactory() >> factory
        })
    }


}
