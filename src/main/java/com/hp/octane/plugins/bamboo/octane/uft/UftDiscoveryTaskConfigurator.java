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

package com.hp.octane.plugins.bamboo.octane.uft;

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

public class UftDiscoveryTaskConfigurator extends AbstractTaskConfigurator {


    @Override
    public void populateContextForEdit(Map<String, Object> context, TaskDefinition taskDefinition) {
        super.populateContextForEdit(context, taskDefinition);
        context.put(UftDiscoveryTask.WORKSPACE_ID_PARAM, taskDefinition.getConfiguration().get(UftDiscoveryTask.WORKSPACE_ID_PARAM));
        context.put(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM, taskDefinition.getConfiguration().get(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM));
        context.put(UftDiscoveryTask.TEST_RUNNER_ID_PARAM, taskDefinition.getConfiguration().get(UftDiscoveryTask.TEST_RUNNER_ID_PARAM));
        context.put(UftDiscoveryTask.SPACE_CONFIGURATION_ID_PARAM, taskDefinition.getConfiguration().get(UftDiscoveryTask.SPACE_CONFIGURATION_ID_PARAM));
    }

    @Override
    public Map<String, String> generateTaskConfigMap(final ActionParametersMap params, final TaskDefinition previousTaskDefinition) {
        final Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);

        config.put(UftDiscoveryTask.WORKSPACE_ID_PARAM, params.getString(UftDiscoveryTask.WORKSPACE_ID_PARAM));
        config.put(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM, params.getString(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM));
        config.put(UftDiscoveryTask.TEST_RUNNER_ID_PARAM, params.getString(UftDiscoveryTask.TEST_RUNNER_ID_PARAM));
        config.put(UftDiscoveryTask.SPACE_CONFIGURATION_ID_PARAM, params.getString(UftDiscoveryTask.SPACE_CONFIGURATION_ID_PARAM));

        return config;
    }

    @Override
    public void validate(final ActionParametersMap params, final ErrorCollection errorCollection) {
        super.validate(params, errorCollection);
        validateNumericalValue(UftDiscoveryTask.WORKSPACE_ID_PARAM, params.getString(UftDiscoveryTask.WORKSPACE_ID_PARAM), errorCollection);
        validateNumericalValue(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM, params.getString(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM), errorCollection);
        validateNumericalValue(UftDiscoveryTask.TEST_RUNNER_ID_PARAM, params.getString(UftDiscoveryTask.TEST_RUNNER_ID_PARAM), errorCollection);
        validateRequiredStringValue(UftDiscoveryTask.SPACE_CONFIGURATION_ID_PARAM, params.getString(UftDiscoveryTask.SPACE_CONFIGURATION_ID_PARAM), errorCollection);
    }

    private void validateNumericalValue(String field, String value, ErrorCollection errorCollection) {
        try {
            Long.parseLong(value);
        } catch (Exception e) {
            errorCollection.addError(field, "Expected numerical value");
        }
    }

    private void validateRequiredStringValue(String field, String value, ErrorCollection errorCollection) {
        if (StringUtils.isEmpty(value)) {
            errorCollection.addError(field, "Value is missing");
        }

    }
}
