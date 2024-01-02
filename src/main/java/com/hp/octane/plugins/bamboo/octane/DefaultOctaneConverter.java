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

package com.hp.octane.plugins.bamboo.octane;

import com.atlassian.bamboo.builder.BuildState;
import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.chains.cache.ImmutableChainStage;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.plan.AbstractChain;
import com.atlassian.bamboo.plan.PlanIdentifier;
import com.atlassian.bamboo.plan.TopLevelPlan;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.results.tests.TestResults;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.trigger.CodeChangedTriggerReason;
import com.atlassian.bamboo.v2.build.trigger.ManualBuildTriggerReason;
import com.atlassian.bamboo.v2.build.trigger.ScheduledTriggerReason;
import com.atlassian.bamboo.v2.build.trigger.TriggerReason;
import com.atlassian.bamboo.variable.VariableDefinition;
import com.atlassian.bamboo.vcs.configuration.PlanRepositoryDefinition;
import com.atlassian.plugin.util.ContextClassLoaderSwitchingUtil;
import com.ctc.wstx.stax.WstxEventFactory;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.causes.CIEventCauseType;
import com.hp.octane.integrations.dto.configuration.CIProxyConfiguration;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.MultiBranchType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.general.CIJobsList;
import com.hp.octane.integrations.dto.general.CIServerInfo;
import com.hp.octane.integrations.dto.general.CIServerTypes;
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.pipelines.PipelinePhase;
import com.hp.octane.integrations.dto.scm.*;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.integrations.dto.snapshots.CIBuildStatus;
import com.hp.octane.integrations.dto.tests.BuildContext;
import com.hp.octane.integrations.dto.tests.TestRun;
import com.hp.octane.integrations.dto.tests.TestRunError;
import com.hp.octane.integrations.dto.tests.TestRunResult;
import com.hp.octane.plugins.bamboo.listener.ParametersHelper;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DefaultOctaneConverter {

	private static DTOFactory dtoFactoryInstance;
	private final static int DEFAULT_STRING_SIZE = 255;
	private final static DefaultOctaneConverter converter = new DefaultOctaneConverter();

	private DefaultOctaneConverter() {
		dtoFactoryInstance = DTOFactory.getInstance();

		ContextClassLoaderSwitchingUtil.runInContext(WstxEventFactory.class.getClassLoader(), () -> {
			//Some classes related to WstxInputFactory and WstxOutputFactory - are created in another classloader therefore we need to do it in this way
			//this is the exception that is thrown if we don't call it with classLoaderSwitch
			//com.ctc.wstx.stax.WstxInputFactory cannot be cast to javax.xml.stream.XMLInputFactory, java.lang.ClassCastException: com.ctc.wstx.stax.WstxInputFactory cannot be cast to javax.xml.stream.XMLInputFactory
			dtoFactoryInstance.getXMLMapper();
		});
	}

	public static DefaultOctaneConverter getInstance() {
		return converter;
	}

	public static DTOFactory getDTOFactory(){
		return dtoFactoryInstance;
	}

	public PipelineNode getPipelineNodeFromJob(ImmutableJob job) {
		return dtoFactoryInstance.newDTO(PipelineNode.class).setJobCiId(getJobCiId(job)).setName(job.getBuildName());
	}

	public String getRootJobCiId(PlanIdentifier identifier) {
		return getCiId(identifier);
	}

	public String getJobCiId(ImmutableJob job) {
		return getCiId(job);
	}

	public PipelinePhase getPipelinePhaseFromStage(ImmutableChainStage stage) {
		PipelinePhase phase = dtoFactoryInstance.newDTO(PipelinePhase.class)
				.setName(stage.getName())
				.setBlocking(true);
		List<PipelineNode> nodes = new ArrayList<>(stage.getJobs().size());
		for (ImmutableJob job : stage.getJobs()) {
			// TODO decide if we want to mirror disabled jobs or not
			// if (!job.isSuspendedFromBuilding()) {
			nodes.add(getPipelineNodeFromJob(job));
			// }
		}
		phase.setJobs(nodes);
		return phase;
	}

	public CIProxyConfiguration getProxyCconfiguration(String server, int port, String user, String password) {
		return dtoFactoryInstance.newDTO(CIProxyConfiguration.class)
				.setHost(server)
				.setPort(port)
				.setUsername(user)
				.setPassword(password);
	}

	public PipelineNode getRootPipelineNodeFromTopLevelPlan(AbstractChain plan) {
		PipelineNode node = dtoFactoryInstance.newDTO(PipelineNode.class)
				.setJobCiId(getRootJobCiId(plan))
				.setName(plan.getName())
                .setMultiBranchType(MultiBranchType.MULTI_BRANCH_PARENT);
		List<PipelinePhase> phases = new ArrayList<>(plan.getAllStages().size());
		List<VariableDefinition> variables = plan.getVariables();
		if (!variables.isEmpty()) {
			List<CIParameter> params = new ArrayList<>();
			for (VariableDefinition def : variables) {
				if (!ParametersHelper.isEncrypted(def)) {
					params.add(dtoFactoryInstance.newDTO(CIParameter.class).setName(def.getKey()).setDefaultValue(def.getValue()));
				}
			}
			node.setParameters(params);
		}
		for (ImmutableChainStage stage : plan.getAllStages()) {
			phases.add(getPipelinePhaseFromStage(stage));
		}
		node.setPhasesInternal(phases);
		return node;
	}

	public CIServerInfo getServerInfo(String baseUrl, String bambooVersion) {
		return dtoFactoryInstance.newDTO(CIServerInfo.class)
				.setSendingTime(System.currentTimeMillis())
				.setType(CIServerTypes.BAMBOO.value())
				.setVersion(bambooVersion)
				.setUrl(baseUrl);
	}

	public CIBuildResult getJobResult(BuildState buildState) {

		switch (buildState) {
			case FAILED:
				return CIBuildResult.FAILURE;
			case SUCCESS:
				return CIBuildResult.SUCCESS;
			default:
				return CIBuildResult.UNAVAILABLE;
		}
	}

    public CIBuildStatus getCIBuildStatus(LifeCycleState state) {
        switch (state) {
            case PENDING:
            case QUEUED:
                return CIBuildStatus.QUEUED;
            case IN_PROGRESS:
                return CIBuildStatus.RUNNING;
            case NOT_BUILT:
            case FINISHED:
                return CIBuildStatus.FINISHED;
            default:
                return CIBuildStatus.UNAVAILABLE;
        }
    }

		public CIJobsList getRootJobsList(List<TopLevelPlan> plans, boolean includeParameters) {
		CIJobsList jobsList = dtoFactoryInstance.newDTO(CIJobsList.class).setJobs(new PipelineNode[0]);

		List<PipelineNode> nodes = new ArrayList<>(plans.size());
		for (TopLevelPlan plan : plans) {
			PipelineNode node = dtoFactoryInstance.newDTO(PipelineNode.class).setJobCiId(getRootJobCiId(plan))
					.setName(plan.getName())
                    .setMultiBranchType(MultiBranchType.MULTI_BRANCH_PARENT);

			//add parameters
			if(includeParameters) {
				List<VariableDefinition> varDefinitions = plan.getEffectiveVariables();
				if (!varDefinitions.isEmpty()) {
					List<CIParameter> params = new ArrayList<>();
					node.setParameters(params);
					for (VariableDefinition def : varDefinitions) {
						if(!ParametersHelper.isEncrypted(def)) {
							params.add(dtoFactoryInstance.newDTO(CIParameter.class).setName(def.getKey()).setDefaultValue(def.getValue()));
						}
					}

					//setIsTestRunner
					if (node.getParameters() != null) {
						Optional opt = node.getParameters().stream().filter(p -> OctaneConstants.TESTS_TO_RUN_PARAMETER.equals(p.getName())).findFirst();
						node.setIsTestRunner(opt.isPresent());
					} else {
						node.setIsTestRunner(false);
					}
				}
			}
			nodes.add(node);
		}

		jobsList.setJobs(nodes.toArray(new PipelineNode[0]));
		return jobsList;
	}

	public String getCiId(PlanIdentifier identifier) {
		return identifier.getPlanKey().getKey();
	}

    public TestRun getTestRunFromTestResult(com.atlassian.bamboo.v2.build.BuildContext buildContext, HPRunnerType runnerType, TestResults testResult, TestRunResult result, long startTime) {
        String className = testResult.getClassName();
        String simpleName = testResult.getShortClassName();
        String packageName = className.substring(0,
                className.length() - simpleName.length() - (className.length() > simpleName.length() ? 1 : 0));
        String testName = testResult.getActualMethodName();
        if (buildContext.getCheckoutLocation().size() == 1) {
            String checkoutDir = buildContext.getCheckoutLocation().values().iterator().next();
            if (testName.startsWith(checkoutDir)) {
                testName = testName.substring(checkoutDir.length());
                testName = StringUtils.stripStart(testName, "\\/");
            }

            if (HPRunnerType.UFT.equals(runnerType)) { /*for example : a/b/c/d/uftTestName => package name = a/b/c/d and testName = uftTestName*/
                packageName = "";
                simpleName = "";//class name in octane
                int packageSplitter = testName.lastIndexOf("\\");
                if (packageSplitter > 0) {
                    packageName = testName.substring(0, packageSplitter);
                    testName = testName.substring(packageSplitter + 1);
                }
            }
        }

        TestRun testRun = dtoFactoryInstance.newDTO(TestRun.class).setClassName(simpleName)
                .setDuration(Math.round(Double.valueOf(testResult.getDurationMs()))).setPackageName(packageName)
                .setResult(result).setStarted(startTime).setTestName(testName);
        if (result == TestRunResult.FAILED) {
            TestRunError error = dtoFactoryInstance.newDTO(TestRunError.class)
                    .setErrorMessage(testResult.getSystemOut());
            if (!testResult.getErrors().isEmpty()) {
                error.setStackTrace(testResult.getErrors().get(0).getContent());
            }
            testRun.setError(error);
        }

        String externalReport = null;
		if (HPRunnerType.UFT.equals(runnerType)) {
			externalReport = getExternalReportForUft(buildContext, testName);
			if (testResult.getSystemOut() != null && testResult.getSystemOut().contains("warning")) {
				TestRunError error = dtoFactoryInstance.newDTO(TestRunError.class)
						.setErrorMessage("Test ended with 'Warning' status.");
				testRun.setError(error);
			}
		}

        if (StringUtils.isNotEmpty(externalReport)) {
            testRun.setExternalReportUrl(externalReport);
        }
        testRun.setClassName(restrictSize(testRun.getClassName(), DEFAULT_STRING_SIZE))
                .setPackageName(restrictSize(testRun.getPackageName(), DEFAULT_STRING_SIZE))
                .setTestName(restrictSize(testRun.getTestName(), DEFAULT_STRING_SIZE));
        return testRun;
    }

	private String restrictSize(String value, int size) {
		String result = value;
		if (value != null && value.length() > size) {
			result = value.substring(0, size);
		}
		return result;
	}

	private String getExternalReportForUft(com.atlassian.bamboo.v2.build.BuildContext buildContext, String testName) {
        try {
            String baseUrl = BambooPluginServices.getBambooServerBaseUrl();
            String planName = buildContext.getParentBuildContext().getTypedPlanKey().getKey();
            String jobName = buildContext.getResultKey().getEntityKey().getKey().substring(planName.length() + 1);//planName-JobName
            long buildId = buildContext.getResultKey().getResultNumberLong();
            long taskId = 0;
            for (TaskDefinition td : buildContext.getBuildDefinition().getTaskDefinitions()) {
                if (td.getPluginKey().equals(HPRunnerTypeUtils.UFT_FS_PLUGIN_KEY)) {
					taskId = td.getId();
                }
            }
            //example of link : http://localhost:8085/artifact/PR1-EXECT/JOB1/build-12/Micro-Focus-Tasks-Artifact-Definition/UFT_Build_12/002_File_System_Execution/Octane_3/Report.html
            // template =  "<baseUrl>/artifact/<planName>/<jobName>/build-<buildId>/Micro-Focus-Tasks-Artifact-Definition/UFT_Build_<buildId>/<taskId>_File_System_Execution/<testName>/Report.html ";
            String externalReportUrl = String.format("%s/artifact/%s/%s/build-%s/Micro-Focus-Tasks-Artifact-Definition/UFT_Build_%s/%03d_File_System_Execution/%s/Report.html",
                    baseUrl, planName, jobName, buildId, buildId, taskId, testName);
            URL url = new URL(externalReportUrl);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            String result = uri.toURL().toString();
            return result;
        } catch (Exception e) {
            return null;
        }
    }

	public CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType, long startTime,
									   long estimatedDuration, List<CIEventCause> causes, String number, BuildState buildState,
									   Long currnetTime, PhaseType phaseType) {

		CIEvent event = getEventWithDetails(project, buildCiId, displayName, eventType, startTime, estimatedDuration,
				causes, number, phaseType);
		//event.setDuration(currnetTime - event.getStartTime());
		event.setDuration(currnetTime);
		event.setResult(getJobResult(buildState));
        event.setMultiBranchType(MultiBranchType.MULTI_BRANCH_PARENT);
		return event;
	}

	public CIEvent getEventWithDetails(String project, CIEventType eventType) {
		CIEvent event = dtoFactoryInstance.newDTO(CIEvent.class).setEventType(eventType).setProject(project);
		return event;
	}

	public CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType,
	                                   long startTime, long estimatedDuration, List<CIEventCause> causes, String number, PhaseType phaseType) {

		CIEvent event = dtoFactoryInstance.newDTO(CIEvent.class).setEventType(eventType).
				setCauses(causes)
				.setProject(project)
				.setProjectDisplayName(displayName)
				.setBuildCiId(buildCiId)
				.setEstimatedDuration(estimatedDuration)
				.setStartTime(startTime)
				.setPhaseType(phaseType)
                .setMultiBranchType(MultiBranchType.MULTI_BRANCH_PARENT);
		if (number != null) {
			event.setNumber(number);
		}

		return event;
	}

	public CIEventCause getCause(TriggerReason reason) {
		if (reason instanceof ManualBuildTriggerReason) {
			ManualBuildTriggerReason manual = (ManualBuildTriggerReason) reason;
			return dtoFactoryInstance.newDTO(CIEventCause.class).setType(CIEventCauseType.USER).setUser(manual.getUserName());
		} else if (reason instanceof CodeChangedTriggerReason) {
			return dtoFactoryInstance.newDTO(CIEventCause.class).setType(CIEventCauseType.SCM);
		} else if (reason instanceof ScheduledTriggerReason) {
			return dtoFactoryInstance.newDTO(CIEventCause.class).setType(CIEventCauseType.TIMER);
		} else {
			return dtoFactoryInstance.newDTO(CIEventCause.class).setType(CIEventCauseType.UNDEFINED);
		}
	}

	public CIEventCause getUpstreamCause(String buildCiId, String project, CIEventCause parentReason) {
		return dtoFactoryInstance.newDTO(CIEventCause.class).setBuildCiId(buildCiId)
				.setCauses(Collections.singletonList(parentReason)).setProject(project).setType(CIEventCauseType.UPSTREAM);
	}

	public BuildContext getBuildContext(String instanceId, String jobId, String buildId) {
		return dtoFactoryInstance.newDTO(BuildContext.class).setBuildId(buildId).setBuildName(buildId)
				.setJobId(jobId).setJobName(jobId).setServerId(instanceId);
	}

	private List<SCMChange> getChangeList(List<CommitFile> fileList) {
		List<SCMChange> scmChangesList = new ArrayList<>();

		for (CommitFile commitFile : fileList) {
			SCMChange scmChange = dtoFactoryInstance.newDTO(SCMChange.class).
					setFile(commitFile.getName()).
					setType("edit");//this is the default value - SCMChange not contains the change type and it must be not empty. for more information: https://answers.atlassian.com/questions/43728210/answers/43730617/comments/43880899
			scmChangesList.add(scmChange);
		}

		return scmChangesList;
	}

	private SCMRepository createRepository(PlanRepositoryDefinition repo) {
		SCMRepository scmRepository = dtoFactoryInstance.newDTO(SCMRepository.class);
		if (repo.getPluginKey().contains(":svn")) {
			scmRepository.setUrl(repo.getVcsLocation().getConfiguration().get("repository.svn.repositoryRoot"));
			scmRepository.setType(SCMType.SVN);
			scmRepository.setBranch(repo.getBranch().getVcsBranch().getName());
		} else if (repo.getPluginKey().contains(":git")) {
			scmRepository.setUrl(repo.getVcsLocation().getConfiguration().get("repository.git.repositoryUrl"));
			scmRepository.setType(SCMType.GIT);
			scmRepository.setBranch(repo.getBranch().getVcsBranch().getName());
		} else {
			scmRepository.setType(SCMType.UNKNOWN);
		}

		return scmRepository;
	}

	private SCMCommit getScmCommit(CommitContext commitContext) {
		SCMCommit scmCommit = dtoFactoryInstance.newDTO(SCMCommit.class);
		scmCommit.setRevId(commitContext.getChangeSetId());
		scmCommit.setComment(commitContext.getComment());
		scmCommit.setUser(commitContext.getAuthorContext().getName());
		scmCommit.setUserEmail(commitContext.getAuthorContext().getEmail());
		//scmCommit.setParentRevId();
		scmCommit.setTime(commitContext.getDate().getTime());
		scmCommit.setChanges(getChangeList(commitContext.getFiles()));
		return scmCommit;
	}

	public SCMData getScmData(com.atlassian.bamboo.v2.build.BuildContext buildContext) {
		SCMData scmData = null;
		SCMRepository scmRepository = null;

		if (buildContext.getVcsRepositories() != null && !buildContext.getVcsRepositories().isEmpty()) {
			PlanRepositoryDefinition repo = buildContext.getVcsRepositories().get(0);
			scmRepository = createRepository(repo);
		}
		List<SCMCommit> scmCommitList = new ArrayList<>();
		BuildChanges buildChanges = buildContext.getBuildChanges();
		for (BuildRepositoryChanges change : buildChanges.getRepositoryChanges()) {
			for (CommitContext commitContext : change.getChanges()) {
				scmCommitList.add(getScmCommit(commitContext));
			}
		}

		if (scmCommitList.size() > 0) {
			scmData = dtoFactoryInstance.newDTO(SCMData.class);
			scmData.setCommits(scmCommitList);
			scmData.setRepository(scmRepository);
			scmData.setBuiltRevId(buildContext.getPlanResultKey().getKey());
		}
		return scmData;
	}
}
