package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.hp.octane.plugins.bamboo.octane.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ALMOctaneCucumberTestReporterConfigurator extends AbstractTaskConfigurator {

    public static final String CUCUMBER_REPORT_PATTERN_FIELD = "cucumberReportXML";
    public static final String MQM_RESULT_FOLDER_PREFIX = "MQM_Result";

    @Override
    public void populateContextForCreate(@NotNull Map<String, Object> context) {
        super.populateContextForCreate(context);
        registerArtifacts((Job) context.get("plan"));
    }

    private boolean registerArtifacts(@NotNull Job job) {
        String name = "MQM test results";
        String pattern = "**/" + MQM_RESULT_FOLDER_PREFIX + "/Build_${bamboo.buildNumber}/*.xml";
        return Utils.registerArtifactDefinition(job, name, pattern);
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
