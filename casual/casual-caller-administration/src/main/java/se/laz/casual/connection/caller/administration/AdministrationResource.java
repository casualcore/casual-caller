/*
 * Copyright (c) 2026, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.connection.caller.administration;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import se.laz.casual.connection.caller.administration.model.ServiceInfo;

import java.util.ArrayList;
import java.util.List;

@Path("/")
public class AdministrationResource
{
    @Inject
    private AdministrationService administrationService;

    @Path("/configuration")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConfiguration()
    {
        return Response.ok(administrationService.getConfiguration()).build();
    }

    @Path("/connections")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getConnections()
    {
        return Response.ok(administrationService.getConnections()).build();
    }

    @Path("/services")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getServices()
    {
        List<ServiceInfo> allCachedServices = new ArrayList<>();
        administrationService.getServices().forEach(s -> allCachedServices.add(administrationService.getService(s)));
        return Response.ok(allCachedServices).build();
    }

    @Path("/queues")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getQueues()
    {
        return Response.ok(administrationService.getQueues()).build();
    }

    @Path("/discovery/service/{service : .+}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getService(@PathParam("service") String serviceName)
    {
        return Response.ok(administrationService.getService(serviceName)).build();
    }

    @Path("/discovery/queue/{queue : .+}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getQueues(@PathParam("queue") String queueName)
    {
        return Response.ok(administrationService.getQueue(queueName)).build();
    }
}
