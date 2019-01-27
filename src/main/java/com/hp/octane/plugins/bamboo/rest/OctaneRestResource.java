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
import com.atlassian.sal.api.component.ComponentLocator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.configuration.OctaneConfiguration;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLHandshakeException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import org.acegisecurity.acls.Permission;
import java.util.List;

@Provider
@Path("/testconnection")
public class OctaneRestResource {
    private static final Logger log = LoggerFactory.getLogger(OctaneRestResource.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response testConfiguration(String body) throws IOException {
        OctaneConnectionDTO dto = objectMapper.readValue(body, OctaneConnectionDTO.class);
        return Response.ok(tryToConnect(dto)).build();
    }

    private String tryToConnect(OctaneConnectionDTO dto) {
        try {
            String octaneUrl = dto.getOctaneUrl();
            String accessKey = dto.getAccessKey();
            String apiSecret = dto.getApiSecret();
            String userName = dto.getUserName();
            if (octaneUrl == null || octaneUrl.isEmpty()) {
                return "Location URL is required";
            }
            if (accessKey == null || accessKey.isEmpty()) {
                return "Client ID is required";
            }

            if (apiSecret == null || apiSecret.isEmpty()) {
                return "Client Secret is required";
            }

            if(userName == null || userName.isEmpty()){
                return "Bamboo user is required";
            }
            if (!IsUserExist(userName)) {
                return "Bamboo user does not exist\n";
            }

            if(!isUserAuthorized(userName)){
                return "Bamboo user misconfigured or doesn't have enough permissions";
            }

            OctaneConfiguration config = OctaneSDK.getInstance().getConfigurationService().buildConfiguration(octaneUrl, accessKey, apiSecret);
            OctaneResponse result = OctaneSDK.getInstance().getConfigurationService().validateConfiguration(config);
            if (result.getStatus() == HttpStatus.SC_OK) {
                return "Success";
            } else if (result.getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                return "You are unauthorized";
            } else if (result.getStatus() == HttpStatus.SC_FORBIDDEN) {
                return "Connection Forbidden";

            } else if (result.getStatus() == HttpStatus.SC_NOT_FOUND) {
                return "URL not found";
            }
            return "Error validating octane config";

        } catch(SSLHandshakeException e){
            log.error("Exception at tryToConnect", e);
            return e.getMessage();
        } catch (Exception e) {
            log.error("Exception at tryToConnect", e);
            return "Error validating octane config";
        }
    }

    private boolean isUserAuthorized(String userName) {
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

    private boolean IsUserExist(String userName) {
        BambooUserManager bambooUserManager = ComponentLocator.getComponent(com.atlassian.bamboo.user.BambooUserManager.class);
        BambooUser bambooUser = bambooUserManager.loadUserByUsername(userName);
        if (bambooUser != null) {
            return true;
        }
        return false;
    }
}