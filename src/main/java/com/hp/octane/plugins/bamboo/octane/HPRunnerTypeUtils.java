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

import com.atlassian.bamboo.task.runtime.RuntimeTaskDefinition;

import java.util.List;

/**
 * HPRunnerTypeUtils
 */
public class HPRunnerTypeUtils {

    public static String UFT_FS_PLUGIN_KEY = "com.adm.app-delivery-management-bamboo:RunFromFileSystemUftTask";
    public static HPRunnerType getHPRunnerType(List<RuntimeTaskDefinition> taskDefinitions) {
        try {
            for (RuntimeTaskDefinition definition : taskDefinitions) {
                if (definition.getPluginKey().equals(UFT_FS_PLUGIN_KEY)) {
                    return HPRunnerType.UFT;
                }
            }
        } catch (Exception e) {
            //do nothing
        }

        return HPRunnerType.NONE;
    }

}
