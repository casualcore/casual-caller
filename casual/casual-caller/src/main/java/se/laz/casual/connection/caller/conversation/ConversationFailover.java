/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.connection.caller.conversation;

import jakarta.resource.ResourceException;
import se.laz.casual.api.Conversation;
import se.laz.casual.api.conversation.TpConnectReturn;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.connection.caller.CasualCallerException;
import se.laz.casual.connection.caller.CasualResourceException;
import se.laz.casual.connection.caller.ConnectionFactoryEntry;
import se.laz.casual.connection.caller.functions.FunctionThrowsResourceException;
import se.laz.casual.jca.CasualConnection;
import se.laz.casual.network.connection.CasualConnectionException;

import java.util.List;
import java.util.UUID;

public class ConversationFailover
{
    public static TpConnectReturn tpconnectWithFailover(String serviceName,
                                                        List<ConnectionFactoryEntry> validEntries,
                                                        FunctionThrowsResourceException<TpConnectReturn, CasualConnection> doCall)
    {
        BiFunctionThrowsResourceException<CasualConnection, UUID, TpConnectReturn> tpConnectWrapsConnection = (connection, uuid) -> {
            TpConnectReturn tpConnectReturn = doCall.apply(connection);
            if(tpConnectReturn.getErrorState() == ErrorState.OK)
            {
                Conversation conversation = ConversationImpl.of(connection, tpConnectReturn.getConversation().orElseThrow(() -> new CasualCallerException("tpconnect, ErrorState.OK but missing conversation!")));
                return TpConnectReturn.of(conversation);
            }
            return tpConnectReturn;
        };
        return issueCall(serviceName, validEntries, tpConnectWrapsConnection);
    }

    private static <R> R issueCall(String serviceName,
                                   List<ConnectionFactoryEntry> validEntries,
                                   BiFunctionThrowsResourceException<CasualConnection,UUID,R> wrapperFunction)
    {
        Exception thrownException = null;
        for (ConnectionFactoryEntry connectionFactoryEntry : validEntries)
        {
            try
            {
                // note, this connection NEEDS to be closed by the user application!!!
                CasualConnection con = connectionFactoryEntry.getConnectionFactory().getConnection();
                return wrapperFunction.apply(con, UUID.randomUUID());
            }
            catch (CasualConnectionException e)
            {
                //This error branch will most likely happen if there are connection errors during a service call
                connectionFactoryEntry.invalidate();
                // These exceptions are rollback-only, do not attempt any retries.
                throw new CasualResourceException("Call failed during execution to service=" + serviceName + " on connection=" + connectionFactoryEntry.getJndiName() + " because of a network connection error, retries not possible.", e);
            }
            catch (ResourceException e)
            {
                // This error branch will most likely happen on failure to establish connection with a casual backend
                connectionFactoryEntry.invalidate();
                // Do retries on ResourceExceptions. Save the thrown exception and return to the loop
                // If there are more entries to try that will be done, or the flow will exit and this
                // exception will be thrown wrapped at the end of the method.
                thrownException = e;
            }
        }
        throw new CasualResourceException("Call failed to all " + validEntries.size() + " available casual connections.", thrownException);
    }
    @FunctionalInterface
    interface BiFunctionThrowsResourceException<T,U,R>
    {
        R apply(T arg1, U arg2) throws ResourceException;
    }
}
