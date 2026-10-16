/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import se.laz.casual.jca.CasualConnectionFactory;
import se.laz.casual.jca.DomainId;

import java.util.Objects;

/**
 * Produces connection factories towards one specific instance connected to a reverse pool.
 * One producer exists per connected instance and its unique name is the base entry name with
 * the domain id of the instance appended.
 */
public class ReverseConnectionFactoryProducer implements ConnectionFactoryProducer
{
    private final ConnectionFactoryEntry base;
    private final DomainId domainId;

    private ReverseConnectionFactoryProducer(ConnectionFactoryEntry base, DomainId domainId)
    {
        this.base = base;
        this.domainId = domainId;
    }

    public static ReverseConnectionFactoryProducer of(ConnectionFactoryEntry base, DomainId domainId)
    {
        Objects.requireNonNull(base, "base can not be null");
        Objects.requireNonNull(domainId, "domainId can not be null");
        return new ReverseConnectionFactoryProducer(base, domainId);
    }

    public DomainId getDomainId()
    {
        return domainId;
    }

    @Override
    public String getUniqueName()
    {
        return base.getJndiName() + "[" + domainId.getId() + "]";
    }

    @Override
    public CasualConnectionFactory getConnectionFactory()
    {
        return DomainIdPinnedConnectionFactory.of(base.getConnectionFactory(), domainId);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        ReverseConnectionFactoryProducer that = (ReverseConnectionFactoryProducer) o;
        return Objects.equals(base.getJndiName(), that.base.getJndiName()) && Objects.equals(domainId, that.domainId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(base.getJndiName(), domainId);
    }

    @Override
    public String toString()
    {
        return "ReverseConnectionFactoryProducer{" +
                "uniqueName='" + getUniqueName() + '\'' +
                '}';
    }
}
