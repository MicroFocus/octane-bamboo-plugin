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

import com.atlassian.bamboo.v2.build.BuildContext;

import java.util.HashMap;
import java.util.Map;

public class BuildContextCache {

    private static Map<String, BuildContext> map = new HashMap<>();

    public static void add(String planResultKey, BuildContext buildContext) {
        map.put(planResultKey, buildContext);
    }

    public static BuildContext extract(String planResultKey) {
        return map.remove(planResultKey);
    }

}
