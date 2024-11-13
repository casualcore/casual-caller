/*
 * Copyright (c) 2024, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.http

import jakarta.ws.rs.core.Application
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.JerseyTest
import se.laz.casual.api.buffer.CasualBuffer
import se.laz.casual.api.buffer.ServiceReturn
import se.laz.casual.api.buffer.type.CStringBuffer
import se.laz.casual.api.buffer.type.JsonBuffer
import se.laz.casual.api.buffer.type.OctetBuffer
import se.laz.casual.api.buffer.type.fielded.FieldedTypeBuffer
import se.laz.casual.api.flags.ErrorState
import se.laz.casual.api.flags.ServiceReturnState
import se.laz.casual.http.test.resource.TestPath
import se.laz.casual.http.test.resource.TestResource
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class HttpClientTest extends Specification
{
   @Shared
   HttpClient client = HttpClient.of()
   @Shared
   def content = '{"msg":"bazinga!"}'
   @Shared
   def key = 'FLD_STRING1'
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
      given:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/${TestPath.OK.path}")
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      expect:
      response.getServiceReturnState() == returnState
      response.getErrorState() == errorState
      response.getReplyBuffer().getBytes() == buffer.getBytes()
      where:
      buffer                                                      || returnState                   || errorState
      JsonBuffer.of(content)                                      || ServiceReturnState.TPSUCCESS  || ErrorState.OK
      OctetBuffer.of([content.getBytes(StandardCharsets.UTF_8)])  || ServiceReturnState.TPSUCCESS  || ErrorState.OK
      FieldedTypeBuffer.create().write( key, content)             || ServiceReturnState.TPSUCCESS  || ErrorState.OK
      CStringBuffer.of(content)                                   || ServiceReturnState.TPSUCCESS  || ErrorState.OK
   }

   def 'not found'()
   {
      given:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/does-not-exist")
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      expect:
      response.getServiceReturnState() == returnState
      response.getErrorState() == errorState
      response.getReplyBuffer().getBytes().size() == 0
      where:
      buffer                                                      || returnState                   || errorState
      JsonBuffer.of(content)                                      || ServiceReturnState.TPFAIL     || ErrorState.TPENOENT
      OctetBuffer.of([content.getBytes(StandardCharsets.UTF_8)])  || ServiceReturnState.TPFAIL     || ErrorState.TPENOENT
      FieldedTypeBuffer.create().write( key, content)             || ServiceReturnState.TPFAIL     || ErrorState.TPENOENT
      CStringBuffer.of(content)                                   || ServiceReturnState.TPFAIL     || ErrorState.TPENOENT
   }

   def 'error'()
   {
      given:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/${TestPath.ERROR.path}")
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      expect:
      response.getServiceReturnState() == returnState
      response.getErrorState() == errorState
      response.getReplyBuffer().getBytes().size() == 0
      where:
      buffer                                                      || returnState                   || errorState
      JsonBuffer.of(content)                                      || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
      OctetBuffer.of([content.getBytes(StandardCharsets.UTF_8)])  || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
      FieldedTypeBuffer.create().write( key, content)             || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
      CStringBuffer.of(content)                                   || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
   }

   def 'error with body'()
   {
      given:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/${TestPath.ERROR_WITH_BODY.path}")
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      expect:
      response.getServiceReturnState() == returnState
      response.getErrorState() == errorState
      response.getReplyBuffer().getBytes().size() != 0
      where:
      buffer                                                      || returnState                   || errorState
      JsonBuffer.of(content)                                      || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
      OctetBuffer.of([content.getBytes(StandardCharsets.UTF_8)])  || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
      FieldedTypeBuffer.create().write( key, content)             || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
      CStringBuffer.of(content)                                   || ServiceReturnState.TPFAIL     || ErrorState.TPESVCERR
   }

   def 'timeout'()
   {
      given:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/${TestPath.TIMEOUT.path}")
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      expect:
      response.getServiceReturnState() == returnState
      response.getErrorState() == errorState
      response.getReplyBuffer().getBytes().size() == 0
      where:
      buffer                                                      || returnState                   || errorState
      JsonBuffer.of(content)                                      || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
      OctetBuffer.of([content.getBytes(StandardCharsets.UTF_8)])  || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
      FieldedTypeBuffer.create().write( key, content)             || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
      CStringBuffer.of(content)                                   || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
   }

   def 'timeout with body'()
   {
      given:
      URI uri = new URI(resource.getBaseUri().toString() + "${root}/${TestPath.TIMEOUT_WITH_BODY.path}")
      ServiceReturn<CasualBuffer> response = client.request(uri, buffer)
      expect:
      response.getServiceReturnState() == returnState
      response.getErrorState() == errorState
      response.getReplyBuffer().getBytes().size() != 0
      where:
      buffer                                                      || returnState                   || errorState
      JsonBuffer.of(content)                                      || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
      OctetBuffer.of([content.getBytes(StandardCharsets.UTF_8)])  || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
      FieldedTypeBuffer.create().write( key, content)             || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
      CStringBuffer.of(content)                                   || ServiceReturnState.TPFAIL     || ErrorState.TPETIME
   }

}
