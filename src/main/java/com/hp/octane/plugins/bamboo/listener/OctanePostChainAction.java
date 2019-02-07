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
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.event.HibernateEventListenerAspect;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.results.tests.TestResults;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.events.BuildContextEvent;
import com.atlassian.bamboo.v2.build.events.PostBuildCompletedEvent;
import com.atlassian.event.api.EventListener;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.tests.*;
import com.hp.octane.plugins.bamboo.octane.HPRunnerType;
import com.hp.octane.plugins.bamboo.api.OctaneConfigurationKeys;
import com.hp.octane.plugins.bamboo.octane.HPRunnerTypeUtils;
import com.hp.octane.plugins.bamboo.octane.uft.UftManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class OctanePostChainAction extends BaseListener implements PostChainAction {
	private final PluginSettingsFactory settingsFactory;

	public OctanePostChainAction(PluginSettingsFactory settingsFactory) {
		this.settingsFactory = settingsFactory;
	}

	@EventListener
	@HibernateEventListenerAspect
	public void handle(BuildContextEvent event) {
		// TODO move this listener into OctanePostJobAction
		log.info("Build context event " + event.getClass().getSimpleName());
		if (event instanceof PostBuildCompletedEvent) {
			HPRunnerType runnerType = HPRunnerTypeUtils.getHPRunnerType(event.getContext().getRuntimeTaskDefinitions());
			CurrentBuildResult results = event.getContext().getBuildResult();
			PlanKey planKey = event.getPlanKey();
			PlanResultKey planResultKey = event.getPlanResultKey();
			{
				CIEventCause cause = CONVERTER.getCauseWithDetails(
						event.getContext().getParentBuildIdentifier().getBuildResultKey(),
						event.getContext().getParentBuildContext().getPlanResultKey().getPlanKey().getKey(), "admin");

				CIEvent ciEvent = CONVERTER.getEventWithDetails(
						planResultKey.getPlanKey().getKey(),
						planResultKey.getKey(),
						event.getContext().getShortName(),
						CIEventType.FINISHED,
						event.getContext().getCurrentResult().getTasksStartDate().getTime(),
						100,
						Arrays.asList(cause),
						String.valueOf(planResultKey.getBuildNumber()),
						event.getContext().getCurrentResult().getBuildState(),
						(event.getTimestamp() - event.getContext().getCurrentResult().getTasksStartDate().getTime()),
						PhaseType.INTERNAL);
				if (HPRunnerType.UFT.equals(runnerType)) {
					UftManager.getInstance().addUftParametersToEvent(ciEvent, event.getContext());
				}

				OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(ciEvent));
			}

			if ((results.getFailedTestResults() != null && !results.getFailedTestResults().isEmpty()) ||
					(results.getSkippedTestResults() != null && !results.getSkippedTestResults().isEmpty()) ||
					(results.getSuccessfulTestResults() != null && !results.getSuccessfulTestResults().isEmpty())) {
				OctaneSDK.getClients().forEach(client ->
						client.getTestsService().enqueuePushTestsResult(planKey.getKey(), planResultKey.getKey()));
			}
		}
	}

	public void execute(Chain chain, ChainResultsSummary chainResultsSummary, ChainExecution chainExecution) throws Exception {
		log.info("Chain " + chain.getName() + " completed with result "
				+ chainResultsSummary.getBuildState().toString());
		log.info("Build identifier " + chainExecution.getBuildIdentifier().getBuildResultKey() + " chain id "
				+ chain.getKey());
		List<CIEventCause> causes = new ArrayList<>();
		CIEvent ciEvent = CONVERTER.getEventWithDetails(
				chain.getPlanKey().getKey(),
				chainExecution.getBuildIdentifier().getBuildResultKey(),
				chain.getName(),
				CIEventType.FINISHED,
				chainExecution.getStartTime() != null ? chainExecution.getStartTime().getTime() : chainExecution.getQueueTime().getTime(),
				chainResultsSummary.getDuration(),
				causes,
				String.valueOf(chainExecution.getBuildIdentifier().getBuildNumber()),
				chainResultsSummary.getBuildState(),
				chainResultsSummary.getProcessingDuration(),//System.currentTimeMillis(),
				PhaseType.INTERNAL);

//		event.setResult((chainResultsSummary.getBuildState() == BuildState.SUCCESS) ? CIBuildResult.SUCCESS : CIBuildResult.FAILURE);
		// TODO pushing finished type event with null duration results in http
		// 400, octane rest api could be more verbose to specify the reason
//		event.setDuration(System.currentTimeMillis() - chainExecution.getQueueTime().getTime());
		OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(ciEvent));
	}
}
