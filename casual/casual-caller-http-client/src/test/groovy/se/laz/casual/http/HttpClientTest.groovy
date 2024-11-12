package se.laz.casual.http

import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.Response
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.JerseyTest
import se.laz.casual.api.buffer.CasualBuffer
import se.laz.casual.api.buffer.ServiceReturn
import se.laz.casual.api.buffer.type.JsonBuffer
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.ServiceReturnState
import se.laz.casual.http.test.resource.TestPath
import se.laz.casual.http.test.resource.TestResource
import spock.lang.Shared
import spock.lang.Specification

class HttpClientTest extends Specification
{
   @Shared
   HttpClient client = HttpClient.of()
   @Shared
   def content = '{"msg":"bazinga!"}'
   @Shared
   def root = 'test'

   JerseyTest resource = new JerseyTest(){
      @Override
      protected Application configure(){
         return new ResourceConfig(TestResource.class)
      }
   }

   def setup()
   {
      resource.setUp()
   }

   def cleanup()
   {
      resource.tearDown()
   }

   def 'ok'()
   {
      when:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/${TestPath.OK.path}")
      CasualBuffer buffer = JsonBuffer.of('"msg":"bazinga"')
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      then:
      response.getServiceReturnState() == ServiceReturnState.TPSUCCESS
      response.getErrorState() == ErrorState.OK
      response.getReplyBuffer() == buffer
   }
   def 'error'()
   {
      when:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/${TestPath.ERROR.path}")
      CasualBuffer buffer = JsonBuffer.of('"msg":"bazinga"')
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      then:
      response.getServiceReturnState() == ServiceReturnState.TPFAIL
      response.getErrorState() == ErrorState.TPESVCERR
      response.getReplyBuffer().getBytes().size() == 0
   }

}
