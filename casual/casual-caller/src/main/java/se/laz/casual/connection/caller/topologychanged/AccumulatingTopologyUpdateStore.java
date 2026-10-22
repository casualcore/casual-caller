/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.topologychanged;

import se.laz.casual.connection.caller.CasualCallerException;
import se.laz.casual.jca.DomainId;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * Accumulate domain ids of topology updates occurring within the given delay window.
 * Making them only eligible for removal once the delay has expired from the
 * first time the domain id is put in the store.
 */
public class AccumulatingTopologyUpdateStore
{
    private final Set<DomainId> uniqueDomainIds = ConcurrentHashMap.newKeySet();
    private final DelayQueue<DelayedDomainId> domainUpdateQueue = new DelayQueue<>();

    private final long delayMillis;

    /**
     * Create a new store with the given accumulation delay.
     *
     * @param delay how long to accumulate.
     */
    public AccumulatingTopologyUpdateStore( long delay )
    {
        this.delayMillis = delay;
    }

    /**
     * Retrieve the fixed interval for delaying updates.
     *
     * @return the fixed interval in milliseconds.
     */
    public long getDelay()
    {
        return this.delayMillis;
    }

    /**
     * Put a domain into the store, if the domain already exists
     * it will not be added again.
     *
     * @param domainId to be put in the store.
     */
    public void put( DomainId domainId )
    {
        Objects.requireNonNull( domainId, "DomainId is null." );
        if( uniqueDomainIds.add( domainId ) )
        {
            domainUpdateQueue.add( new DelayedDomainId( domainId, delayMillis ) );
        }
    }

    /**
     * Retrieve the next available domain from the store.
     * Blocks if no domains are yet available.
     *
     * @return the next available domainId
     */
    public DomainId take()
    {
        try
        {
            DomainId id = domainUpdateQueue.take().getDomainId();
            uniqueDomainIds.remove( id );
            return id;
        }
        catch( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            throw new CasualCallerException( "Thread was interrupted." );
        }
    }

    /**
     * The size of unique domainIds currently stored.
     *
     * @return the current size.
     */
    public int size()
    {
        return uniqueDomainIds.size();
    }

    private class DelayedDomainId implements Delayed
    {
        private final DomainId domainId;
        private final long readyAt;

        DelayedDomainId( DomainId domainId, long delay )
        {
            this.domainId = domainId;
            this.readyAt = System.currentTimeMillis() + delay;
        }

        public DomainId getDomainId()
        {
            return domainId;
        }

        @Override
        public int compareTo( Delayed delayed )
        {
            return Long.compare( this.readyAt, ( (DelayedDomainId)delayed ).readyAt );
        }

        @Override
        public long getDelay( TimeUnit unit )
        {
            long remaining = readyAt - System.currentTimeMillis();
            return unit.convert( remaining, TimeUnit.MILLISECONDS );
        }
    }

}
