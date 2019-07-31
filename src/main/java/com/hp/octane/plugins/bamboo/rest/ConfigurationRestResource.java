/*
 *     Copyright 2017 EntIT Software LLC, a Micro Focus company, L.P.
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.hp.octane.plugins.bamboo.rest;


import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Consumes({MediaType.APPLICATION_JSON})
@Produces({MediaType.APPLICATION_JSON})
@Path("/space-configs")
@Scanned
public class ConfigurationRestResource {

    private OctaneConnectionManager octaneConnectionManager = OctaneConnectionManager.getInstance();
    private static final Logger log = LogManager.getLogger(ConfigurationRestResource.class);
    private UserManager userManager;


    private UserManager getUserManager() {
        if (userManager == null) {
            this.userManager = ComponentLocator.getComponent(UserManager.class);
        }
        return userManager;
    }

    private boolean hasPermissions(HttpServletRequest request) {
        UserProfile username = getUserManager().getRemoteUser(request);
        return (username != null && getUserManager().isSystemAdmin(username.getUserKey()));
    }

    @PUT
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateConfiguration(@Context HttpServletRequest request, OctaneConnection model, @PathParam("id") String id) {
        log.info("update configuration " + id);
        if (!hasPermissions(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("user does not have permission").build();
        }
        if (!model.getId().equals(id)) {
            return Response.status(Response.Status.BAD_REQUEST).entity("invalid request : unexpected id").build();
        }

        if (octaneConnectionManager.getConnectionById(model.getId()) == null) {
            Response.status(Response.Status.NOT_FOUND).entity("No configuration with id " + id).build();
        }

        octaneConnectionManager.replacePlainPasswordIfRequired(model);
        octaneConnectionManager.updateConfiguration(model);
        return Response.ok().entity(model).build();
    }


    @POST
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addConfiguration(@Context HttpServletRequest request, OctaneConnection model) {
        if (!hasPermissions(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("user dont have permission").build();
        }
        log.info("add configuration");
        if (!model.getId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("New configuration should not contain instance id").build();
        }
        model.setId(UUID.randomUUID().toString());

        try {
            octaneConnectionManager.addConfiguration(model);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
        return Response.ok().entity(model).build();
    }


    @DELETE
    @Path("/{id}")
    public Response deleteConfiguration(@Context HttpServletRequest request, @PathParam("id") String id) {
        if (octaneConnectionManager.getConnectionById(id) == null) {
            Response.status(Response.Status.NOT_FOUND).entity("No configuration with id " + id).build();
        }

        try {
            octaneConnectionManager.deleteConfiguration(id);
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
        return Response.ok().entity(true).build();
    }

    @GET
    @Path("/")
    public Response getAllConfigurations(@Context HttpServletRequest request) {
        List<OctaneConnection> newList = new ArrayList();
        octaneConnectionManager.getOctaneConnections().getOctaneConnections().forEach(c -> newList.add(c.cloneForUI()));
        return Response.ok(newList).build();
    }


}