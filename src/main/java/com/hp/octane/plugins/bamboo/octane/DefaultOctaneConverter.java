/*
 *     Copyright 2017 Hewlett-Packard Development Company, L.P.
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
import com.atlassian.bamboo.builder.LifeCycleState;
import com.atlassian.bamboo.chains.cache.ImmutableChainStage;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.commit.CommitFile;
import com.atlassian.bamboo.plan.PlanIdentifier;
import com.atlassian.bamboo.plan.cache.ImmutableJob;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.bamboo.plugins.git.GitRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.svn.SvnRepository;
import com.atlassian.bamboo.results.tests.TestResults;
import com.atlassian.bamboo.resultsummary.ImmutableResultsSummary;
import com.atlassian.bamboo.v2.build.BuildChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.causes.CIEventCause;
import com.hp.octane.integrations.dto.causes.CIEventCauseType;
import com.hp.octane.integrations.dto.configuration.CIProxyConfiguration;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.events.CIEventType;
import com.hp.octane.integrations.dto.events.PhaseType;
import com.hp.octane.integrations.dto.general.CIJobsList;
import com.hp.octane.integrations.dto.general.CIServerInfo;
import com.hp.octane.integrations.dto.general.CIServerTypes;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.pipelines.PipelinePhase;
import com.hp.octane.integrations.dto.scm.SCMChange;
import com.hp.octane.integrations.dto.scm.SCMCommit;
import com.hp.octane.integrations.dto.scm.SCMData;
import com.hp.octane.integrations.dto.scm.SCMRepository;
import com.hp.octane.integrations.dto.scm.SCMType;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.integrations.dto.snapshots.CIBuildStatus;
import com.hp.octane.integrations.dto.snapshots.SnapshotNode;
import com.hp.octane.integrations.dto.snapshots.SnapshotPhase;
import com.hp.octane.integrations.dto.tests.BuildContext;
import com.hp.octane.integrations.dto.tests.TestRun;
import com.hp.octane.integrations.dto.tests.TestRunError;
import com.hp.octane.integrations.dto.tests.TestRunResult;

import java.util.ArrayList;
import java.util.List;

public class DefaultOctaneConverter implements DTOConverter {

	private DTOFactory dtoFactoryInstance;

	private static DTOConverter converter;

	private DefaultOctaneConverter() {
		super();
		dtoFactoryInstance = DTOFactory.getInstance();
	}

	public static DTOConverter getInstance() {
		synchronized (DefaultOctaneConverter.class) {
			if (converter == null) {
				converter = new DefaultOctaneConverter();
			}
		}
		return converter;
	}

	public PipelineNode getPipelineNodeFromJob(ImmutableJob job) {
		return dtoFactoryInstance.newDTO(PipelineNode.class).setJobCiId(getJobCiId(job)).setName(job.getBuildName());
	}

	public String getRootJobCiId(ImmutableTopLevelPlan plan) {
		return getCiId(plan);
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

	public PipelineNode getRootPipelineNodeFromTopLevelPlan(ImmutableTopLevelPlan plan) {
		PipelineNode node = dtoFactoryInstance.newDTO(PipelineNode.class)
				.setJobCiId(getRootJobCiId(plan))
				.setName(plan.getName());
		List<PipelinePhase> phases = new ArrayList<>(plan.getAllStages().size());
		for (ImmutableChainStage stage : plan.getAllStages()) {
			phases.add(getPipelinePhaseFromStage(stage));
		}
		node.setPhasesInternal(phases);
		return node;
	}

	public CIServerInfo getServerInfo(String baseUrl, String instanceId, String impersonatedUser) {
		return dtoFactoryInstance.newDTO(CIServerInfo.class)
				.setInstanceId(instanceId)
				.setInstanceIdFrom(System.currentTimeMillis())
				.setSendingTime(System.currentTimeMillis())
				.setImpersonatedUser(impersonatedUser)
				.setType(CIServerTypes.BAMBOO.value())
				.setUrl(baseUrl);
	}

	public SnapshotNode getSnapshot(ImmutableTopLevelPlan plan, ImmutableResultsSummary resultsSummary) {
		SnapshotNode snapshotNode = getSnapshotNode(plan, resultsSummary, true);

		List<SnapshotPhase> phases = new ArrayList<>(plan.getAllStages().size());
		for (ImmutableChainStage stage : plan.getAllStages()) {
			phases.add(getSnapshotPhaseFromStage(stage));
		}
		snapshotNode.setPhasesInternal(phases);
		return snapshotNode;
	}

	private SnapshotPhase getSnapshotPhaseFromStage(ImmutableChainStage stage) {
		SnapshotPhase phase = dtoFactoryInstance.newDTO(SnapshotPhase.class);
		phase.setName(stage.getName());
		phase.setBlocking(true);

		List<SnapshotNode> nodes = new ArrayList<>(stage.getJobs().size());
		for (ImmutableJob job : stage.getJobs()) {
			// TODO decide if we want to mirror disabled jobs or not
			nodes.add(getSnapshotNode(job, job.getLatestResultsSummary(), false));
		}
		phase.setBuilds(nodes);

		return phase;
	}

	private SnapshotNode getSnapshotNode(ImmutablePlan plane, ImmutableResultsSummary resultsSummary, boolean isRoot) {

		return dtoFactoryInstance.newDTO(SnapshotNode.class)
				.setBuildCiId(resultsSummary.getPlanResultKey().getKey())
				.setName(isRoot ? plane.getBuildName() : plane.getName())
				.setJobCiId((getCiId(plane)))
				.setDuration(resultsSummary.getDuration())
				.setNumber(String.valueOf(resultsSummary.getBuildNumber()))
				.setResult(getJobResult(resultsSummary.getBuildState()))
				.setStatus(getJobStatus(plane.getLatestResultsSummary().getLifeCycleState()))
				.setStartTime(resultsSummary.getBuildDate() != null ? resultsSummary.getBuildDate().getTime()
						: (resultsSummary.getBuildCompletedDate() != null
						? resultsSummary.getBuildCompletedDate().getTime() : System.currentTimeMillis()));
	}

	private CIBuildStatus getJobStatus(LifeCycleState lifeCycleState) {
		switch (lifeCycleState) {
			case FINISHED:
				return CIBuildStatus.FINISHED;
			case IN_PROGRESS:
				return CIBuildStatus.RUNNING;
			case QUEUED:
				return CIBuildStatus.QUEUED;
			default:
				return CIBuildStatus.UNAVAILABLE;
		}
	}

	private CIBuildResult getJobResult(BuildState buildState) {

		switch (buildState) {
			case FAILED:
				return CIBuildResult.FAILURE;
			case SUCCESS:
				return CIBuildResult.SUCCESS;
			default:
				return CIBuildResult.UNAVAILABLE;
		}
	}

	public CIJobsList getRootJobsList(List<ImmutableTopLevelPlan> plans) {
		CIJobsList jobsList = dtoFactoryInstance.newDTO(CIJobsList.class).setJobs(new PipelineNode[0]);

		List<PipelineNode> nodes = new ArrayList<>(plans.size());
		for (ImmutableTopLevelPlan plan : plans) {
			PipelineNode node = DTOFactory.getInstance().newDTO(PipelineNode.class).setJobCiId(getRootJobCiId(plan))
					.setName(plan.getName());
			nodes.add(node);
		}

		jobsList.setJobs(nodes.toArray(new PipelineNode[nodes.size()]));
		return jobsList;
	}

	public String getCiId(PlanIdentifier identifier) {
		return identifier.getPlanKey().getKey();
	}

	public TestRun getTestRunFromTestResult(TestResults testResult, TestRunResult result, long startTime) {
		String className = testResult.getClassName();
		String simpleName = testResult.getShortClassName();
		String packageName = className.substring(0,
				className.length() - simpleName.length() - (className.length() > simpleName.length() ? 1 : 0));

		TestRun testRun = dtoFactoryInstance.newDTO(TestRun.class).setClassName(simpleName)
				.setDuration(Math.round(Double.valueOf(testResult.getDuration()))).setPackageName(packageName)
				.setResult(result).setStarted(startTime).setTestName(testResult.getActualMethodName());
		if (result == TestRunResult.FAILED) {
			TestRunError error = dtoFactoryInstance.newDTO(TestRunError.class)
					.setErrorMessage(testResult.getSystemOut());
			if (!testResult.getErrors().isEmpty()) {
				error.setStackTrace(testResult.getErrors().get(0).getContent());
			}
			testRun.setError(error);
		}
		return testRun;
	}

	@Override
	public CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType, long startTime,
									   long estimatedDuration, List<CIEventCause> causes, String number, BuildState buildState,
									   Long currnetTime, PhaseType phaseType) {

		CIEvent event = getEventWithDetails(project, buildCiId, displayName, eventType, startTime, estimatedDuration,
				causes, number, phaseType);
		//event.setDuration(currnetTime - event.getStartTime());
		event.setDuration(currnetTime);
		event.setResult(getJobResult(buildState));
		return event;
	}

	@Override
	public CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType,
	                                   long startTime, long estimatedDuration, List<CIEventCause> causes, String number, PhaseType phaseType) {

		CIEvent event = dtoFactoryInstance.newDTO(CIEvent.class).setEventType(eventType).
				setCauses(causes)
				.setProject(project)
				.setProjectDisplayName(displayName)
				.setBuildCiId(buildCiId)
				.setEstimatedDuration(estimatedDuration)
				.setStartTime(startTime)
				.setPhaseType(phaseType);
		if (number != null) {
			event.setNumber(number);
		}

		return event;
	}

	@Override
	public CIEvent getEventWithDetails(String project, String buildCiId, String displayName, CIEventType eventType, long startTime, long estimatedDuration,
	                                   List<CIEventCause> causes, String number, SCMData scmData, PhaseType phaseType) {

		CIEvent event = getEventWithDetails(project, buildCiId, displayName, eventType, startTime, estimatedDuration, causes, number, phaseType);
		event.setScmData(scmData);
		return event;
	}

	public CIEventCause getCauseWithDetails(String buildCiId, String project, String user) {
		return DTOFactory.getInstance().newDTO(CIEventCause.class).setBuildCiId(buildCiId)
				.setCauses(new ArrayList<CIEventCause>()).setProject(project).setType(CIEventCauseType.UPSTREAM)
				.setUser(user);
	}

	public BuildContext getBuildContext(String instanceId, String jobId, String buildId) {
		return DTOFactory.getInstance().newDTO(BuildContext.class).setBuildId(buildId).setBuildName(buildId)
				.setJobId(jobId).setJobName(jobId).setServerId(instanceId);
	}


	private List<SCMChange> getChangeList(List<CommitFile> fileList) {
		List<SCMChange> scmChangesList = new ArrayList<>();

		for (CommitFile commitFile : fileList) {
			SCMChange scmChange = DTOFactory.getInstance().newDTO(SCMChange.class).
					setFile(commitFile.getName()).
					setType("edit");//this is the default value - SCMChange not contains the change type and it must be not empty. for more information: https://answers.atlassian.com/questions/43728210/answers/43730617/comments/43880899
			scmChangesList.add(scmChange);
		}

		return scmChangesList;
	}

	private SCMRepository createRepository(Repository repo) {
		SCMRepository scmRepository = DTOFactory.getInstance().newDTO(SCMRepository.class);
		if (repo instanceof SvnRepository) {
			SvnRepository svn = (SvnRepository) repo;
			scmRepository.setUrl(svn.getRepositoryUrl());
			scmRepository.setType(SCMType.SVN);
			scmRepository.setBranch(svn.getVcsBranch().getName());
		} else if (repo instanceof GitRepository) {
			GitRepository git = (GitRepository) repo;
			scmRepository.setUrl(git.getRepositoryUrl());
			scmRepository.setType(SCMType.GIT);
			scmRepository.setBranch(git.getVcsBranch().getName());
		} else {
			scmRepository.setType(SCMType.UNKNOWN);
		}

		return scmRepository;
	}

	private SCMCommit getScmCommit(CommitContext commitContext) {
		SCMCommit scmCommit = DTOFactory.getInstance().newDTO(SCMCommit.class);
		scmCommit.setRevId(commitContext.getChangeSetId());
		scmCommit.setComment(commitContext.getComment());
		scmCommit.setUser(commitContext.getAuthorContext().getName());
		scmCommit.setUserEmail(commitContext.getAuthorContext().getEmail());
		//scmCommit.setParentRevId();
		scmCommit.setTime(commitContext.getDate().getTime());
		scmCommit.setChanges(getChangeList(commitContext.getFiles()));
		return scmCommit;
	}

	@Override
	public SCMData getScmData(com.atlassian.bamboo.v2.build.BuildContext buildContext) {
		SCMData scmData = null;
		SCMRepository scmRepository = null;

		if (buildContext.getRepositoryDefinitions() != null && !buildContext.getRepositoryDefinitions().isEmpty()) {
			Repository repo = buildContext.getRepositoryDefinitions().get(0).getRepository();
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
			scmData = DTOFactory.getInstance().newDTO(SCMData.class);
			scmData.setCommits(scmCommitList);
			scmData.setRepository(scmRepository);
			scmData.setBuiltRevId(buildContext.getBuildResultKey());
		}
		return scmData;
	}
}
