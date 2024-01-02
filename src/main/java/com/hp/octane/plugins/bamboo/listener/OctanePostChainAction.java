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

import com.atlassian.bamboo.build.artifact.ArtifactLink;
import com.atlassian.bamboo.build.artifact.ArtifactLinkManager;
import com.atlassian.bamboo.chains.ChainExecution;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.chains.plugins.PostChainAction;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.artifact.ArtifactContext;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
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
import com.hp.octane.integrations.services.entities.EntitiesService;
import com.hp.octane.integrations.uft.UftTestDispatchUtils;
import com.hp.octane.integrations.uft.items.JobRunContext;
import com.hp.octane.integrations.uft.items.UftTestDiscoveryResult;
import com.hp.octane.plugins.bamboo.octane.*;
import com.hp.octane.plugins.bamboo.octane.uft.UftDiscoveryTask;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class OctanePostChainAction extends BaseListener implements PostChainAction {

    private static final Logger LOG = SDKBasedLoggerProvider.getLogger(OctanePostChainAction.class);

    private static Set<String> testResultExpected = new HashSet<>();

    private static final String TEST_RESULT_PUBLISHER_TASK = "com.hpe.adm.octane.ciplugins.bamboo-ci-plugin:octanetestresultpublisher",
    CUCUMBER_TEST_RESULT_PUBLISHER_TASK = "com.hpe.adm.octane.ciplugins.bamboo-ci-plugin:octanecucumber";
    private static final Set<String> octaneResultReporterTask = new HashSet(Arrays.asList(TEST_RESULT_PUBLISHER_TASK,CUCUMBER_TEST_RESULT_PUBLISHER_TASK));

    private static final String DISCOVERY_JOB_ARTIFACT_NAME = "UFT discovery result";

    public static void onJobCompleted(PostBuildCompletedEvent event) {
        if (!OctaneConnectionManager.hasActiveClients()) {
            return;
        }
        PlanKey planKey = event.getPlanKey();
        PlanResultKey planResultKey = event.getPlanResultKey();

        ArtifactContext artifactContext = event.getContext().getArtifactContext();
        boolean hasTestResultsArtifact = artifactContext.getPublishingResults().stream()
                .anyMatch(r -> r.getArtifactDefinitionContext().getName().equals(OctaneConstants.MQM_RESULT_ARTIFACT_NAME));
        boolean hasDefinedOctanePublisherTask = event.getContext().getRuntimeTaskDefinitions().stream()
                .anyMatch(task -> octaneResultReporterTask.contains(task.getPluginKey().toLowerCase()));

        CurrentBuildResult results = event.getContext().getBuildResult();
        boolean hasTests =
                (hasTestResultsArtifact && hasDefinedOctanePublisherTask) ||
                (results.getFailedTestResults() != null && !results.getFailedTestResults().isEmpty()) ||
                (results.getSkippedTestResults() != null && !results.getSkippedTestResults().isEmpty()) ||
                (results.getSuccessfulTestResults() != null && !results.getSuccessfulTestResults().isEmpty());
        LOG.info(planResultKey.toString() + " : onJobCompleted, hasTests = " + hasTests);

        CIEventCause parentReason = CONVERTER.getCause(event.getContext().getTriggerReason());
        String parentId = event.getContext().getParentBuildContext().getPlanResultKey().getPlanKey().getKey();
        CIEventCause cause = CONVERTER.getUpstreamCause(
                event.getContext().getParentBuildIdentifier().getPlanResultKey().getKey(),
                parentId,
                parentReason);

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
        MultibranchHelper.enrichMultiBranchEventForJob( event.getContext(),ciEvent);

        ParametersHelper.addParametersToEvent(ciEvent, event.getContext());
        OctaneSDK.getClients().forEach(client -> client.getEventsService().publishEvent(ciEvent));

        if (hasTests) {
            try {
                saveJobTestResults(event, hasTestResultsArtifact && hasDefinedOctanePublisherTask);
            } catch (Exception e) {
                LOG.error("Failed to saveJobTestResults : " + e.getMessage());
            }
            testResultExpected.add(event.getContext().getParentBuildContext().getPlanResultKey().getKey());
            OctaneSDK.getClients().forEach(client ->
                    client.getTestsService().enqueuePushTestsResult(planKey.getKey(), planResultKey.getKey(), parentId));
        }

        handleDiscoveryResults(event);

    }

    private static void handleDiscoveryResults(PostBuildCompletedEvent event){
        String discoveryHasClients = event.getContext().getBuildResult().getCustomBuildData().get(UftDiscoveryTask.UFT_DISCOVERY_HAS_CLIENTS);
        boolean hasDiscoveryTask = event.getContext().getArtifactContext().getPublishingResults().stream()
                .anyMatch(r -> r.getArtifactDefinitionContext().getName().equals(DISCOVERY_JOB_ARTIFACT_NAME));
        try {
            if (hasDiscoveryTask && StringUtils.isNoneEmpty(discoveryHasClients) && "false".equals(discoveryHasClients)) {
                PlanResultKey planResultKey = event.getPlanResultKey();
                ResultsSummaryManager resultsSummaryManager = ComponentLocator.getComponent(ResultsSummaryManager.class);
                ResultsSummary rs = resultsSummaryManager.getResultsSummary(planResultKey);
                ArtifactLinkManager artifactLinkManager = ComponentLocator.getComponent(ArtifactLinkManager.class);
                Collection<ArtifactLink> links = artifactLinkManager.getArtifactLinks(rs, null);
                ArtifactLink link = links.stream().filter(l -> l.getArtifact().getLabel().equals(DISCOVERY_JOB_ARTIFACT_NAME)).findFirst().orElse(null);
                if (link != null) {
                    LOG.info(planResultKey + " : Generating discovery from artifacts. ");
                    UftTestDiscoveryResult result = ArtifactsHelper.getTestDiscovery(link.getArtifact(), planResultKey);
                    if (result != null) {
                        LOG.info("DISCOVERY RESULTS = " + result);
                        OctaneSDK.getClientByInstanceId(result.getConfigurationId()).getConfigurationService().validateConfigurationAndGetConnectivityStatus();
                        EntitiesService entitiesService = OctaneSDK.getClientByInstanceId(result.getConfigurationId()).getEntitiesService();
                        UftTestDispatchUtils.prepareDiscoveryResultForDispatch(entitiesService, result);


                        JobRunContext jobRunContext = new JobRunContext(event.getContext().getProjectName(), event.getContext().getBuildNumber());
                        UftTestDispatchUtils.dispatchDiscoveryResult(entitiesService, result, jobRunContext, LOG::info);
                        //save result

                        File discoveryResultsFolder = new File(MqmResultsHelper.getBuildResultDirectory(planResultKey.getPlanKey()),UftDiscoveryTask.RESULT_FOLDER);
                        if (!discoveryResultsFolder.exists()) {
                            discoveryResultsFolder.mkdir();
                        }

                        File reportXmlFile = new File(discoveryResultsFolder, UftDiscoveryTask.RESULT_FILE_NAME_PREFIX + event.getContext().getBuildNumber() + ".xml");
                        try {
                            result.writeToFile(reportXmlFile);
                            LOG.info("Final result file is saved in {}",reportXmlFile.getAbsolutePath());
                        } catch (IOException e) {
                            LOG.info(String.format("Failed to save final result file {} : {}", reportXmlFile.getAbsolutePath(),e.getMessage()));
                        }
                    } else {
                        LOG.info("Failed to get discovery results {} - {}" ,link.getArtifact().getLabel(),link.getArtifact());
                    }
                } else {
                    new RuntimeException(OctaneConstants.MQM_RESULT_ARTIFACT_NAME + " artifact is not found");
                }
            } else if("false".equals(discoveryHasClients)){
                LOG.info("Discovery task don't have client and no discovery exist= " +
                        event.getContext().getArtifactContext().getPublishingResults().stream()
                                .map(art->art.getArtifactDefinitionContext().getName())
                                .collect(Collectors.joining(",")));
            }
        } catch (IOException ioe){
            LOG.error("Failed to get Discovery artifact = " + ioe);
        }
        enqueueBuildLog(event.getPlanResultKey().getPlanKey().getKey(), event.getPlanResultKey().getKey(),
                getRootJob(event.getContext()));
    }

    private static String getRootJob(BuildContext buildContext) {
        if (buildContext.getParentBuildContext() == null) {
            return buildContext.getPlanResultKey().getPlanKey().getKey();
        }
        return getRootJob(buildContext.getParentBuildContext());
    }

    private static void saveJobTestResults(PostBuildCompletedEvent event, boolean hasTestResultsArtifact) {
        PlanResultKey planResultKey = event.getPlanResultKey();
         if (hasTestResultsArtifact) {
            LOG.info(planResultKey.toString() + " : test result artifact found - " + OctaneConstants.MQM_RESULT_ARTIFACT_NAME);
            ResultsSummaryManager resultsSummaryManager = ComponentLocator.getComponent(ResultsSummaryManager.class);
            ResultsSummary rs = resultsSummaryManager.getResultsSummary(planResultKey);
            ArtifactLinkManager artifactLinkManager = ComponentLocator.getComponent(ArtifactLinkManager.class);
            Collection<ArtifactLink> links = artifactLinkManager.getArtifactLinks(rs, null);
            ArtifactLink link = links.stream().filter(l -> l.getArtifact().getLabel().equals(OctaneConstants.MQM_RESULT_ARTIFACT_NAME)).findFirst().orElse(null);
            if (link != null) {
                File buildResultDirectory = new File(MqmResultsHelper.getBuildResultDirectory(planResultKey.getPlanKey()),OctaneConstants.MQM_RESULT_FOLDER);
                LOG.info(planResultKey + " : Generating test result from artifacts. Copy artifacts to " + buildResultDirectory.getAbsolutePath());
                ArtifactsHelper.copyArtifactTo(buildResultDirectory, link.getArtifact());
            } else {
                new RuntimeException(OctaneConstants.MQM_RESULT_ARTIFACT_NAME + " artifact is not found");
            }
        } else {
            LOG.info(planResultKey.toString() + " : Generating test result from context");
            InputStream is = MqmResultsHelper.generateTestResultStream(event.getContext(), planResultKey.getKey(), Integer.toString(planResultKey.getBuildNumber()));

            Path targetFilePath = MqmResultsHelper.getMqmResultFilePath(planResultKey);
            MqmResultsHelper.saveStreamToFile(is,planResultKey,targetFilePath);
        }

        File testResultFile = MqmResultsHelper.getMqmResultFilePath(planResultKey).toFile();
        boolean testResultFileExist = testResultFile.exists();
        LOG.info(planResultKey + " : test result file created=" + testResultFileExist + ". Path is " + testResultFile);
    }

    @Override
    public void execute(@NotNull ImmutableChain chain, @NotNull ChainResultsSummary chainResultsSummary, @NotNull ChainExecution chainExecution) throws InterruptedException, Exception {
        if (!OctaneConnectionManager.hasActiveClients()) {
            return;
        }
        LOG.info("Chain " + chainExecution.getBuildIdentifier().getPlanResultKey() + " completed with result " + chainResultsSummary.getBuildState().toString());

        CIEvent ciEvent = CONVERTER.getEventWithDetails(
                chain.getPlanKey().getKey(),
                chainExecution.getBuildIdentifier().getPlanResultKey().getKey(),
                chain.getName(),
                CIEventType.FINISHED,
                chainExecution.getStartTime() != null ? chainExecution.getStartTime().getTime() : chainExecution.getQueueTime().getTime(),
                chainResultsSummary.getDuration(),
                Collections.singletonList(CONVERTER.getCause(chainExecution.getTriggerReason())),
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

        String buildCiId =
                PlanKeys.getPlanResultKey(chain.getPlanKey(), chainExecution.getBuildIdentifier().getBuildNumber())
                        .getKey();

        enqueueBuildLog(chain.getPlanKey().getKey(), buildCiId, chain.getPlanKey().getKey());
    }

    private static void enqueueBuildLog(String jobCiId, String buildCiId, String parents) {
        if (!OctaneSDK.hasClients()) {
            return;
        }
        try {
            LOG.info("enqueued build '" + jobCiId + " #" + buildCiId + "' for log submission");
            OctaneSDK.getClients().forEach(octaneClient -> {
                octaneClient.getLogsService().enqueuePushBuildLog(jobCiId, buildCiId, parents);
            });
        } catch (IllegalArgumentException iae) {
            LOG.error(iae.getMessage());
        } catch (Exception t) {
            LOG.error("failed to enqueue " + jobCiId + " for logs push to Octane", t);
        }
    }
}
