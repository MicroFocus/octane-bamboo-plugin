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
package com.hp.octane.plugins.bamboo.listener;

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.plugins.PreChainAction;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.MultiBranchType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class OctanePreChainAction extends BaseListener implements PreChainAction {

    @Override
    public void execute(@NotNull ImmutableChain chain, @NotNull ChainExecution chainExecution) throws InterruptedException, Exception {
        if (!OctaneConnectionManager.hasActiveClients()) {
            return;
        }

        log.info("Executing chain " + chain.getName() + " build id "
                + chainExecution.getBuildIdentifier().getPlanResultKey().getKey() + " build number "
                + chainExecution.getBuildIdentifier().getBuildNumber());
        CIEvent event = CONVERTER.getEventWithDetails(chainExecution.getPlanResultKey().getPlanKey().getKey(),
                chainExecution.getBuildIdentifier().getPlanResultKey().getKey(), chain.getName(), CIEventType.STARTED,
                chainExecution.getStartTime() != null ? chainExecution.getStartTime().getTime() : System.currentTimeMillis(),
                chainExecution.getAverageDuration(),
                Collections.singletonList(CONVERTER.getCause(chainExecution.getTriggerReason())),
                String.valueOf(chainExecution.getBuildIdentifier().getBuildNumber()),
                PhaseType.INTERNAL);

        MultibranchHelper.enrichMultiBranchEvent(chain, event);

        BuildContext buildContext = (BuildContext) chainExecution.getBuildIdentifier();
        ParametersHelper.addParametersToEvent(event, buildContext);
        OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(event));
    }

}
