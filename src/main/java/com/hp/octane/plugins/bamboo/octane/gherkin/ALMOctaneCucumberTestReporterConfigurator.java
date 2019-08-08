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

package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.hp.octane.plugins.bamboo.octane.ArtifactsHelper;
import com.hp.octane.plugins.bamboo.octane.OctaneConstants;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ALMOctaneCucumberTestReporterConfigurator extends AbstractTaskConfigurator {

    public static final String CUCUMBER_REPORT_PATTERN_FIELD = "cucumberReportXML";

    @Override
    public void populateContextForCreate(@NotNull Map<String, Object> context) {
        super.populateContextForCreate(context);
        registerArtifacts((Job) context.get("plan"));
    }

    private boolean registerArtifacts(@NotNull Job job) {
        String pattern = "**/" + OctaneConstants.MQM_RESULT_FOLDER + "/Build_${bamboo.buildNumber}/*.xml";
        return ArtifactsHelper.registerArtifactDefinition(job, OctaneConstants.MQM_RESULT_ARTIFACT_NAME, pattern);
    }

    @Override
    public void populateContextForEdit(Map<String, Object> context, TaskDefinition taskDefinition) {
        super.populateContextForEdit(context, taskDefinition);
        registerArtifacts((Job) context.get("plan"));
        context.put(CUCUMBER_REPORT_PATTERN_FIELD, taskDefinition.getConfiguration().get(CUCUMBER_REPORT_PATTERN_FIELD));
    }

    @Override
    public Map<String, String> generateTaskConfigMap(final ActionParametersMap params, final TaskDefinition previousTaskDefinition) {
        final Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);
        config.put(CUCUMBER_REPORT_PATTERN_FIELD, params.getString(CUCUMBER_REPORT_PATTERN_FIELD));
        return config;
    }

    @Override
    public void validate(final ActionParametersMap params, final ErrorCollection errorCollection) {
        super.validate(params, errorCollection);
        validateXmlFileValue(CUCUMBER_REPORT_PATTERN_FIELD, params.getString(CUCUMBER_REPORT_PATTERN_FIELD), errorCollection);
    }

    private void validateXmlFileValue(String field, String value, ErrorCollection errorCollection) {
        if (value.contains("../")) {
            errorCollection.addError(field, "Field should not contain '../'");
        }
    }
}
