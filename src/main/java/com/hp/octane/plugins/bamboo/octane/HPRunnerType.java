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


package com.hp.octane.plugins.bamboo.octane;

import com.hp.octane.integrations.dto.tests.TestField;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Runner type of test
 */
public enum HPRunnerType {

    UFT("UFT", null, "UFT"),
    StormRunnerLoad("StormRunner Load", null, null),
    StormRunnerFunctional("StormRunner Functional", null, null),
    LOAD_RUNNER("LoadRunner", null, null),
    PerformanceCenter("Performance Center", "Performance", null),
    NONE(null, null, null);

    private String testingToolType;
    private String testType;
    private String framework;

    HPRunnerType(String testingToolType, String testType, String framework) {
        this.testingToolType = testingToolType;
        this.testType = testType;
        this.framework = framework;
    }

    public String TestingToolType() {
        return testingToolType;
    }

    public String TestType() {
        return testType;
    }

    public String Framework() {
        return framework;
    }

    public List<TestField> getTestFields() {
        if (NONE.equals(this)) {
            return null;
        } else {
            List<TestField> list = new ArrayList<>();
            if (StringUtils.isNotEmpty(Framework())) {
                list.add(DefaultOctaneConverter.getDTOFactory().newDTO(TestField.class).setType("Framework").setValue(Framework()));
            }
            if (StringUtils.isNotEmpty(TestingToolType())) {
                list.add(DefaultOctaneConverter.getDTOFactory().newDTO(TestField.class).setType("Testing_Tool_Type").setValue(TestingToolType()));
            }
            if (StringUtils.isNotEmpty(TestType())) {
                list.add(DefaultOctaneConverter.getDTOFactory().newDTO(TestField.class).setType("Test_Type").setValue(TestType()));
            }

            return list;
        }
    }


}
