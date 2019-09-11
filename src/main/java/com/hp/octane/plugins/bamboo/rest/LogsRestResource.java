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
import com.hp.octane.plugins.bamboo.octane.SDKBasedLoggerProvider;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;


@Path("/logs")//http://localhost:8085/rest/octane-admin/1.0/logs
@Scanned
public class LogsRestResource {

    @Context
    HttpServletRequest request;

    private static final Logger log = SDKBasedLoggerProvider.getLogger(LogsRestResource.class);
    private UserManager userManager;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLastLog() {
        return getLogFile(null);
    }

    @Path("/{id}")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLogById(@PathParam("id") Integer id) {
        return getLogFile(id);
    }

    private Response getLogFile(Integer id) {
        if (!hasPermissions(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("user does not have permission").build();
        }

        java.nio.file.Path path = Paths.get(
                SDKBasedLoggerProvider.getAllowedStorageFile().getAbsolutePath(),
                "nga",
                "logs",
                id == null ? "nga.log" : String.format("nga-%s.log", id));

        if (path.toFile().exists()) {
            try {
                String str = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                return Response.ok(str).build();
            } catch (IOException e) {
                log.error("Failed to read log file : " + e.getMessage(), e);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Failed to read log file").build();
            }


        } else {
            return Response.status(Response.Status.NOT_FOUND).entity("No log file is found").build();
        }
    }

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


}