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

package com.hp.octane.plugins.bamboo.rest;

import java.util.LinkedList;
import java.util.List;

public class OctaneConnectionCollection {
    private List<OctaneConnection> octaneConnections;

    public OctaneConnectionCollection() {
        this.octaneConnections = new LinkedList<>();
    }

    public List<OctaneConnection> getOctaneConnections() {
        return octaneConnections;
    }

    public void setOctaneConnections(List<OctaneConnection> octaneConnections) {
        this.octaneConnections = octaneConnections;
    }

    public void addConnection(OctaneConnection octaneConnection) {
        octaneConnections.add(octaneConnection);
    }

    public boolean removeConnection(OctaneConnection octaneConnection) {
        return octaneConnections.remove(octaneConnection);
    }

    public void updateConnection(OctaneConnection octaneConnection) {
        getConnectionById(octaneConnection.getId())
                .setLocation(octaneConnection.getLocation())
                .setClientId(octaneConnection.getClientId())
                .setClientSecret(octaneConnection.getClientSecret())
                .setBambooUser(octaneConnection.getBambooUser());
    }

    public OctaneConnection getConnectionById(String id) {
        return octaneConnections.stream().filter(c -> c.getId().equals(id)).findFirst().orElse(null);
    }


}
