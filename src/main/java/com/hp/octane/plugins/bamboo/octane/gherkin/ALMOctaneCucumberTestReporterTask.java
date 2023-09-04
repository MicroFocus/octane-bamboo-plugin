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
package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.task.*;
import com.hp.octane.plugins.bamboo.octane.OctaneConstants;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Paths;

public class ALMOctaneCucumberTestReporterTask implements TaskType {

    @NotNull
    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {
        final BuildLogger buildLogger = taskContext.getBuildLogger();

        if (!taskContext.isFinalising()) {
            buildLogger.addBuildLogEntry("***************ALM octane cucumber test reporter task must be final*************");
        }
        File targetDirectory = Paths.get(taskContext.getWorkingDirectory().getAbsolutePath(),
                OctaneConstants.MQM_RESULT_FOLDER,
                "Build_" + taskContext.getBuildContext().getBuildNumber()).toFile();
        targetDirectory.mkdirs();

        try {
            ALMOctaneCucumberTestReporterUtils.copyTestResults(targetDirectory.getAbsolutePath(),
                    taskContext.getWorkingDirectory().getAbsolutePath(),
                    taskContext.getConfigurationMap().get(ALMOctaneCucumberTestReporterConfigurator.CUCUMBER_REPORT_PATTERN_FIELD),
                    buildLogger,
                    taskContext.getBuildContext().getBuildResult().getTasksStartDate());
            ALMOctaneCucumberTestReporterUtils.aggregateGherkinFilesToMqmResultFile(targetDirectory.getAbsolutePath(), taskContext.getBuildContext().getShortName(), taskContext.getBuildContext().getBuildNumber(), buildLogger);
            return TaskResultBuilder.newBuilder(taskContext).success().build();
        } catch (Exception e) {
            buildLogger.addBuildLogEntry("Exception running cucumber task : " + e.getMessage());
            return TaskResultBuilder.newBuilder(taskContext).failedWithError().build();
        }
    }

}
