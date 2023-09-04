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
import com.hp.octane.integrations.executor.TestsToRunConverterResult;
import com.hp.octane.integrations.executor.TestsToRunConvertersFactory;
import com.hp.octane.integrations.executor.TestsToRunFramework;
import com.hp.octane.plugins.bamboo.octane.OctaneConstants;
import com.hp.octane.plugins.bamboo.octane.executor.TestFrameworkConverterTask;
import com.hp.octane.plugins.bamboo.octane.utils.JsonHelper;
import org.apache.commons.lang.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@Scanned
@Path("/converter-task/convert")
public class FrameworkConverterRestResources {
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response doTestConvert(@Context HttpServletRequest request, String body) {
        try {
            Map<String, String> params = JsonHelper.deserialize(body, HashMap.class);
            String rawTests = params.get(OctaneConstants.TESTS_TO_RUN_PARAMETER), framework = params.get(TestFrameworkConverterTask.FRAMEWORK_PARAMETER), format = params.get(TestFrameworkConverterTask.CONVERTER_FORMAT);

            if (!hasPermissions(request)) {
                throw new IllegalArgumentException("Bamboo user does not exist");
            }

            if (StringUtils.isEmpty(rawTests)) {
                throw new IllegalArgumentException("'Tests to run' parameter is missing");
            }

            if (StringUtils.isEmpty(framework)) {
                throw new IllegalArgumentException("'Framework' parameter is missing");
            }

            TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(framework);
            if (TestsToRunFramework.Custom.equals(testsToRunFramework) && StringUtils.isEmpty(format)) {
                throw new IllegalArgumentException("'Format' parameter is missing");
            }

            TestsToRunConverterResult convertResult = TestsToRunConvertersFactory.createConverter(testsToRunFramework)
                    .setFormat(format)
                    .convert(rawTests, TestFrameworkConverterTask.DEFAULT_EXECUTING_DIRECTORY, null);
            return Response.ok("Conversion is successful : " + convertResult.getConvertedTestsString()).build();
        } catch (Exception e) {
            return Response.status(405).entity("Failed to convert : " + e.getMessage()).build();
        }
    }

    private boolean hasPermissions(HttpServletRequest request) {
        UserProfile username = ComponentLocator.getComponent(UserManager.class).getRemoteUser(request);
        return (username != null);
    }
}


