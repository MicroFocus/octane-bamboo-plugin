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

package com.hp.octane.plugins.bamboo.octane.executor;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.configuration.ConfigurationMap;
import com.atlassian.bamboo.task.*;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.hp.octane.integrations.executor.TestsToRunConverterResult;
import com.hp.octane.integrations.executor.TestsToRunConvertersFactory;
import com.hp.octane.integrations.executor.TestsToRunFramework;
import com.hp.octane.plugins.bamboo.octane.OctaneConstants;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class TestFrameworkConverterTask implements TaskType {
    private String framework;
    private String format;

    public final static String FRAMEWORK_PARAMETER = "framework";
    public final static String CONVERTER_FORMAT = "customConverterFormat";


    private final static String TESTS_TO_RUN_CONVERTED_PARAMETER = "testsToRunConverted";

    public final static String DEFAULT_EXECUTING_DIRECTORY = "${bamboo.build.working.directory}";
    private final static String CHECKOUT_DIRECTORY_PARAMETER = "testsToRunCheckoutDirectory";

    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {
        boolean skip = false;
        final BuildLogger buildLogger = taskContext.getBuildLogger();
        Map<String, VariableDefinitionContext> variables = taskContext.getBuildContext().getVariableContext().getEffectiveVariables();
        String rawTests = getTestsToRunValue(variables);
        String checkoutDirectory = DEFAULT_EXECUTING_DIRECTORY;
        if (StringUtils.isNotEmpty(rawTests)) {
            addLogEntry(buildLogger, OctaneConstants.TESTS_TO_RUN_PARAMETER + " found with value : " + rawTests);
            String checkoutDir = variables.containsKey(CHECKOUT_DIRECTORY_PARAMETER) ? variables.get(CHECKOUT_DIRECTORY_PARAMETER).getValue() : null;
            if (StringUtils.isEmpty(checkoutDir)) {
                checkoutDir = DEFAULT_EXECUTING_DIRECTORY;
                addLogEntry(buildLogger, CHECKOUT_DIRECTORY_PARAMETER + " is not defined, using default value : " + checkoutDir);
            } else {
                addLogEntry(buildLogger, CHECKOUT_DIRECTORY_PARAMETER + " parameter found with value : " + checkoutDirectory);
            }
        } else {
            skip = true;
            addLogEntry(buildLogger, OctaneConstants.TESTS_TO_RUN_PARAMETER + " is not defined or has empty value. Skipping.");
        }
        ConfigurationMap configurationMap = taskContext.getConfigurationMap();
        framework = configurationMap.get(FRAMEWORK_PARAMETER);

        if (StringUtils.isEmpty(framework)) {
            addLogEntry(buildLogger, "No framework is selected. Skipping.");
            skip = true;
        }

        if (!skip) {
            format = configurationMap.get(CONVERTER_FORMAT);
            TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(framework);
            addLogEntry(buildLogger, "framework : " + framework);
            if (framework.equals("custom")) {
                addLogEntry(buildLogger, "format : " + format);
            }
            TestsToRunConverterResult convertResult = TestsToRunConvertersFactory.createConverter(testsToRunFramework).setFormat(format)
                    .convert(rawTests, checkoutDirectory, null);
            addLogEntry(buildLogger, "Found #tests : " + convertResult.getTestsData().size());
            addLogEntry(buildLogger, TESTS_TO_RUN_CONVERTED_PARAMETER + " = " + convertResult.getConvertedTestsString());
            addLogEntry(buildLogger, TESTS_TO_RUN_CONVERTED_PARAMETER + " length = " + convertResult.getConvertedTestsString().length());
            //if framework is uft and converter result more then 4000 ,save to file and save path reference to the file

            String testToRunConverted = convertResult.getConvertedTestsString();
            if (convertResult.getConvertedTestsString().length() >= OctaneConstants.BAMBOO_MAX_FIELD_CAPACITY) {
                if (testsToRunFramework.equals(TestsToRunFramework.MF_UFT)) {
                    File converterResultFile = saveUftTestsToMtbxFile(taskContext, buildLogger, convertResult);
                    testToRunConverted = converterResultFile.getAbsolutePath();
                } else {
                    String msg = String.format("Conversion value is too long (%s characters) for Bamboo. Check possibility to reduce number of tests for execution. Max allowed value is %s.",
                            convertResult.getConvertedTestsString().length(), OctaneConstants.BAMBOO_MAX_FIELD_CAPACITY);
                    buildLogger.addBuildLogEntry(msg);
                    throw new TaskException(msg);
                }
            }
            taskContext.getBuildContext().getVariableContext().addResultVariable(TESTS_TO_RUN_CONVERTED_PARAMETER, testToRunConverted);
        }

        return TaskResultBuilder.newBuilder(taskContext).success().build();
    }

    @NotNull
    private String getTestsToRunValue(Map<String, VariableDefinitionContext> variables) {
        StringBuilder rawTestsStringBuilder = new StringBuilder();
        if (variables.containsKey(OctaneConstants.TEST_TO_RUN_SPLIT_COUNT)) {
            int splitCount = Integer.parseInt(variables.get(OctaneConstants.TEST_TO_RUN_SPLIT_COUNT).getValue());
            for (int i = 0; i < splitCount; i++) {
                rawTestsStringBuilder.append(variables.containsKey(OctaneConstants.TESTS_TO_RUN_PARAMETER + i) ? variables.get(OctaneConstants.TESTS_TO_RUN_PARAMETER + i).getValue() : "");
            }
        } else {
            rawTestsStringBuilder.append(variables.containsKey(OctaneConstants.TESTS_TO_RUN_PARAMETER) ? variables.get(OctaneConstants.TESTS_TO_RUN_PARAMETER).getValue() : "");
        }
        return rawTestsStringBuilder.toString();
    }

    private static void addLogEntry(BuildLogger buildLogger, String message) {
        buildLogger.addBuildLogEntry("octaneTestFrameworkConverter: " + message);
    }

    private static File saveUftTestsToMtbxFile(@NotNull TaskContext taskContext, BuildLogger buildLogger, TestsToRunConverterResult convertResult) throws TaskException {
        File converterResultFile = new File(taskContext.getWorkingDirectory(), "Uft_Build_" + taskContext.getBuildContext().getBuildNumber() + ".mtbx");

        try {
            FileOutputStream fop = new FileOutputStream(converterResultFile);
            byte[] contentInBytes = convertResult.getConvertedTestsString().replace("${bamboo.build.working.directory}", taskContext.getWorkingDirectory().getAbsolutePath()).getBytes(StandardCharsets.UTF_8);

            fop.write(contentInBytes);
            fop.flush();
            fop.close();

            buildLogger.addBuildLogEntry("MTBX file is saved in " + converterResultFile.getAbsolutePath());
            return converterResultFile;
        } catch (IOException e) {
            String msg = String.format("Failed to save MTBX file  " + converterResultFile.getAbsolutePath() + " : " + e.getMessage());
            buildLogger.addBuildLogEntry(msg);
            throw new TaskException(msg, e);
        }
    }
}
