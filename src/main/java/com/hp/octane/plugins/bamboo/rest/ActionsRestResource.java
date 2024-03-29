/**
 *
 * Copyright 2017-2023 Open Text
 *
 * The only warranties for products and services of Open Text and
 * its affiliates and licensors (“Open Text”) are as may be set forth
 * in the express warranty statements accompanying such products and services.
 * Nothing herein should be construed as constituting an additional warranty.
 * Open Text shall not be liable for technical or editorial errors or
 * omissions contained herein. The information contained herein is subject
 * to change without notice.
 *
 * Except as specifically indicated otherwise, this document contains
 * confidential information and a valid license is required for possession,
 * use or copying. If this work is provided to the U.S. Government,
 * consistent with FAR 12.211 and 12.212, Commercial Computer Software,
 * Computer Software Documentation, and Technical Data for Commercial Items are
 * licensed to the U.S. Government under vendor's standard commercial license.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hp.octane.plugins.bamboo.rest;


import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.services.configuration.ConfigurationService;
import com.hp.octane.plugins.bamboo.octane.SDKBasedLoggerProvider;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;


@Path("/actions")//http://localhost:8085/rest/octane-admin/1.0/actions
@Scanned
public class ActionsRestResource {

    @Context
    HttpServletRequest request;
    private UserManager userManager;

    @Path("/octane-roots-cache")//http://localhost:8085/rest/octane-admin/1.0/actions/octane-roots-cache
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getOctaneRootsCache() {
        if (!hasPermissions(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("user does not have permission").build();
        }

        Map<String, Object> clients = new HashMap<>();
        OctaneSDK.getClients().forEach(

                client -> {
                    ConfigurationService cs = client.getConfigurationService();
                    clients.put(cs.getConfiguration().getLocationForLog(), cs.getOctaneRootsCacheCollection());
                }
        );

        return Response.ok(clients).build();
    }

    @Path("/octane-roots-cache/clear")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Response clearOctaneRootsCache() {
        if (!hasPermissions(request)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("user does not have permission").build();
        }

        OctaneSDK.getClients().stream().forEach(oc -> {
            oc.getConfigurationService().resetOctaneRootsCache();
        });
        return Response.ok("done").build();
    }

    private UserManager getUserManager() {
        if (userManager == null) {
            this.userManager = ComponentLocator.getComponent(UserManager.class);
        }
        return userManager;
    }

    private boolean hasPermissions(HttpServletRequest request) {
        UserProfile username = getUserManager().getRemoteUser(request);
        return (username != null && (getUserManager().isSystemAdmin(username.getUserKey()) || getUserManager().isAdmin(username.getUserKey())));
    }


}