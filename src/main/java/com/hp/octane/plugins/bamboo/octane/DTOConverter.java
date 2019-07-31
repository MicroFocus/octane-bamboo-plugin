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

package com.hp.octane.plugins.bamboo.octane;

import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.chains.cache.ImmutableChainStage;
import com.atlassian.bamboo.plan.PlanIdentifier;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.bamboo.results.tests.TestResults;
import com.atlassian.bamboo.resultsummary.ImmutableResultsSummary;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.configuration.CIProxyConfiguration;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.general.CIJobsList;
import com.hp.octane.integrations.dto.general.CIServerInfo;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.pipelines.PipelinePhase;
import com.hp.octane.integrations.dto.scm.SCMData;
import com.hp.octane.integrations.dto.snapshots.SnapshotNode;
import com.hp.octane.integrations.dto.tests.BuildContext;
import com.hp.octane.integrations.dto.tests.TestRun;
import com.hp.octane.integrations.dto.tests.TestRunResult;

import java.util.List;

public interface DTOConverter {
	PipelineNode getRootPipelineNodeFromTopLevelPlan(ImmutableTopLevelPlan plan);

	PipelineNode getPipelineNodeFromJob(ImmutableJob job);

	PipelinePhase getPipelinePhaseFromStage(ImmutableChainStage stage);

	CIProxyConfiguration getProxyCconfiguration(String proxyServer, int proxyPort, String proxyUser,
	                                            String proxyPassword);

	CIServerInfo getServerInfo(String baseUrl, String bambooVersion);

	SnapshotNode getSnapshot(ImmutableTopLevelPlan plan, ImmutableResultsSummary summary);

	CIJobsList getRootJobsList(List<ImmutableTopLevelPlan> toplevels);

	String getCiId(PlanIdentifier identifier);

	TestRun getTestRunFromTestResult(com.atlassian.bamboo.v2.build.BuildContext buildContext, HPRunnerType runnerType, TestResults currentTestResult, TestRunResult result, long startTime);


	CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType,
								long startTime, long estimatedDuration, List<CIEventCause> causes, String number, BuildState buildState, Long currnetTime, PhaseType phaseType);

	CIEvent getEventWithDetails(String project, CIEventType eventType);

	CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType,
								long startTime, long estimatedDuration, List<CIEventCause> causes, String number, PhaseType phaseType);

	CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType,
								long startTime, long estimatedDuration, List<CIEventCause> causes, String number, SCMData scmData, PhaseType phaseType);


	CIEventCause getCauseWithDetails(String buildCiId, String project, String user);

	BuildContext getBuildContext(String instanceId, String identifier, String build);

	SCMData getScmData(com.atlassian.bamboo.v2.build.BuildContext buildContext);
}
