/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.topologychanged

import se.laz.casual.jca.DomainId
import spock.lang.Shared
import spock.lang.Specification

class AccumulatingTopologyUpdateStoreTest extends Specification
{
    @Shared
    DomainId id1 = DomainId.of( UUID.randomUUID(  ) )
    @Shared
    DomainId id2 = DomainId.of( UUID.randomUUID(  ) )

    AccumulatingTopologyUpdateStore instance

    long delay = 2L

    def setup()
    {
        instance = new AccumulatingTopologyUpdateStore(delay)
    }

    def "Initial store has size 0"()
    {
        expect:
        instance.size() == 0
        instance.getDelay(  ) == delay
    }


    def "Add domain null, throws NullPointerException"()
    {
        when:
        instance.put( null )

        then:
        thrown NullPointerException
    }

    def "Add domain, remove domain."()
    {
        when:
        instance.put( id1 )

        then:
        instance.size() == 1

        when:
        DomainId actual = instance.take()


        then:
        actual == id1
        instance.size() == 0
    }

    def "Add domain twice, only removes 1."()
    {
        when:
        instance.put( id1 )
        instance.put( id1 )

        then:
        instance.size() == 1
    }

    def "Add multiple domains, only has unique."()
    {
        when:
        instance.put( id1 )
        instance.put( id2 )

        then:
        instance.size() == 2
    }

    def "Add multiple, remove multiple, only unique, preserve order FIFO"()
    {
        when:
        instance.put( id1 )
        instance.put( id2 )
        instance.put( id1 )
        instance.put( id2 )
        instance.put( id1 )
        instance.put( id2 )

        then:
        instance.size() == 2

        when:
        DomainId first = instance.take()
        DomainId second = instance.take()

        then:
        first == id1
        second == id2
        instance.size() == 0
    }
}
