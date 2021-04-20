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

import com.atlassian.bamboo.chains.StageExecution;
import com.atlassian.bamboo.chains.plugins.PreJobAction;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.scm.SCMData;
import com.hp.octane.plugins.bamboo.octane.DefaultOctaneConverter;
import com.hp.octane.plugins.bamboo.octane.MqmResultsHelper;
import com.hp.octane.plugins.bamboo.octane.SDKBasedLoggerProvider;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

public class OctanePreJobAction extends BaseListener implements PreJobAction {

	private static final Logger LOG = SDKBasedLoggerProvider.getLogger(OctanePreJobAction.class);

	public void execute(StageExecution paramStageExecution, BuildContext buildContext) {
		if(!OctaneConnectionManager.hasActiveClients()){
			return;
		}

		PlanResultKey resultKey = buildContext.getPlanResultKey();

		CIEventCause parentReason = CONVERTER.getCause(buildContext.getTriggerReason());
		String parentJobId = buildContext.getParentBuildContext().getPlanResultKey().getPlanKey().getKey();
		CIEventCause cause = CONVERTER.getUpstreamCause(
				buildContext.getParentBuildIdentifier().getPlanResultKey().getKey(),
				parentJobId,
				parentReason);

		//create and send started event
		CIEvent event = CONVERTER.getEventWithDetails(
				resultKey.getPlanKey().getKey(),
				resultKey.getKey(),
				buildContext.getShortName(),
				CIEventType.STARTED,
				System.currentTimeMillis(),
				paramStageExecution.getChainExecution().getAverageDuration(),
				Collections.singletonList(cause),
				String.valueOf(resultKey.getBuildNumber()),
				PhaseType.INTERNAL);

		MultibranchHelper.enrichMultiBranchEventForJob(buildContext,event);

		ParametersHelper.addParametersToEvent(event, buildContext);
		OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(event));

		//create and send SCM event
		SCMData scmData = CONVERTER.getScmData(buildContext);
		if (scmData != null) {
			List<SCMData> scmDataList = Collections.singletonList(scmData);
			InputStream is = DefaultOctaneConverter.getDTOFactory().dtoCollectionToJsonStream(scmDataList);
			Path targetFilePath = MqmResultsHelper.getScmDataFilePath(resultKey);
			LOG.info("Generating scm data file for " + resultKey.getKey() + " (" + scmData.getCommits().size() + " commits): " + targetFilePath);
			MqmResultsHelper.saveStreamToFile(is, resultKey, targetFilePath);

			OctaneSDK.getClients().forEach(client -> client.getSCMDataService().enqueueSCMData(resultKey.getPlanKey().getKey(), resultKey.getKey(), scmData, parentJobId));
		}
	}
}
