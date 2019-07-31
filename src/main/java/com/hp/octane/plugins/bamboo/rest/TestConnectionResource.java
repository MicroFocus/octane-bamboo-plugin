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

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.bamboo.user.BambooUser;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.sal.api.component.ComponentLocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.octane.integrations.OctaneConfiguration;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.exceptions.OctaneConnectivityException;
import com.hp.octane.plugins.bamboo.octane.BambooPluginServices;
import com.hp.octane.plugins.bamboo.octane.MqmProject;
import com.hp.octane.plugins.bamboo.octane.utils.Utils;
import org.acegisecurity.acls.Permission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.List;
import java.util.UUID;


@Path("/test")
@Scanned
public class TestConnectionResource {
    private static final Logger log = LogManager.getLogger(TestConnectionResource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();


    @POST
    @Path("/testconnection")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response testConfiguration(@Context HttpServletRequest request, OctaneConnection model) throws IOException {
        try {
            OctaneConnectionManager.getInstance().replacePlainPasswordIfRequired(model);
            tryToConnect(model);
            return Response.ok().build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    private void tryToConnect(OctaneConnection dto) throws OctaneConnectivityException {
        String location = dto.getLocation();
        String clientId = dto.getClientId();
        String clientSecret = dto.getClientSecret();
        String bambooUser = dto.getBambooUser();
        if (location == null || location.isEmpty()) {
            throw new IllegalArgumentException("Location URL is required");
        }
        if (clientId == null || clientId.isEmpty()) {
            throw new IllegalArgumentException("Client ID is required");
        }

        if (clientSecret == null || clientSecret.isEmpty()) {
            throw new IllegalArgumentException("Client Secret is required");
        }

        if (bambooUser == null || bambooUser.isEmpty()) {
            throw new IllegalArgumentException("Bamboo user is required");
        }
        if (!isUserExist(bambooUser)) {
            throw new IllegalArgumentException("Bamboo user does not exist");
        }

        if (!hasBuildPermission(bambooUser)) {
            throw new IllegalArgumentException("Bamboo user doesn't have enough permissions");
        }
        MqmProject mqmProject = Utils.parseUiLocation(location);
        if (mqmProject.hasError()) {
            throw new IllegalArgumentException(mqmProject.getErrorMsg());
        }
        OctaneConfiguration testedOctaneConfiguration = new OctaneConfiguration(UUID.randomUUID().toString(),
                mqmProject.getLocation(),
                mqmProject.getSharedSpace());
        testedOctaneConfiguration.setClient(clientId);
        testedOctaneConfiguration.setSecret(clientSecret);
        try {
            OctaneSDK.testAndValidateOctaneConfiguration(testedOctaneConfiguration.getUrl(),
                    testedOctaneConfiguration.getSharedSpace(),
                    testedOctaneConfiguration.getClient(),
                    testedOctaneConfiguration.getSecret(),
                    BambooPluginServices.class);
        } catch (OctaneConnectivityException e) {
            throw new IllegalArgumentException(e.getErrorMessageVal());
        } catch (Exception e) {
            throw new IllegalArgumentException("Unexpected exception :" + e.getMessage());
        }
    }

    private boolean hasBuildPermission(String userName) {
        PlanManager planManager = ComponentLocator.getComponent(PlanManager.class);
        List<Chain> plans = planManager.getAllPlans(Chain.class);
        if (plans.isEmpty()) {
            log.info("Server does not have any plan to run");
            return true;
        }
        boolean hasPermission;
        for (Chain chain : plans) {
            hasPermission = isUserHasPermission(BambooPermission.BUILD, userName, chain);
            if (hasPermission) {
                return true;
            }
        }
        return false;
    }

    private boolean isUserHasPermission(Permission permissionType, String user, Chain chain) {
        BambooPermissionManager permissionManager = ComponentLocator.getComponent(BambooPermissionManager.class);
        return permissionManager.hasPermission(user, permissionType, chain);
    }

    private boolean isUserExist(String userName) {
        BambooUserManager bambooUserManager = ComponentLocator.getComponent(com.atlassian.bamboo.user.BambooUserManager.class);
        BambooUser bambooUser = bambooUserManager.loadUserByUsername(userName);
        if (bambooUser != null) {
            return true;
        }
        return false;
    }
}