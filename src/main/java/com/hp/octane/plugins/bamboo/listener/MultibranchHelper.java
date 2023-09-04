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

import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutableChainBranch;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.MultiBranchType;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.plugins.bamboo.octane.SDKBasedLoggerProvider;
import org.apache.logging.log4j.Logger;

import java.util.Set;

import static com.hp.octane.plugins.bamboo.listener.BaseListener.CONVERTER;

public class MultibranchHelper {

    private static CachedPlanManager cachedPlanManager;
    private static PlanManager planManager;
    protected static final Logger log = SDKBasedLoggerProvider.getLogger(OctanePostChainAction.class);


    private static CachedPlanManager getCachedPlanManager() {
        if (cachedPlanManager == null) {
            cachedPlanManager = ComponentLocator.getComponent(CachedPlanManager.class);
        }
        return cachedPlanManager;
    }

    private static PlanManager getPlanManager() {
        if (planManager == null) {
            planManager = ComponentLocator.getComponent(PlanManager.class);
        }
        return planManager;
    }

    public static void enrichMultiBranchEventForJob(BuildContext buildContext, CIEvent ciEvent) {
        try {
            PlanKey parentKey = buildContext.getParentBuildContext().getPlanResultKey().getPlanKey();
            ImmutablePlan plan = getCachedPlanManager().getPlanByKey(parentKey);
            boolean isMultiBranchChild = plan != null && plan instanceof ImmutableChainBranch;

            if (isMultiBranchChild) {
                ciEvent.setSkipValidation(true);
            }
        } catch (Exception e) {
            log.error("Failed to enrichMultiBranchEventForJob : " + e.getMessage());
        }
    }

    public static void enrichMultiBranchEvent(ImmutableChain chain, CIEvent ciEvent) {
        if (chain instanceof ImmutableChainBranch) {
            ciEvent.setParentCiId(chain.getMaster().getPlanKey().toString())
                    .setMultiBranchType(MultiBranchType.MULTI_BRANCH_CHILD)
                    .setSkipValidation(true);
        }
    }

    public static void onChainDeleted(PlanKey planKey) {
        Plan plan = getPlanManager().getPlanByKey(planKey);
        if (plan instanceof ImmutableChainBranch) {
            CIEvent cievent = CONVERTER.getEventWithDetails(planKey.toString(), CIEventType.DELETED);
            OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(cievent));
        }
    }


}
