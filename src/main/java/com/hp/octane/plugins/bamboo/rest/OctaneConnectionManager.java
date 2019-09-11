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

import com.atlassian.plugin.PluginAccessor;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.hp.octane.integrations.OctaneClient;
import com.hp.octane.integrations.OctaneConfiguration;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.utils.OctaneUrlParser;
import com.hp.octane.plugins.bamboo.octane.BambooPluginServices;
import com.hp.octane.plugins.bamboo.octane.SDKBasedLoggerProvider;
import com.hp.octane.plugins.bamboo.octane.utils.JsonHelper;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class OctaneConnectionManager {

    private static final Logger log = SDKBasedLoggerProvider.getLogger(OctaneConnectionManager.class);
    private PluginSettingsFactory settingsFactory;
    private static final String PLUGIN_PREFIX = "com.hp.octane.plugins.bamboo.";
    public static final String CONFIGURATIONS_LIST = PLUGIN_PREFIX + "configuration_list";
    public static final String PLAIN_PASSWORD = "___PLAIN_PASSWORD____";
    private OctaneConnectionCollection octaneConnectionCollection;
    private static OctaneConnectionManager instance = new OctaneConnectionManager();

    public static OctaneConnectionManager getInstance() {
        return instance;
    }

    private OctaneConnectionManager() {
    }

    public OctaneConnectionCollection getOctaneConnections() {
        return octaneConnectionCollection;
    }

    private void saveSettings() {
        try {
            String confStr = JsonHelper.serialize(octaneConnectionCollection);
            PluginSettings settings = this.settingsFactory.createGlobalSettings();
            settings.put(CONFIGURATIONS_LIST, confStr);
        } catch (IOException e) {
            log.error("Failed to saveSettings : " + e.getMessage());
        }
    }

    public OctaneConnection getConnectionById(String id) {
        return octaneConnectionCollection.getConnectionById(id);
    }

    public void addConfiguration(OctaneConnection newConfiguration) {
        log.info("add new configuration: " + newConfiguration.getLocation());
        addSdkClient(newConfiguration);
        octaneConnectionCollection.addConnection(newConfiguration);
        saveSettings();
    }

    public void updateConfiguration(OctaneConnection octaneConnection) {
        log.info("update configuration: " + octaneConnection.getLocation());
        updateClientInSDK(octaneConnection);
        octaneConnectionCollection.updateConnection(octaneConnection);
        saveSettings();
        return;
    }

    public boolean deleteConfiguration(String id) {
        OctaneConnection octaneConnection = getConnectionById(id);
        if (octaneConnection != null) {
            log.info("delete configuration: " + octaneConnection.getLocation());
            removeClientFromSDK(id);
            boolean removed = octaneConnectionCollection.removeConnection(octaneConnection);
            saveSettings();
        }

        return octaneConnection != null;
    }

    public void replacePlainPasswordIfRequired(OctaneConnection octaneConnection) {
        if (octaneConnection.getClientSecret().equals(OctaneConnectionManager.PLAIN_PASSWORD)) {
            octaneConnection.setClientSecret(getConnectionById(octaneConnection.getId()).getClientSecret());
        }
    }

    private void addSdkClient(OctaneConnection configuration) {
        OctaneConfiguration octaneConfiguration = OctaneConfiguration.createWithUiLocation(configuration.getId(), configuration.getLocation());
        octaneConfiguration.setClient(configuration.getClientId());
        octaneConfiguration.setSecret(configuration.getClientSecret());
        OctaneSDK.addClient(octaneConfiguration, BambooPluginServices.class);
    }

    private void updateClientInSDK(OctaneConnection configuration) {
        OctaneClient currentClient = OctaneSDK.getClientByInstanceId(configuration.getId());
        OctaneUrlParser octaneUrlParser = OctaneUrlParser.parse(configuration.getLocation());

        OctaneConfiguration config = currentClient.getConfigurationService().getCurrentConfiguration();
        config.setSharedSpace(octaneUrlParser.getSharedSpace());
        config.setUrl(octaneUrlParser.getLocation());
        config.setClient(configuration.getClientId());
        config.setSecret(configuration.getClientSecret());
    }

    private void removeClientFromSDK(String uuid) {
        OctaneClient currentClient = OctaneSDK.getClientByInstanceId(uuid);
        OctaneSDK.removeClient(currentClient);
    }

    public void initSdkClients(PluginSettingsFactory settingsFactory) {
        log.info("");
        log.info("");
        log.info("***********************************************************************************");
        log.info("****************************Enabling plugin - init SDK Clients*********************");
        log.info("***********************************************************************************");

        try {
            String pluginVersion = ComponentLocator.getComponent(PluginAccessor.class).getPlugin(BambooPluginServices.PLUGIN_KEY).getPluginInformation().getVersion();
            log.info("Plugin version : " + pluginVersion);
        } catch (Exception e) {
            log.error("Failed to get plugin version : " + e.getMessage());
        }

        this.settingsFactory = settingsFactory;
        try {
            PluginSettings settings = settingsFactory.createGlobalSettings();
            if (settings.get(OctaneConnectionManager.CONFIGURATIONS_LIST) == null) {
                octaneConnectionCollection = new OctaneConnectionCollection();

                ///try to upgrade configuration from previous version
                OctaneConnection octaneConnection = PreviousVersionsConfigurationHelper_1_7.tryReadConfiguration(settingsFactory);
                if (octaneConnection != null) {
                    addConfiguration(octaneConnection);
                    //don't remove previous version,to give possibility to user to do revert to previous version
                    //PreviousVersionsConfigurationHelper_1_7.removePreviousVersion(settingsFactory);
                }
            } else {
                String confStr = ((String) settings.get(OctaneConnectionManager.CONFIGURATIONS_LIST));
                octaneConnectionCollection = JsonHelper.deserialize(confStr, OctaneConnectionCollection.class);
                for (OctaneConnection c : octaneConnectionCollection.getOctaneConnections()) {
                    try {
                        addSdkClient(c);
                    } catch (Exception e) {
                        log.info(String.format("Failed to add client '%s' to sdk : %s", c.getId(), e.getMessage()));
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to initSdkClients : " + e.getMessage(), e);
        }
    }

    public void removeClients() {
        log.info("Disabling plugin - removing SDK clients");
        OctaneSDK.getClients().forEach(c -> OctaneSDK.removeClient(c));
    }

    public static boolean hasActiveClients() {
        return OctaneSDK.hasClients();
    }
}
