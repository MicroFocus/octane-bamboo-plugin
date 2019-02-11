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

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.hp.octane.integrations.OctaneClient;
import com.hp.octane.integrations.OctaneConfiguration;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.plugins.bamboo.api.OctaneConfigurationKeys;
import com.hp.octane.plugins.bamboo.octane.BambooPluginServices;
import com.hp.octane.plugins.bamboo.octane.MqmProject;
import com.hp.octane.plugins.bamboo.octane.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;
import java.util.UUID;

public class PluginStatusChangesListener implements InitializingBean, DisposableBean {
	private static final Logger logger = LoggerFactory.getLogger(PluginStatusChangesListener.class);

	private final PluginSettingsFactory settingsFactory;

	public PluginStatusChangesListener(PluginSettingsFactory settingsFactory) {
		this.settingsFactory = settingsFactory;
	}

	@Override
	public void destroy() throws Exception {
		logger.info("Destroying plugin - removing SDK clients");
		List<OctaneClient> clients = OctaneSDK.getClients();
		for (OctaneClient client : clients) {
			OctaneSDK.removeClient(client);
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		logger.info("Init ALM Octane plugin - creating SDK clients");
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

		if(octaneUrl.isEmpty() && accessKey.isEmpty() && apiSecret.isEmpty()){
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
}
