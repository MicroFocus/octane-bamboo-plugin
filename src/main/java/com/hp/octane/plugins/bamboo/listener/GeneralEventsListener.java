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
import com.atlassian.bamboo.event.HibernateEventListenerAspect;
import com.atlassian.bamboo.v2.build.events.PostBuildCompletedEvent;
import com.atlassian.event.api.EventListener;
import com.atlassian.plugin.event.events.PluginDisablingEvent;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.hp.octane.plugins.bamboo.octane.BambooPluginServices;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class GeneralEventsListener extends BaseListener {

    private static final Logger logger = LogManager.getLogger(GeneralEventsListener.class);
    private PluginSettingsFactory settingsFactory;
    private static volatile boolean sysParamConfigured = false;

    public GeneralEventsListener(PluginSettingsFactory settingsFactory) {
        this.settingsFactory = settingsFactory;
    }

    @EventListener
    public void onChainDeleted(ChainDeletedEvent event) {
        MultibranchHelper.onChainDeleted(event.getPlanKey());
    }

    @EventListener
    @HibernateEventListenerAspect
    public void onJobCompleted(PostBuildCompletedEvent event) {
        log.info("on job completed " + event.getPlanKey().getKey());
        OctanePostChainAction.onJobCompleted(event);
    }

    @EventListener
    public void onPluginEnabled(PluginEnabledEvent event) {
        if (BambooPluginServices.PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            initOctaneAllowedStorageParameter();
            OctaneConnectionManager.getInstance().init(settingsFactory);
        }
    }

    @EventListener
    public void onPluginDisabling(PluginDisablingEvent event) {
        if (BambooPluginServices.PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            OctaneConnectionManager.getInstance().removeClients();
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
