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
import com.hp.octane.integrations.OctaneConfiguration;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.entities.Entity;
import com.hp.octane.integrations.exceptions.OctaneConnectivityException;
import com.hp.octane.integrations.exceptions.OctaneSDKGeneralException;
import com.hp.octane.integrations.utils.OctaneUrlParser;
import com.hp.octane.plugins.bamboo.octane.BambooPluginServices;
import com.hp.octane.plugins.bamboo.octane.SDKBasedLoggerProvider;
import org.acegisecurity.acls.Permission;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Path("/test")
@Scanned
public class TestConnectionResource {
    private static final Logger log = SDKBasedLoggerProvider.getLogger(TestConnectionResource.class);

    @POST
    @Path("/testconnection")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response testConfiguration(@Context HttpServletRequest request, OctaneConnection model) {
        try {
            OctaneConnectionManager.getInstance().replacePlainPasswordIfRequired(model);
            List<Entity> workspaces = tryToConnectAndGetAvailableWorkspaces(model);
            String tooltip="";
            if (workspaces != null && !workspaces.isEmpty()) {
                int workspaceNumberLimit = 30;
                String titleNewLine = "\n";
                String suffix = (workspaces.size() > workspaceNumberLimit) ? titleNewLine + "and more " + (workspaces.size() - workspaceNumberLimit) + " workspaces" : "";
                tooltip= workspaces.stream()
                        .sorted(Comparator.comparingInt(e -> Integer.parseInt(e.getId())))
                        .limit(workspaceNumberLimit)
                        .map(w -> w.getId() + " - " + w.getName())
                        .collect(Collectors.joining(titleNewLine, "Available workspaces are : " + titleNewLine, suffix));
            }
            return Response.status(Response.Status.OK).entity(tooltip).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.CONFLICT).entity(e.getMessage()).build();
        }
    }

    private List<Entity> tryToConnectAndGetAvailableWorkspaces(OctaneConnection dto) throws OctaneConnectivityException {
        String location = dto.getLocation();
        String clientId = dto.getClientId();
        String clientSecret = dto.getClientSecret();
        String bambooUser = dto.getBambooUser();
        if (StringUtils.isEmpty(location)) {
            throw new IllegalArgumentException("Location URL is missing");
        }
        if (StringUtils.isEmpty(clientId)) {
            throw new IllegalArgumentException("Client ID is missing");
        }

        if (StringUtils.isEmpty(clientSecret)) {
            throw new IllegalArgumentException("Client Secret is missing");
        }

        if (StringUtils.isEmpty(bambooUser)) {
            throw new IllegalArgumentException("Bamboo user is missing");
        }
        if (!isUserExist(bambooUser)) {
            throw new IllegalArgumentException("Bamboo user does not exist");
        }

        if (!hasBuildPermission(bambooUser)) {
            throw new IllegalArgumentException("Bamboo user doesn't have enough permissions");
        }
        OctaneUrlParser octaneUrlParser = OctaneUrlParser.parse(location);

        OctaneConfiguration testedOctaneConfiguration = OctaneConfiguration.createWithUiLocation(UUID.randomUUID().toString(), location);
        testedOctaneConfiguration.setClient(clientId);
        testedOctaneConfiguration.setSecret(clientSecret);
        try {
            return OctaneSDK.testOctaneConfigurationAndFetchAvailableWorkspaces(testedOctaneConfiguration.getUrl(),
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