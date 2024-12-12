# CasualCaller Producer

```java
import se.laz.casual.connection.caller.CasualCaller;
import se.laz.casual.test.service.remote.MissingResourceException;

import jakarta.enterprise.inject.Produces;
import javax.naming.InitialContext;
import javax.naming.NamingException;

public class CasualCallerProducer
{
    @Produces
    public CasualCaller get()
    {
        try
        {
            InitialContext context = new InitialContext();
            return (CasualCaller) context.lookup("java:/global/casual-caller-app/casual-caller/CasualCallerImpl");
        }
        catch (NamingException e)
        {
            throw new MissingResourceException("Failed finding CasualCallerImpl, using CasualCaller will not work", e);
        }
    }
}
```

You will always find CasualCaller at ```java:/global/casual-caller-app/casual-caller/CasualCallerImpl``` if the casual-caller-app has been successfully deployed.


# Example Conversation

```java
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import se.laz.casual.api.Conversation;
import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ConversationReturn;
import se.laz.casual.api.buffer.type.OctetBuffer;
import se.laz.casual.api.conversation.TpConnectReturn;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.connection.caller.CasualCaller;

import java.util.Optional;

@Stateless
public class SomeBean
{
    private CasualCaller casualCaller;
    
    @Inject
    public SomeBean(CasualCaller casualCaller)
    {
        this.casualCaller = casualCaller;
    }
    
    public String exampleConversation(CasualBuffer msg, String serviceName, Flag<AtmiFlags> flags)
    {
        try(TpConnectReturn tpConnectReturn = casualCaller.tpconnect(serviceName, msg, flags))
        {
            if(tpConnectReturn.getErrorState() != ErrorState.OK)
            {
                throw new TPConnectFailedException(tpConnectReturn.getErrorState().name());
            }
            Conversation conversation = tpConnectReturn.getConversation().orElseThrow(() -> new TPConnectFailedException("ErrorState.OK but no conversation!"));
            StringBuilder builder = new StringBuilder("Payload:\n");
            msg = OctetBuffer.of("Extra, extra, read all about it!\n".getBytes(StandardCharsets.UTF_8));
            // send buffer and hand over control
            conversation.tpsend(msg,true);
            ErrorState errorState = ErrorState.OK;
            while(conversation.isReceiving() && errorState == ErrorState.OK)
            {
                ConversationReturn<CasualBuffer> conversationReturn = conversation.tprecv();
                Optional<ErrorState> maybeError = conversationReturn.getErrorState();
                errorState = maybeError.orElse(ErrorState.OK);
                if(errorState == ErrorState.OK)
                {
                    msg = OctetBuffer.of(conversationReturn.getReplyBuffer().getBytes());
                    Optional<byte[]> payload = msg.getBytes().isEmpty() ? Optional.empty() : Optional.ofNullable(msg.getBytes().get(0));
                    payload.ifPresent(d -> builder.append(new String(d)));
                }
            }
            return builder + "\n Error: " + errorState.name();
        }
    }
}

```

This example calls some service that expects an OctetBuffer.

Note the usage of tpconnect within try-with-resources, this ensures that both the connection and the conversation are always closed.
