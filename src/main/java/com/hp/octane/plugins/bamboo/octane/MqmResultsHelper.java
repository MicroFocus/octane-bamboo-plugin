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

package com.hp.octane.plugins.bamboo.octane;

import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.results.tests.TestResults;
import com.atlassian.bamboo.storage.StorageLocationService;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.dto.tests.*;
import com.hp.octane.integrations.utils.SdkConstants;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

public class MqmResultsHelper {
    private static StorageLocationService storageLocationService;
    private static DefaultOctaneConverter CONVERTER = DefaultOctaneConverter.getInstance();
    private static final Logger log = SDKBasedLoggerProvider.getLogger(MqmResultsHelper.class);

    private static StorageLocationService getStorageLocationService() {
        if (storageLocationService == null) {
            storageLocationService = ComponentLocator.getComponent(StorageLocationService.class);
        }
        return storageLocationService;
    }

    public static File getBuildResultDirectory(PlanKey plan) {
        return getStorageLocationService().getBuildResultsDirectory(plan);
    }

    public static Path getMqmResultFilePath(PlanResultKey planResultKey) {
        File dirFile = getBuildResultDirectory(planResultKey.getPlanKey());
        return Paths.get(dirFile.getAbsolutePath(), OctaneConstants.MQM_RESULT_FOLDER, "Build_" + planResultKey.getBuildNumber(), SdkConstants.General.MQM_TESTS_FILE_NAME);
    }

    public static Path getScmDataFilePath(PlanResultKey planResultKey) {
        File dirFile = getBuildResultDirectory(planResultKey.getPlanKey());
        return Paths.get(dirFile.getAbsolutePath(), OctaneConstants.MQM_RESULT_FOLDER, "Build_" + planResultKey.getBuildNumber(), OctaneConstants.SCM_DATA_FILE_NAME);
    }

    public static InputStream generateTestResultStream(com.atlassian.bamboo.v2.build.BuildContext buildContext, String jobId, String buildId) {

        InputStream output = null;
        HPRunnerType runnerType = HPRunnerTypeUtils.getHPRunnerType(buildContext.getRuntimeTaskDefinitions());
        CurrentBuildResult results = buildContext.getBuildResult();
        List<TestRun> testRuns = new ArrayList<>();

        if (results.getFailedTestResults() != null) {
            for (TestResults currentTestResult : results.getFailedTestResults()) {
                testRuns.add(CONVERTER.getTestRunFromTestResult(buildContext, runnerType, currentTestResult, TestRunResult.FAILED,
                        results.getTasksStartDate().getTime()));
            }
        }
        if (results.getSkippedTestResults() != null) {
            for (TestResults currentTestResult : results.getSkippedTestResults()) {
                testRuns.add(CONVERTER.getTestRunFromTestResult(buildContext, runnerType, currentTestResult, TestRunResult.SKIPPED,
                        results.getTasksStartDate().getTime()));
            }
        }
        if (results.getSuccessfulTestResults() != null) {
            for (TestResults currentTestResult : results.getSuccessfulTestResults()) {
                testRuns.add(CONVERTER.getTestRunFromTestResult(buildContext, runnerType, currentTestResult, TestRunResult.PASSED,
                        results.getTasksStartDate().getTime()));
            }
        }
        //filter tests that should not be reported to Octane
        List<TestRun> filteredTestRuns = filterTestToIgnoreFromResults(testRuns,buildContext);

        if (!filteredTestRuns.isEmpty()) {
            List<TestField> testFields = runnerType.getTestFields();
            BuildContext context = CONVERTER.getBuildContext(SdkConstants.General.INSTANCE_ID_TO_BE_SET_IN_SDK, jobId, buildId);
            TestsResult testsResult = DefaultOctaneConverter.getDTOFactory().newDTO(TestsResult.class)
                    .setTestRuns(filteredTestRuns)
                    .setBuildContext(context)
                    .setTestFields(testFields);

            //  return stream to SDK
            output = DefaultOctaneConverter.getDTOFactory().dtoToXmlStream(testsResult);

        }
        return output;
    }

    private static List<TestRun> filterTestToIgnoreFromResults(List<TestRun> testRuns, com.atlassian.bamboo.v2.build.BuildContext buildContext) {

        Set<String> testsToIgnoreList = new HashSet<>();
        //get the ignore tests from the local variable on the pipeline
        testsToIgnoreList.addAll(getTestToIgnoreFromVariable(buildContext.getVariableContext().getEffectiveVariables().get("octaneIgnoreTestsByName")));
        //get ignore tests from the global variable
        testsToIgnoreList.addAll(getTestToIgnoreFromVariable(buildContext.getVariableContext().getEffectiveVariables().get("octaneIgnoreTestsByNameGlobal")));

        List returnVal = testRuns.stream().filter(x -> !testsToIgnoreList.contains(x.getTestName())).collect(Collectors.toList());

        return returnVal;
    }

    private static List getTestToIgnoreFromVariable(VariableDefinitionContext testsToIgnoreVar) {

        if (testsToIgnoreVar == null || testsToIgnoreVar.getValue() == null || testsToIgnoreVar.getValue().isEmpty()) {
            return Collections.emptyList();
        }

        String[] testsToIgnore = testsToIgnoreVar.getValue().split(";");
        return Arrays.asList(testsToIgnore);
    }

    public static synchronized void saveStreamToFile(InputStream is, PlanResultKey planResultKey, Path targetFilePath) {
        try {
            if (is == null) {
                targetFilePath.toFile().createNewFile();
            } else {
                targetFilePath.getParent().toFile().mkdirs();
                Files.copy(is, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            log.error("Failed to saveToTestResultFile of " + planResultKey.toString(), e);
        }
    }

}
