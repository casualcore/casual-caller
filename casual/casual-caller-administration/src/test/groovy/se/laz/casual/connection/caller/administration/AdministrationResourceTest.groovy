/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.administration

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import jakarta.ws.rs.core.Application
import org.glassfish.hk2.utilities.binding.AbstractBinder
import org.glassfish.jersey.server.ResourceConfig
import org.glassfish.jersey.test.JerseyTest
import se.laz.casual.connection.caller.administration.model.*
import se.laz.casual.network.messages.domain.TransactionType
import spock.lang.Shared
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class AdministrationResourceTest extends Specification {

  @Shared
  Service service

  @Shared
  ServiceConnection serviceConnection

  @Shared
  ServiceInfo serviceInfo

  @Shared
  AdministrationService administrationServiceMock

  @Shared
  HttpClient client = HttpClient.newBuilder().build();

  JerseyTest resource

  def createResource() {
    resource = new JerseyTest(){
      @Override
      protected Application configure(){
        def config = new ResourceConfig(AdministrationResource.class)
        config.register(new AbstractBinder() {
          @Override
          protected void configure() {
            bind(administrationServiceMock).to(AdministrationService.class).ranked(1)
          }
        })
        return config
      }
    }
    resource.setUp()
  }

  def setup()
  {
    service = new Service(
        "servicename/test", "category1", TransactionType.JOIN, 1, 2, serviceConnection)
    serviceConnection = new ServiceConnection("someconnetion", true)
    serviceInfo = new ServiceInfo(List.of(service),
        List.of(serviceConnection))
  }

  def cleanup()
  {
    if(Objects.nonNull(resource)) {
      resource.tearDown()
    }
  }

  def 'testGetConfiguration'() {
    given:
    Configuration configuration = new Configuration(
        "jndiHost", 1000, true, 1023, "routeName"
    )
    administrationServiceMock = Mock(AdministrationService.class) {
      getConfiguration() >> configuration
    }
    createResource()
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(resource.getBaseUri().toString() + "configuration"))
        .GET().build()
    when:
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    then:
    response.statusCode() == 200
    Configuration responseData = new Gson().fromJson(response.body(), Configuration.class)
    responseData == configuration
  }

  def 'testGetConnections'() {
    given:
    List<ServiceConnection> serviceConnections = List.of(serviceConnection)
    administrationServiceMock = Mock(AdministrationService.class) {
      getConnections() >> serviceConnections
    }
    createResource()
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(resource.getBaseUri().toString() + "connections"))
        .GET().build()
    when:
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    then:
    response.statusCode() == 200
    List<ServiceConnection> responseData = new Gson().fromJson(response.body(), new TypeToken<ArrayList<ServiceConnection>>() {}.getType())
    responseData == serviceConnections
  }

  def 'testGetServices'() {
    given:
    administrationServiceMock = Mock(AdministrationService.class) {
      getServices() >> List.of(service.name())
      getService(*_) >> serviceInfo
    }
    createResource()
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(resource.getBaseUri().toString() + "services"))
        .GET().build()
    when:
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    then:
    response.statusCode() == 200
    List<ServiceInfo> responseData = new Gson().fromJson(response.body(), new TypeToken<ArrayList<ServiceInfo>>() {}.getType())
    responseData == List.of(serviceInfo)
  }

  def 'testGetQueues'() {
    given:
    List<String> queueNames = List.of("q1", "q2")
    administrationServiceMock = Mock(AdministrationService.class) {
      getQueues() >> queueNames
    }
    createResource()
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(resource.getBaseUri().toString() + "queues"))
        .GET().build()
    when:
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    then:
    response.statusCode() == 200
    List<String> responseData = new Gson().fromJson(response.body(), new TypeToken<ArrayList<String>>() {}.getType())
    responseData == queueNames
  }

  def 'testDiscoveryService'() {
    given:
    administrationServiceMock = Mock(AdministrationService.class) {
      getService(*_) >> serviceInfo
    }
    createResource()
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(resource.getBaseUri().toString() + "discovery/service/${service.name()}"))
        .GET().build()
    when:
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    then:
    response.statusCode() == 200
    ServiceInfo responseData = new Gson().fromJson(response.body(), ServiceInfo.class)
    responseData == serviceInfo
  }

  def 'testDiscoveryQueue'() {
    given:
    Queue queue = new Queue("q1/queue", new QueueConnection("jndiQueue", true))
    administrationServiceMock = Mock(AdministrationService.class) {
      getQueue(*_) >> queue
    }
    createResource()
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(resource.getBaseUri().toString() + "discovery/queue/${queue.name()}"))
        .GET().build()
    when:
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    then:
    response.statusCode() == 200
    Queue responseData = new Gson().fromJson(response.body(), Queue.class)
    responseData == queue
  }
}
