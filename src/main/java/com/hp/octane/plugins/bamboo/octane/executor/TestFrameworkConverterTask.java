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
package com.hp.octane.plugins.bamboo.octane.executor;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.task.*;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.hp.octane.integrations.executor.TestsToRunConverterResult;
import com.hp.octane.integrations.executor.TestsToRunConvertersFactory;
import com.hp.octane.integrations.executor.TestsToRunFramework;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class TestFrameworkConverterTask implements TaskType {

    public static String FRAMEWORK_PARAMETER = "framework";

    private final String TESTS_TO_RUN_PARAMETER = "testsToRun";
    private final String TESTS_TO_RUN_CONVERTED_PARAMETER = "testsToRunConverted";

    private final String DEFAULT_EXECUTING_DIRECTORY = "${bamboo.build.working.directory}";
    private final String CHECKOUT_DIRECTORY_PARAMETER = "testsToRunCheckoutDirectory";

    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {
        boolean skip = false;
        final BuildLogger buildLogger = taskContext.getBuildLogger();
        Map<String, VariableDefinitionContext> variables = taskContext.getBuildContext().getVariableContext().getEffectiveVariables();

        String rawTests = variables.containsKey(TESTS_TO_RUN_PARAMETER) ? variables.get(TESTS_TO_RUN_PARAMETER).getValue() : null;

        String checkoutDirectory = DEFAULT_EXECUTING_DIRECTORY;
        if (StringUtils.isNotEmpty(rawTests)) {
            buildLogger.addBuildLogEntry(TESTS_TO_RUN_PARAMETER + " found with value : " + rawTests);
            String checkoutDir = variables.containsKey(CHECKOUT_DIRECTORY_PARAMETER) ? variables.get(CHECKOUT_DIRECTORY_PARAMETER).getValue() : null;
            if (StringUtils.isEmpty(checkoutDir)) {
                checkoutDir = DEFAULT_EXECUTING_DIRECTORY;
                buildLogger.addBuildLogEntry(CHECKOUT_DIRECTORY_PARAMETER + " is not defined, using default value : " + checkoutDir);
            } else {
                buildLogger.addBuildLogEntry(CHECKOUT_DIRECTORY_PARAMETER + " parameter found with value : " + checkoutDirectory);
            }
        } else {
            skip = true;
            buildLogger.addBuildLogEntry(TESTS_TO_RUN_PARAMETER + " is not defined or has empty value. Skipping.");
        }

        String framework = taskContext.getConfigurationMap().get(FRAMEWORK_PARAMETER);
        if (StringUtils.isEmpty(framework)) {
            buildLogger.addBuildLogEntry("No framework is selected. Skipping.");
            skip = true;
        }

        if (!skip) {
            TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(framework);
            TestsToRunConverterResult convertResult = TestsToRunConvertersFactory.createConverter(testsToRunFramework).convert(rawTests, checkoutDirectory);
            buildLogger.addBuildLogEntry("Found #tests : " + convertResult.getTestsData().size());
            buildLogger.addBuildLogEntry(TESTS_TO_RUN_CONVERTED_PARAMETER + " = " + convertResult.getConvertedTestsString());
            taskContext.getBuildContext().getVariableContext().addResultVariable(TESTS_TO_RUN_CONVERTED_PARAMETER, convertResult.getConvertedTestsString());
        }

        return TaskResultBuilder.newBuilder(taskContext).success().build();
    }
}
