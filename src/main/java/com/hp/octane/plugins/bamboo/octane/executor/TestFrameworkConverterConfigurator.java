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

import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.task.AbstractTaskConfigurator;
import com.atlassian.bamboo.task.TaskDefinition;
import com.hp.octane.integrations.executor.TestsToRunFramework;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TestFrameworkConverterConfigurator extends AbstractTaskConfigurator {

    private static String SUPPORTED_FRAMEWORKS = "supportedFrameworks";

    @Override
    public void populateContextForEdit(Map<String, Object> context, TaskDefinition taskDefinition) {
        super.populateContextForEdit(context, taskDefinition);
        context.put(TestFrameworkConverterTask.FRAMEWORK_PARAMETER, taskDefinition.getConfiguration().get(TestFrameworkConverterTask.FRAMEWORK_PARAMETER));
        context.put(TestFrameworkConverterTask.CONVERTER_FORMAT, taskDefinition.getConfiguration().get(TestFrameworkConverterTask.CONVERTER_FORMAT));

        populateContextForLists(context);
    }


    @Override
    public void populateContextForCreate(@NotNull Map<String, Object> context) {
        super.populateContextForCreate(context);
        context.put(TestFrameworkConverterTask.FRAMEWORK_PARAMETER, "");
        context.put(TestFrameworkConverterTask.CONVERTER_FORMAT, "");
        populateContextForLists(context);
    }

    @Override
    public Map<String, String> generateTaskConfigMap(final ActionParametersMap params, final TaskDefinition previousTaskDefinition) {
        final Map<String, String> config = super.generateTaskConfigMap(params, previousTaskDefinition);

        config.put(TestFrameworkConverterTask.FRAMEWORK_PARAMETER, params.getString(TestFrameworkConverterTask.FRAMEWORK_PARAMETER));
        config.put(TestFrameworkConverterTask.CONVERTER_FORMAT, params.getString(TestFrameworkConverterTask.CONVERTER_FORMAT));
        return config;
    }

    private void populateContextForLists(Map<String, Object> context) {
        context.put(SUPPORTED_FRAMEWORKS, getSupportedFrameworks());
    }

    private Object getSupportedFrameworks() {
        Map<String, String> map = new HashMap<String, String>();
        for (TestsToRunFramework fr : TestsToRunFramework.values()) {
            if(TestsToRunFramework.Custom.equals(fr)){
                continue;
            }
            map.put(fr.value(), fr.getDesc());
        }

        return map;
    }


}
