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
import com.atlassian.bamboo.configuration.ConfigurationMap;
import com.atlassian.bamboo.task.*;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.hp.octane.integrations.executor.TestsToRunConverter;
import com.hp.octane.integrations.executor.TestsToRunConverterResult;
import com.hp.octane.integrations.executor.TestsToRunConvertersFactory;
import com.hp.octane.integrations.executor.TestsToRunFramework;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TestFrameworkConverterTask implements TaskType {
    private String framework;
    private String format;

    public static String FRAMEWORK_PARAMETER = "framework";
    public static String CONVERTER_FORMAT = "customConverterFormat";


    private final String TESTS_TO_RUN_PARAMETER = "testsToRun";
    private final String TESTS_TO_RUN_CONVERTED_PARAMETER = "testsToRunConverted";

    private final String DEFAULT_EXECUTING_DIRECTORY = "${bamboo.build.working.directory}";
    private final String CHECKOUT_DIRECTORY_PARAMETER = "testsToRunCheckoutDirectory";
    private final int BAMBOO_MAX_FIELD_CAPACITY = 4000;

    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {
        boolean skip = false;
        final BuildLogger buildLogger = taskContext.getBuildLogger();
        Map<String, VariableDefinitionContext> variables = taskContext.getBuildContext().getVariableContext().getEffectiveVariables();

        String rawTests = variables.containsKey(TESTS_TO_RUN_PARAMETER) ? variables.get(TESTS_TO_RUN_PARAMETER).getValue() : null;

        String checkoutDirectory = DEFAULT_EXECUTING_DIRECTORY;
        if (StringUtils.isNotEmpty(rawTests)) {
            addLogEntry( buildLogger,TESTS_TO_RUN_PARAMETER + " found with value : " + rawTests);
            String checkoutDir = variables.containsKey(CHECKOUT_DIRECTORY_PARAMETER) ? variables.get(CHECKOUT_DIRECTORY_PARAMETER).getValue() : null;
            if (StringUtils.isEmpty(checkoutDir)) {
                checkoutDir = DEFAULT_EXECUTING_DIRECTORY;
                addLogEntry( buildLogger,CHECKOUT_DIRECTORY_PARAMETER + " is not defined, using default value : " + checkoutDir);
            } else {
                addLogEntry( buildLogger,CHECKOUT_DIRECTORY_PARAMETER + " parameter found with value : " + checkoutDirectory);
            }
        } else {
            skip = true;
            addLogEntry( buildLogger,TESTS_TO_RUN_PARAMETER + " is not defined or has empty value. Skipping.");
        }
        ConfigurationMap configurationMap = taskContext.getConfigurationMap();
        framework = configurationMap.get(FRAMEWORK_PARAMETER);

        if (StringUtils.isEmpty(framework)) {
            addLogEntry( buildLogger,"No framework is selected. Skipping.");
            skip = true;
        }

        if (!skip) {
            format = configurationMap.get(CONVERTER_FORMAT);
            TestsToRunFramework testsToRunFramework = TestsToRunFramework.fromValue(framework);
            addLogEntry( buildLogger,"framework : " + framework);
            if (framework.equals("custom")) {
                addLogEntry( buildLogger,"format : " + format);
            }
            TestsToRunConverterResult convertResult = TestsToRunConvertersFactory.createConverter(testsToRunFramework).setFormat("")
                    .convert(rawTests, checkoutDirectory);
            addLogEntry( buildLogger,"Found #tests : " + convertResult.getTestsData().size());
            addLogEntry( buildLogger,TESTS_TO_RUN_CONVERTED_PARAMETER + " = " + convertResult.getConvertedTestsString());
            addLogEntry( buildLogger,TESTS_TO_RUN_CONVERTED_PARAMETER + " length = " + convertResult.getConvertedTestsString().length());
            //if framework is uft and converter result more then 4000 ,save to file and save path reference to the file

            String testToRunConverted = convertResult.getConvertedTestsString();
            if (convertResult.getConvertedTestsString().length() >= BAMBOO_MAX_FIELD_CAPACITY && testsToRunFramework.equals(TestsToRunFramework.MF_UFT)) {
                File converterResultFile = saveUftTestsToMtbxFile(taskContext, buildLogger, convertResult);
                testToRunConverted = converterResultFile.getAbsolutePath();

            }
            taskContext.getBuildContext().getVariableContext().addResultVariable(TESTS_TO_RUN_CONVERTED_PARAMETER, testToRunConverted);
        }

        return TaskResultBuilder.newBuilder(taskContext).success().build();
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
