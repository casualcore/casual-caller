/*
 * Copyright (c) 2022, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.InvalidTransactionException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

public class TransactionManagerImpl implements TransactionManager
{
    private Transaction currentTransaction;

    public void setCurrentTransaction(Transaction currentTransaction)
    {
        this.currentTransaction = currentTransaction;
    }

    @Override
    public void begin() throws NotSupportedException, SystemException
    {

    }

    @Override
    public void commit() throws RollbackException, HeuristicMixedException, HeuristicRollbackException, SecurityException, IllegalStateException, SystemException
    {

    }

    @Override
    public int getStatus() throws SystemException
    {
        return 0;
    }

    @Override
    public Transaction getTransaction() throws SystemException
    {
        return currentTransaction;
    }

    @Override
    public void resume(Transaction tobj) throws InvalidTransactionException, IllegalStateException, SystemException
    {

    }

    @Override
    public void rollback() throws IllegalStateException, SecurityException, SystemException
    {

    }

    @Override
    public void setRollbackOnly() throws IllegalStateException, SystemException
    {

    }

    @Override
    public void setTransactionTimeout(int seconds) throws SystemException
    {

    }

    @Override
    public Transaction suspend() throws SystemException
    {
        return null;
    }
}
