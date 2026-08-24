/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller;

import jakarta.resource.ResourceException;
import jakarta.resource.spi.ConnectionRequestInfo;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.jca.CasualConnectionFactory;
import se.laz.casual.jca.CasualRequestInfo;
import se.laz.casual.jca.DomainId;

import javax.naming.NamingException;
import javax.naming.Reference;
import java.util.Objects;

/**
 * A connection factory towards one specific instance connected to a reverse pool.
 * Connections are always requested with the domain id of that instance so that they only
 * ever go towards it.
 */
public class DomainIdPinnedConnectionFactory implements CasualConnectionFactory
{
    private static final long serialVersionUID = 1L;
    private final CasualConnectionFactory delegate;
    private final DomainId domainId;

    private DomainIdPinnedConnectionFactory(CasualConnectionFactory delegate, DomainId domainId)
    {
        this.delegate = delegate;
        this.domainId = domainId;
    }

    public static DomainIdPinnedConnectionFactory of(CasualConnectionFactory delegate, DomainId domainId)
    {
        Objects.requireNonNull(delegate, "delegate can not be null");
        Objects.requireNonNull(domainId, "domainId can not be null");
        return new DomainIdPinnedConnectionFactory(delegate, domainId);
    }

    @Override
    public CasualConnection getConnection() throws ResourceException
    {
        return delegate.getConnection(CasualRequestInfo.of(domainId));
    }

    @Override
    public CasualConnection getConnection(ConnectionRequestInfo connectionRequestInfo) throws ResourceException
    {
        return delegate.getConnection(null == connectionRequestInfo ? CasualRequestInfo.of(domainId) : connectionRequestInfo);
    }

    @Override
    public Reference getReference() throws NamingException
    {
        return delegate.getReference();
    }

    @Override
    public void setReference(Reference reference)
    {
        delegate.setReference(reference);
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
        DomainIdPinnedConnectionFactory that = (DomainIdPinnedConnectionFactory) o;
        return Objects.equals(delegate, that.delegate) && Objects.equals(domainId, that.domainId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(delegate, domainId);
    }

    @Override
    public String toString()
    {
        return "DomainIdPinnedConnectionFactory{" +
                "delegate=" + delegate +
                ", domainId=" + domainId +
                '}';
    }
}
