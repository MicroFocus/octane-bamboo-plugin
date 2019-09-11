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

import com.atlassian.bamboo.build.artifact.ArtifactLink;
import com.atlassian.bamboo.build.artifact.ArtifactLinkManager;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.artifact.ArtifactContext;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.ResultsSummaryManager;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.events.PostBuildCompletedEvent;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.plugins.bamboo.octane.*;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class OctanePostChainAction extends BaseListener implements PostChainAction {

    private static final Logger LOG = SDKBasedLoggerProvider.getLogger(OctanePostChainAction.class);

    private static Set<String> testResultExpected = new HashSet<>();

    public static void onJobCompleted(PostBuildCompletedEvent event) {
        if (!OctaneConnectionManager.hasActiveClients()) {
            return;
        }
        PlanKey planKey = event.getPlanKey();
        PlanResultKey planResultKey = event.getPlanResultKey();

        CurrentBuildResult results = event.getContext().getBuildResult();
        boolean hasTests = (results.getFailedTestResults() != null && !results.getFailedTestResults().isEmpty()) ||
                (results.getSkippedTestResults() != null && !results.getSkippedTestResults().isEmpty()) ||
                (results.getSuccessfulTestResults() != null && !results.getSuccessfulTestResults().isEmpty());
        LOG.info(planResultKey.toString() + " : onJobCompleted, hasTests = " + hasTests);

        CIEventCause cause = CONVERTER.getCauseWithDetails(
                event.getContext().getParentBuildIdentifier().getPlanResultKey().getKey(),
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
        ciEvent.setTestResultExpected(hasTests);

        ParametersHelper.addParametersToEvent(ciEvent, event.getContext());
        OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(ciEvent));

        if (hasTests) {
            try {
                saveJobTestResults(event);
            } catch (Exception e) {
                LOG.error("Failed to saveJobTestResults : " + e.getMessage());
            }
            testResultExpected.add(event.getContext().getParentBuildContext().getPlanResultKey().getKey());
            OctaneSDK.getClients().forEach(client ->
                    client.getTestsService().enqueuePushTestsResult(planKey.getKey(), planResultKey.getKey()));
        }
    }

    private static void saveJobTestResults(PostBuildCompletedEvent event) throws IOException {
        PlanResultKey planResultKey = event.getPlanResultKey();
        ArtifactContext artifactContext = event.getContext().getArtifactContext();


        boolean hasTestResultsArtifact = artifactContext.getPublishingResults()
                .stream().filter(r -> r.getArtifactDefinitionContext().getName().equals(OctaneConstants.MQM_RESULT_ARTIFACT_NAME)).findFirst().isPresent();
        if (hasTestResultsArtifact) {
            LOG.info(planResultKey.toString() + " : test result artifact found");
            ResultsSummaryManager resultsSummaryManager = ComponentLocator.getComponent(ResultsSummaryManager.class);
            ResultsSummary rs = resultsSummaryManager.getResultsSummary(planResultKey);
            ArtifactLinkManager artifactLinkManager = ComponentLocator.getComponent(ArtifactLinkManager.class);
            Collection<ArtifactLink> links = artifactLinkManager.getArtifactLinks(rs, null);
            ArtifactLink link = links.stream().filter(l -> l.getArtifact().getLabel().equals(OctaneConstants.MQM_RESULT_ARTIFACT_NAME)).findFirst().orElse(null);
            if (link != null) {
                File buildResultDirectory = new File(MqmResultsHelper.getBuildResultDirectory(planResultKey.getPlanKey()),OctaneConstants.MQM_RESULT_FOLDER);
                LOG.info(planResultKey.toString() + " : Generating test result from artifacts. Copy artifacts to " + buildResultDirectory.getAbsolutePath());
                ArtifactsHelper.copyArtifactTo(buildResultDirectory, link.getArtifact());
            } else {
                new RuntimeException(OctaneConstants.MQM_RESULT_ARTIFACT_NAME + " artifact is not found");
            }
        } else {
            LOG.info(planResultKey.toString() + " : Generating test result from context");
            InputStream is = MqmResultsHelper.generateTestResultStream(event.getContext(), planResultKey.getKey(), Integer.toString(planResultKey.getBuildNumber()));
            MqmResultsHelper.saveToTestResultFile(is, planResultKey);
        }

        File testResultFile = MqmResultsHelper.getMqmResultFilePath(planResultKey).toFile();
        boolean testResultFileExist = testResultFile.exists();
        LOG.info(planResultKey.toString() + " : text result file created=" + testResultFileExist + ". Path is " + testResultFile);
    }

    public void execute(Chain chain, ChainResultsSummary chainResultsSummary, ChainExecution chainExecution) {
        if (!OctaneConnectionManager.hasActiveClients()) {
            return;
        }
        LOG.info("Chain " + chainExecution.getBuildIdentifier().getPlanResultKey() + " completed with result " + chainResultsSummary.getBuildState().toString());
        if (MultibranchHelper.isMultiBranchParent(chain)) {
            LOG.info(String.format("Chain '%s(%s)' is recognized as multi-branch parent. Finish event is ignored.", chain.getPlanKey(), chain.getName()));
            //don't sent event on multibranch parent
            return;
        }

        List<CIEventCause> causes = new ArrayList<>();
        CIEvent ciEvent = CONVERTER.getEventWithDetails(
                chain.getPlanKey().getKey(),
                chainExecution.getBuildIdentifier().getPlanResultKey().getKey(),
                chain.getName(),
                CIEventType.FINISHED,
                chainExecution.getStartTime() != null ? chainExecution.getStartTime().getTime() : chainExecution.getQueueTime().getTime(),
                chainResultsSummary.getDuration(),
                causes,
                String.valueOf(chainExecution.getBuildIdentifier().getBuildNumber()),
                chainResultsSummary.getBuildState(),
                chainResultsSummary.getProcessingDuration(),//System.currentTimeMillis(),
                PhaseType.INTERNAL);
        if (chainExecution.isStopRequested()) {
            ciEvent.setResult(CIBuildResult.ABORTED);
        }

        //handle setTestResultExpected
        String key = chainExecution.getPlanResultKey().getKey();
        ciEvent.setTestResultExpected(testResultExpected.contains(key));
        testResultExpected.remove(key);

        MultibranchHelper.enrichMultiBranchEvent(chain, ciEvent);

        com.atlassian.bamboo.v2.build.BuildContext buildContext = (BuildContext) chainExecution.getBuildIdentifier();
        ParametersHelper.addParametersToEvent(ciEvent, buildContext);
        OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(ciEvent));
    }
}
