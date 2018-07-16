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

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainStage;
import com.atlassian.bamboo.chains.plugins.PreChainAction;
import com.atlassian.bamboo.plan.Plan;
import com.atlassian.bamboo.plan.PlanManager;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.plugins.bamboo.octane.uft.UftManager;

import java.util.ArrayList;
import java.util.List;

public class OctanePreChainAction extends BaseListener implements PreChainAction {

	public void execute(Chain chain, ChainExecution chainExecution) throws Exception {

		tryCompleteUftPlan(chain);

		log.info("Executing chain " + chain.getName() + " build id "
				+ chainExecution.getBuildIdentifier().getBuildResultKey() + " build number "
				+ chainExecution.getBuildIdentifier().getBuildNumber());
		List<CIEventCause> causes = new ArrayList<CIEventCause>();
		CIEvent event = CONVERTER.getEventWithDetails(chainExecution.getPlanResultKey().getPlanKey().getKey(),
				chainExecution.getBuildIdentifier().getBuildResultKey(), chain.getName(), CIEventType.STARTED,
				chainExecution.getStartTime() != null ? chainExecution.getStartTime().getTime() : System.currentTimeMillis(),
				chainExecution.getAverageDuration(), causes,
				String.valueOf(chainExecution.getBuildIdentifier().getBuildNumber()),
				PhaseType.INTERNAL);

		OctaneSDK.getInstance().getEventsService().publishEvent(event);
	}

	/**
	 * Workaround for UFT integration
	 * Some action in creation of UFT related plan cannot be created without session (for example creation of artifact definition)
	 * This method check if chain is UFT related and send it to UFT manager
	 * @param chain
	 */
	private void tryCompleteUftPlan(Chain chain) {
		try {
			if (chain.getPlanKey().getKey().startsWith(UftManager.PROJECT_KEY+"-" + UftManager.DISCOVERY_KEY_PREFIX)) {
				PlanManager planManager = ComponentLocator.getComponent(PlanManager.class);
				for (ChainStage chainStage : chain.getStages()) {
					for (Job job : chainStage.getJobs()) {
						Plan plan = planManager.getPlanByKey(job.getPlanKey());
						UftManager.getInstance().completeDiscoveryJob(plan);
					}
				}
			}
		}catch (Exception e){
			log.error("Fail in completing UFT-related chain : " + e.getMessage());
		}
	}

}
