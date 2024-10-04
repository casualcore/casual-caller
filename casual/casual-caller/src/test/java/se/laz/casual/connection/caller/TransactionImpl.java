/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import javax.transaction.xa.XAResource;

public class TransactionImpl implements Transaction
{
    private int status;

    public TransactionImpl(int status)
    {
        this.status = status;
    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException
    {
        status = Status.STATUS_COMMITTED;
    }

    @Override
    public boolean delistResource(XAResource xaRes, int flag) throws IllegalStateException, SystemException
    {
        return true;
    }

    @Override
    public boolean enlistResource(XAResource xaRes) throws RollbackException, IllegalStateException, SystemException
    {
        return true;
    }

    @Override
    public int getStatus() throws SystemException
    {
        return status;
    }

    @Override
    public void registerSynchronization(Synchronization sync) throws RollbackException, IllegalStateException, SystemException
    {
        throw new UnsupportedOperationException("Synchronization not supported");
    }

    @Override
    public void rollback() throws IllegalStateException, SystemException
    {
        status = Status.STATUS_ROLLEDBACK;
    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException
    {
        status = Status.STATUS_MARKED_ROLLBACK;
    }
}
