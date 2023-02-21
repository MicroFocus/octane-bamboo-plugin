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
package com.hp.octane.plugins.bamboo.octane.uft;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.task.*;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.executor.impl.TestingToolType;
import com.hp.octane.integrations.services.entities.EntitiesService;
import com.hp.octane.integrations.uft.UftTestDiscoveryUtils;
import com.hp.octane.integrations.uft.UftTestDispatchUtils;
import com.hp.octane.integrations.uft.items.CustomLogger;
import com.hp.octane.integrations.uft.items.JobRunContext;
import com.hp.octane.integrations.uft.items.UftTestDiscoveryResult;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class UftDiscoveryTask implements TaskType {
    public static final String WORKSPACE_ID_PARAM = "workspaceId";
    public static final String SCM_REPOSITORY_ID_PARAM = "scmRepositoryId";
    public static final String TEST_RUNNER_ID_PARAM = "testRunnerId";
    public static final String SPACE_CONFIGURATION_ID_PARAM = "spaceConfigurationId";

    public static final String RESULT_FOLDER = "_discovery_results";
    public static final String RESULT_FILE_NAME_PREFIX = "uft_discovery_result_build_";

    public static final String UFT_DISCOVERY_HAS_CLIENTS = "uftDiscoveryHasClients";


    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {

        //long planId = taskContext.getBuildContext().getPlanId();

        final BuildLogger buildLogger = taskContext.getBuildLogger();
        File checkoutLocation = new File(taskContext.getBuildContext().getCheckoutLocation().values().iterator().next());
        UftTestDiscoveryResult result = UftTestDiscoveryUtils.doFullDiscovery(checkoutLocation);
        buildLogger.addBuildLogEntry(String.format("Found %s tests", result.getAllTests().size()));
        buildLogger.addBuildLogEntry(String.format("Found %s data tables ", result.getAllScmResourceFiles().size()));


        String spaceConfigurationId = taskContext.getConfigurationMap().get(SPACE_CONFIGURATION_ID_PARAM);
        if (StringUtils.isEmpty(spaceConfigurationId)) {
            spaceConfigurationId = OctaneSDK.getClients().size() == 1 ? OctaneSDK.getClients().get(0).getInstanceId() : null;
        }
        if (StringUtils.isEmpty(spaceConfigurationId)) {
            buildLogger.addBuildLogEntry("Space configuration is missing! This field should contain instance ID of space configuration!!!!!!!!!!!");
            return TaskResultBuilder.newBuilder(taskContext).failed().build();
        }

        try {
            result.setWorkspaceId(taskContext.getConfigurationMap().get(WORKSPACE_ID_PARAM));
            result.setScmRepositoryId(taskContext.getConfigurationMap().get(SCM_REPOSITORY_ID_PARAM));
            result.setTestRunnerId(taskContext.getConfigurationMap().get(TEST_RUNNER_ID_PARAM));
            result.setConfigurationId(spaceConfigurationId);
            result.setTestingToolType(TestingToolType.UFT);
            result.setFullScan(true);

            if(OctaneSDK.hasClients()) {
                OctaneSDK.getClientByInstanceId(spaceConfigurationId).getConfigurationService().validateConfigurationAndGetConnectivityStatus();

                EntitiesService entitiesService = OctaneSDK.getClientByInstanceId(spaceConfigurationId).getEntitiesService();
                UftTestDispatchUtils.prepareDiscoveryResultForDispatch(entitiesService, result);


                JobRunContext jobRunContext = new JobRunContext(taskContext.getBuildContext().getProjectName(), taskContext.getBuildContext().getBuildNumber());
                UftTestDispatchUtils.dispatchDiscoveryResult(entitiesService, result, jobRunContext, new CustomLogger() {
                    @Override
                    public void add(String s) {
                        buildLogger.addBuildLogEntry(s);
                    }
                });
            } else {
                buildLogger.addBuildLogEntry("Discovery task have no clients exist, will dispatch test on job complete trigger");
                taskContext.getBuildContext().getBuildResult().getCustomBuildData().put(UFT_DISCOVERY_HAS_CLIENTS, "false");
            }

            //save result
            File discoveryResultsFolder = new File(taskContext.getWorkingDirectory(), UftDiscoveryTask.RESULT_FOLDER);
            if (!discoveryResultsFolder.exists()) {
                discoveryResultsFolder.mkdir();
            }

            File reportXmlFile = new File(discoveryResultsFolder, UftDiscoveryTask.RESULT_FILE_NAME_PREFIX + taskContext.getBuildContext().getBuildNumber() + ".xml");
            try {
                result.writeToFile(reportXmlFile);
                buildLogger.addBuildLogEntry(String.format("%s result file is saved in %s",
                        OctaneSDK.hasClients() ? "Final" :"Initial",
                        reportXmlFile.getAbsolutePath()));
            } catch (IOException e) {
                buildLogger.addBuildLogEntry(String.format("Failed to save final result file  " + reportXmlFile.getAbsolutePath() + " : " + e.getMessage()));
            }
            return TaskResultBuilder.newBuilder(taskContext).success().build();
        } catch (Exception e) {
            buildLogger.addBuildLogEntry(String.format("Octane configuration is not valid : " + e.getMessage()));
            return TaskResultBuilder.newBuilder(taskContext).failed().build();
        }
    }

}
