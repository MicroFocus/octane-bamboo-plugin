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
