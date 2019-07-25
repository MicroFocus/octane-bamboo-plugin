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

import com.atlassian.bamboo.event.ChainDeletedEvent;
import com.atlassian.event.api.EventListener;
import com.atlassian.plugin.event.events.PluginDisablingEvent;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.hp.octane.integrations.OctaneClient;
import com.hp.octane.integrations.OctaneConfiguration;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.plugins.bamboo.api.OctaneConfigurationKeys;
import com.hp.octane.plugins.bamboo.octane.BambooPluginServices;
import com.hp.octane.plugins.bamboo.octane.MqmProject;
import com.hp.octane.plugins.bamboo.octane.utils.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class GeneralEventsListener extends BaseListener {

    private static final Logger logger = LogManager.getLogger(GeneralEventsListener.class);
    private PluginSettingsFactory settingsFactory;
    private static volatile boolean sysParamConfigured = false;

    public GeneralEventsListener(PluginSettingsFactory settingsFactory) {
        this.settingsFactory = settingsFactory;
        initOctaneAllowedStorageParameter();
    }

    @EventListener
    public void onChainDeleted(ChainDeletedEvent event) {
        MultibranchHelper.onChainDeleted(event.getPlanKey());
    }

    @EventListener
    public void onPluginEnabled(PluginEnabledEvent event) {
        if (BambooPluginServices.PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            initClients();
        }
    }

    @EventListener
    public void onPluginDisabling(PluginDisablingEvent event) {
        if (BambooPluginServices.PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            removeClients();
        }
    }

    private void initClients() {
        logger.info("");
        logger.info("");
        logger.info("***********************************************************************************");
        logger.info("****************************Enabling plugin - init SDK Clients*********************");
        logger.info("***********************************************************************************");
        PluginSettings settings = settingsFactory.createGlobalSettings();
        String uuid, octaneUrl, accessKey, apiSecret, userName;
        if (settings.get(OctaneConfigurationKeys.UUID) != null) {
            uuid = String.valueOf(settings.get(OctaneConfigurationKeys.UUID));
        } else {
            // generate new UUID
            uuid = UUID.randomUUID().toString();
            settings.put(OctaneConfigurationKeys.UUID, uuid);
        }
        octaneUrl = settings.get(OctaneConfigurationKeys.OCTANE_URL) != null ?
                String.valueOf(settings.get(OctaneConfigurationKeys.OCTANE_URL)) : "";
        accessKey = settings.get(OctaneConfigurationKeys.ACCESS_KEY) != null ?
                String.valueOf(settings.get(OctaneConfigurationKeys.ACCESS_KEY)) : "";
        apiSecret = settings.get(OctaneConfigurationKeys.API_SECRET) != null ?
                String.valueOf(settings.get(OctaneConfigurationKeys.API_SECRET)) : "";

        if (octaneUrl.isEmpty() && accessKey.isEmpty() && apiSecret.isEmpty()) {
            //empty configuration. Clean plugin installation
            return;
        }
        MqmProject project = Utils.parseUiLocation(octaneUrl);
        OctaneConfiguration octaneConfiguration = new OctaneConfiguration(uuid,
                project.getLocation(),
                project.getSharedSpace());
        octaneConfiguration.setClient(accessKey);
        octaneConfiguration.setSecret(apiSecret);
        OctaneSDK.addClient(octaneConfiguration, BambooPluginServices.class);
    }

    private void removeClients() {
        logger.info("Disabling plugin - removing SDK clients");
        List<OctaneClient> clients = OctaneSDK.getClients();
        for (OctaneClient client : clients) {
            OctaneSDK.removeClient(client);
        }
    }

    private static void initOctaneAllowedStorageParameter() {
        try {
            if (!sysParamConfigured) {
                System.setProperty("octaneAllowedStorage", BambooPluginServices.getAllowedStorageFile().getAbsolutePath() + File.separator);
                sysParamConfigured = true;
            }
        } catch (Throwable e) {
            logger.error("Failed to initOctaneAllowedStorageParameter : " + e.getMessage());
        }
    }

}
