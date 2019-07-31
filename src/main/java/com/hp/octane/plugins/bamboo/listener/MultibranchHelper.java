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

import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChainBranch;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.MultiBranchType;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;

import java.util.Set;

import static com.hp.octane.plugins.bamboo.listener.BaseListener.CONVERTER;

public class MultibranchHelper {

    private static CachedPlanManager cachedPlanManager;
    private static PlanManager planManager;


    public static boolean isMultibranch(ImmutableTopLevelPlan plan) {
        Set<PlanKey> branchKeys = getCachedPlanManager().getBranchKeysOfChain(plan.getPlanKey());
        return !branchKeys.isEmpty();
    }

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

    public static void enrichMultiBranchParentPipeline(ImmutableTopLevelPlan plan, PipelineNode pipelineNode) {
        if (isMultibranch(plan)) {
            pipelineNode.setMultiBranchType(MultiBranchType.MULTI_BRANCH_PARENT);
        }
    }

    public static void enrichMultibranchEvent(Chain chain, CIEvent ciEvent) {
        if (chain instanceof ImmutableChainBranch) {
            ciEvent.setParentCiId(chain.getMaster().getPlanKey().toString()).setMultiBranchType(MultiBranchType.MULTI_BRANCH_CHILD);
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
