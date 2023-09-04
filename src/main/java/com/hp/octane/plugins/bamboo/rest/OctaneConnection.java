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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class OctaneConnection {
    @XmlElement
    private String id;
    @XmlElement
    private String location;
    @XmlElement
    private String clientId;
    @XmlElement
    private String clientSecret;
    @XmlElement
    private String bambooUser;

    public OctaneConnection() {
    }

    public OctaneConnection(String id, String location, String clientId, String clientSecret, String bambooUser) {
        this.id = id;
        this.location = location;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.bambooUser = bambooUser;
    }

    public String getId() {
        return id;
    }

    public OctaneConnection setId(String id) {
        this.id = id;
        return this;
    }

    public String getLocation() {
        return location;
    }

    public OctaneConnection setLocation(String location) {
        this.location = location;
        return this;
    }

    public String getClientId() {
        return clientId;
    }

    public OctaneConnection setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public OctaneConnection setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
        return this;
    }

    public String getBambooUser() {
        return bambooUser;
    }

    public OctaneConnection setBambooUser(String bambooUser) {
        this.bambooUser = bambooUser;
        return this;
    }

    public OctaneConnection cloneForUI(){
        return new OctaneConnection()
                .setId(this.getId())
                .setLocation(this.getLocation())
                .setClientId(this.getClientId())
                .setClientSecret(OctaneConnectionManager.PLAIN_PASSWORD)
                .setBambooUser(this.getBambooUser());
    }
}
