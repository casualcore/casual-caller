/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.conversation;

import jakarta.resource.ResourceException;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import se.laz.casual.api.Conversation;
import se.laz.casual.api.conversation.TpConnectReturn;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.connection.caller.CasualCallerException;
import se.laz.casual.connection.caller.CasualResourceException;
import se.laz.casual.connection.caller.ConnectionFactoryEntry;
import se.laz.casual.connection.caller.functions.FunctionThrowsResourceException;
import se.laz.casual.jca.CasualConnection;


import java.util.List;
import java.util.function.BooleanSupplier;

public class ConversationFailover
{
    private ConversationFailover()
    {}
    public static TpConnectReturn tpconnectWithFailover(String serviceName,
                                                        List<ConnectionFactoryEntry> validEntries,
                                                        FunctionThrowsResourceException<TpConnectReturn, CasualConnection> doCall)
    {
        return tpconnectWithFailover(serviceName, validEntries, doCall, ConversationFailover::transactionAllowsRetry);
    }

    public static TpConnectReturn tpconnectWithFailover(String serviceName,
                                                        List<ConnectionFactoryEntry> validEntries,
                                                        FunctionThrowsResourceException<TpConnectReturn, CasualConnection> doCall,
                                                        BooleanSupplier retryAllowed)
    {
        FunctionThrowsResourceException<TpConnectReturn, CasualConnection> tpConnectWrapsConnection = connection -> {
            TpConnectReturn tpConnectReturn = doCall.apply(connection);
            if(tpConnectReturn.getErrorState() == ErrorState.OK)
            {
                Conversation conversation = ConversationImpl.of(connection, tpConnectReturn.getConversation().orElseThrow(() -> new CasualCallerException("tpconnect, ErrorState.OK but missing conversation!")));
                return TpConnectReturn.of(conversation);
            }
            return tpConnectReturn;
        };
        return issueCall(serviceName, validEntries, tpConnectWrapsConnection, retryAllowed);
    }

    private static TpConnectReturn issueCall(String serviceName,
                                   List<ConnectionFactoryEntry> validEntries,
                                   FunctionThrowsResourceException<TpConnectReturn, CasualConnection> wrapperFunction, BooleanSupplier retryAllowed)
    {
        Exception thrownException = null;
        for (ConnectionFactoryEntry connectionFactoryEntry : validEntries)
        {
            final CasualConnection connection;
            try
            {
                connection = connectionFactoryEntry.getConnectionFactory().getConnection();
            }
            catch (ResourceException e)
            {
                connectionFactoryEntry.invalidate();
                if (!retryAllowed.getAsBoolean())
                {
                    throw new CasualResourceException("Connection acquisition failed; transaction does not permit retry.", e);
                }
                thrownException = e;
                continue;
            }
            boolean closeAttempted = false;
            try
            {
                // A successful conversation owns the connection until the application closes it.
                final TpConnectReturn result = wrapperFunction.apply(connection);
                if (result.getErrorState() != ErrorState.OK)
                {
                    closeAttempted = true;
                    connection.close();
                }
                return result;
            }
            catch (Exception e)
            {
                try
                {
                    if (!closeAttempted)
                    {
                        connection.close();
                    }
                }
                catch (Exception closeFailure)
                {
                    if (closeFailure != e)
                    {
                        e.addSuppressed(closeFailure);
                    }
                }
                connectionFactoryEntry.invalidate();
                throw new CasualResourceException("Conversation invocation failed for service=" + serviceName
                        + " on connection=" + connectionFactoryEntry.getJndiName() + "; no retry is attempted.", e);
            }
        }
        throw new CasualResourceException("Call failed to all " + validEntries.size() + " available casual connections.", thrownException);
    }

    private static boolean transactionAllowsRetry()
    {
        try
        {
            final int status = CDI.current().select(TransactionManager.class).get().getStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_NO_TRANSACTION;
        }
        catch (SystemException e)
        {
            throw new CasualResourceException("Cannot determine transaction status; no retry is attempted.", e);
        }
    }
}
