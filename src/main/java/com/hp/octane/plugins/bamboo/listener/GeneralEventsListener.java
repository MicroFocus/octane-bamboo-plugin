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

public class GeneralEventsListener extends BaseListener {

    @EventListener
    public void onChainDeleted(ChainDeletedEvent event) {
        MultibranchHelper.onChainDeleted(event.getPlanKey());
    }

    @EventListener
    public void onPluginEnabled(PluginEnabledEvent event) {

    }

    @EventListener
    public void onPluginDisabling(PluginDisablingEvent event) {

    }

}
