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

package com.hp.octane.plugins.bamboo.listener;

import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.parameters.CIParameterType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ParametersHelper {

    public static final String SUITE_ID_PARAMETER = "suiteId";
    public static final String SUITE_RUN_ID_PARAMETER = "suiteRunId";
    public static final String TESTS_TO_RUN_PARAMETER = "testsToRun";
    private static List<String> encryptedVariable = Arrays.asList("password", "sshkey", "passphrase", "secret");

    public static void addParametersToEvent(CIEvent ciEvent, com.atlassian.bamboo.v2.build.BuildContext buildContext) {
        try {
            Map<String, VariableDefinitionContext> variables = buildContext.getVariableContext().getEffectiveVariables();
            List<CIParameter> parameters = new ArrayList<>();
            variables.entrySet().forEach(v -> {
                if (!ParametersHelper.isEncrypted(v.getValue())) {
                    parameters.add(
                            DTOFactory.getInstance().newDTO(CIParameter.class)
                                    .setName(v.getKey())
                                    .setValue(v.getValue().getValue())
                                    .setType(CIParameterType.STRING));
                }
            });
            if (parameters != null && !parameters.isEmpty()) {
                ciEvent.setParameters(parameters);
            }
        } catch (Exception e) {
            //do nothing - try/catch just to be on safe side for all other plans
        }
    }

    public static boolean isEncrypted(VariableDefinitionContext variable) {
        for (String val : encryptedVariable) {
            if (variable.getKey().toLowerCase().contains(val)) {
                return true;
            }
        }
        return false;
    }
}
