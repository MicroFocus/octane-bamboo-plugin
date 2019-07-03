package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;

import java.util.Map;

public class ALMOctaneCucumberTestReporterConfigurator extends AbstractTaskConfigurator {

    @Override
    public void populateContextForEdit(Map<String, Object> context, TaskDefinition taskDefinition) {
        super.populateContextForEdit(context, taskDefinition);
        context.put(ALMOctaneCucumberTestReporterUtils.OCTANE_REPORT_XML, taskDefinition.getConfiguration().get(ALMOctaneCucumberTestReporterUtils.OCTANE_REPORT_XML));
    }

    @Override
    public Map<String, String> generateTaskConfigMap(final ActionParametersMap params, final TaskDefinition previousTaskDefinition) {
        final Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);
        config.put(ALMOctaneCucumberTestReporterUtils.OCTANE_REPORT_XML, params.getString(ALMOctaneCucumberTestReporterUtils.OCTANE_REPORT_XML));
        return config;
    }

    @Override
    public void validate(final ActionParametersMap params, final ErrorCollection errorCollection) {
        super.validate(params, errorCollection);
        validateXmlFileValue(ALMOctaneCucumberTestReporterUtils.OCTANE_REPORT_XML, params.getString(ALMOctaneCucumberTestReporterUtils.OCTANE_REPORT_XML), errorCollection);
    }

    private void validateXmlFileValue(String field, String value, ErrorCollection errorCollection) {
        if (value.contains("../")) {
            errorCollection.addError(field, "Field should not contain '../'");
        }
    }
}
