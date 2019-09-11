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

public class GeneralEventsListener extends BaseListener {

    private PluginSettingsFactory settingsFactory;

    public GeneralEventsListener(PluginSettingsFactory settingsFactory) {
        this.settingsFactory = settingsFactory;
    }

    @EventListener
    public void onChainDeleted(ChainDeletedEvent event) {
        if (!OctaneConnectionManager.hasActiveClients()) {
            return;
        }
        MultibranchHelper.onChainDeleted(event.getPlanKey());
    }

    @EventListener
    @HibernateEventListenerAspect
    public void onJobCompleted(PostBuildCompletedEvent event) {
        if (!OctaneConnectionManager.hasActiveClients()) {
            return;
        }
        log.info("on job completed " + event.getPlanKey().getKey());
        OctanePostChainAction.onJobCompleted(event);
    }

    @EventListener
    public void onPluginEnabled(PluginEnabledEvent event) {
        if (BambooPluginServices.PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            OctaneConnectionManager.getInstance().initSdkClients(settingsFactory);
        }
    }

    @EventListener
    public void onPluginDisabling(PluginDisablingEvent event) {
        if (BambooPluginServices.PLUGIN_KEY.equals(event.getPlugin().getKey())) {
            OctaneConnectionManager.getInstance().removeClients();
        }
    }
}
