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

package com.hp.octane.plugins.bamboo.octane.uft;

import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.build.PlanCreationDeniedException;
import com.atlassian.bamboo.build.creation.*;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.credentials.CredentialTypeModuleDescriptor;
import com.atlassian.bamboo.credentials.CredentialsData;
import com.atlassian.bamboo.credentials.CredentialsManager;
import com.atlassian.bamboo.fieldvalue.TaskConfigurationUtils;
import com.atlassian.bamboo.plan.*;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionManager;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.bamboo.plugins.git.GitAuthenticationType;
import com.atlassian.bamboo.plugins.git.GitPasswordCredentialsSource;
import com.atlassian.bamboo.plugins.git.v2.configurator.GitConfigurationConstants;
import com.atlassian.bamboo.project.Project;
import com.atlassian.bamboo.project.ProjectManager;
import com.atlassian.bamboo.repository.AuthenticationType;
import com.atlassian.bamboo.repository.PlanRepositoryLink;
import com.atlassian.bamboo.repository.RepositoryConfigurationService;
import com.atlassian.bamboo.repository.RepositoryDefinitionManager;
import com.atlassian.bamboo.repository.svn.v2.configurator.SvnConfigurationConstants;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.task.TaskDefinition;
import com.atlassian.bamboo.task.TaskDefinitionImpl;
import com.atlassian.bamboo.trigger.TriggerConfigurationService;
import com.atlassian.bamboo.trigger.TriggerModuleDescriptor;
import com.atlassian.bamboo.trigger.TriggerTypeManager;
import com.atlassian.bamboo.user.BambooUser;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.requirement.ImmutableRequirement;
import com.atlassian.bamboo.v2.build.requirement.RequirementService;
import com.atlassian.bamboo.variable.VariableConfigurationService;
import com.atlassian.bamboo.vcs.configuration.PartialVcsRepositoryData;
import com.atlassian.bamboo.vcs.configuration.PartialVcsRepositoryDataBuilder;
import com.atlassian.bamboo.vcs.configuration.VcsRepositoryData;
import com.atlassian.bamboo.vcs.configuration.service.VcsRepositoryConfigurationService;
import com.atlassian.bamboo.vcs.configurator.VcsBranchConfigurator;
import com.atlassian.bamboo.vcs.configurator.VcsChangeDetectionOptionsConfigurator;
import com.atlassian.bamboo.vcs.configurator.VcsLocationConfigurator;
import com.atlassian.bamboo.vcs.module.VcsRepositoryManager;
import com.atlassian.bamboo.vcs.module.VcsRepositoryModuleDescriptor;
import com.atlassian.bamboo.web.utils.BuildConfigurationActionHelper;
import com.atlassian.bamboo.webwork.util.ActionParametersMapImpl;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import com.hp.octane.integrations.dto.executor.CredentialsInfo;
import com.hp.octane.integrations.dto.executor.DiscoveryInfo;
import com.hp.octane.integrations.dto.executor.TestConnectivityInfo;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.scm.SCMRepository;
import com.hp.octane.integrations.dto.scm.SCMType;
import com.hp.octane.integrations.utils.SdkStringUtils;
import com.hp.octane.plugins.bamboo.listener.ParametersHelper;
import com.hp.octane.plugins.bamboo.octane.BambooPluginServices;
import com.hp.octane.plugins.bamboo.octane.DefaultOctaneConverter;
import com.hp.octane.plugins.bamboo.octane.utils.Utils;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class UftManager {

    private BambooUserManager bambooUserManager;
    private ChainCreationService chainCreationService;
    private CredentialsManager credentialsManager;
    private EncryptionService encryptionService;
    private CachedPlanManager cachedPlanManager;

    private PlanManager planManager;
    private PlanExecutionManager planExecutionManager;
    private ProjectManager projectManager;
    private JobCreationService jobCreationService;
    private TriggerTypeManager triggerTypeManager;
    private TriggerConfigurationService triggerConfigurationService;
    private VariableConfigurationService variableConfigurationService;

    private RepositoryDefinitionManager repositoryDefinitionManager;
    private VcsRepositoryManager vcsRepositoryManager;
    private VcsRepositoryConfigurationService vcsRepositoryConfigurationService;
    private RequirementService requirementService;

    private static String CREDENTIALS_USERNAME_FIELD = "username";
    private static String CREDENTIALS_PASSWORD_FIELD = "password";

    private static String USERNAME_PASSWORD_PLUGIN_KEY = "com.atlassian.bamboo.plugin.sharedCredentials:usernamePasswordCredentials";
    private static String DISCOVERY_TASK_PLUGIN_KEY = BambooPluginServices.PLUGIN_KEY + ":octaneUftTestDiscovery";
    private static String EXECUTION_TASK_PLUGIN_KEY = "com.adm.app-delivery-management-bamboo:RunFromFileSystemUftTask";
    private static String CONVERTER_TASK_PLUGIN_KEY = BambooPluginServices.PLUGIN_KEY + ":octaneTestFrameworkConverter";

    private static final String TRIGGER_POLLING_PLUGIN_KEY = "com.atlassian.bamboo.triggers.atlassian-bamboo-triggers:poll";
    public static final String PROJECT_KEY = "UOI";
    public static final String DISCOVERY_PREFIX_KEY = "UFTDISCOVERY";
    public static final String EXECUTOR_PREFIX_KEY = "UFTEXECUTOR";

    private static final Logger log = LogManager.getLogger(UftManager.class);


    private static final String UFT_INTEGRATION_PREFIX = "UFT";

    private static UftManager instance = new UftManager();

    public static UftManager getInstance() {
        return instance;
    }

    private UftManager() {

        bambooUserManager = ComponentLocator.getComponent(BambooUserManager.class);
        chainCreationService = ComponentLocator.getComponent(ChainCreationService.class);
        credentialsManager = ComponentLocator.getComponent(CredentialsManager.class);
        encryptionService = ComponentLocator.getComponent(EncryptionService.class);
        planManager = ComponentLocator.getComponent(PlanManager.class);
        cachedPlanManager = ComponentLocator.getComponent(CachedPlanManager.class);
        planExecutionManager = ComponentLocator.getComponent(PlanExecutionManager.class);
        projectManager = ComponentLocator.getComponent(ProjectManager.class);
        repositoryDefinitionManager = ComponentLocator.getComponent(RepositoryDefinitionManager.class);
        vcsRepositoryConfigurationService = ComponentLocator.getComponent(VcsRepositoryConfigurationService.class);
        vcsRepositoryManager = ComponentLocator.getComponent(VcsRepositoryManager.class);
        jobCreationService = ComponentLocator.getComponent(JobCreationService.class);

        triggerTypeManager = ComponentLocator.getComponent(TriggerTypeManager.class);
        triggerConfigurationService = ComponentLocator.getComponent(TriggerConfigurationService.class);
        variableConfigurationService = ComponentLocator.getComponent(VariableConfigurationService.class);

        requirementService = ComponentLocator.getComponent(RequirementService.class);
    }

    public OctaneResponse upsertCredentials(CredentialsInfo credentialsInfo) {

        OctaneResponse result = DTOFactory.getInstance().newDTO(OctaneResponse.class);
        result.setStatus(HttpStatus.SC_CREATED);

        if (SdkStringUtils.isNotEmpty(credentialsInfo.getCredentialsId())) {
            CredentialsData cred = credentialsManager.getCredentials(Long.parseLong(credentialsInfo.getCredentialsId()));
            if (cred != null) {
                try {
                    Map<String, String> confMap = new HashMap<>();
                    confMap.put(CREDENTIALS_USERNAME_FIELD, credentialsInfo.getUsername());
                    confMap.put(CREDENTIALS_PASSWORD_FIELD, encryptionService.encrypt(credentialsInfo.getPassword()));
                    credentialsManager.editCredentials(cred.getId(), createCredentialName(credentialsInfo.getUsername(), false), confMap);
                    result.setBody(Long.toString(cred.getId()));
                } catch (Exception e) {
                    result.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    result.setBody("Failed to update credentials " + e.getMessage());
                    log.error("Failed to update credentials " + e.getMessage(), e);
                }
            }
        } else if (SdkStringUtils.isNotEmpty(credentialsInfo.getUsername()) && credentialsInfo.getPassword() != null) {
            //try to locate already existing credentials
            boolean found = false;
            Iterable<CredentialsData> credentialsIterator = credentialsManager.getAllCredentials(USERNAME_PASSWORD_PLUGIN_KEY);
            for (CredentialsData cred : credentialsIterator) {
                if (cred.getName().startsWith("UFT")) {
                    String credUserName = cred.getConfiguration().get(CREDENTIALS_USERNAME_FIELD);
                    String credPassword = cred.getConfiguration().get(CREDENTIALS_PASSWORD_FIELD);
                    if (SdkStringUtils.equals(credentialsInfo.getUsername(), credUserName) && SdkStringUtils.equals(credentialsInfo.getPassword(), encryptionService.decrypt(credPassword))) {
                        result.setBody(Long.toString(cred.getId()));
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {

                Map<String, String> confMap = new HashMap<>();
                confMap.put(CREDENTIALS_USERNAME_FIELD, credentialsInfo.getUsername());
                confMap.put(CREDENTIALS_PASSWORD_FIELD, encryptionService.encrypt(credentialsInfo.getPassword()));
                CredentialTypeModuleDescriptor credentialTypeModuleDescriptor = credentialsManager.getCredentialTypeDescriptor(USERNAME_PASSWORD_PLUGIN_KEY);

                try {
                    CredentialsData cred = credentialsManager.createCredentials(credentialTypeModuleDescriptor, createCredentialName(credentialsInfo.getUsername(), true), confMap);
                    result.setBody(Long.toString(cred.getId()));
                } catch (Exception e) {
                    result.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    result.setBody("Failed to create credentials " + e.getMessage());
                    log.error("Failed to create credentials " + e.getMessage(), e);
                }
            }
        }

        return result;
    }

    public OctaneResponse checkRepositoryConnectivity(TestConnectivityInfo testConnectivityInfo) {
        log.info("checkRepositoryConnectivity");
        OctaneResponse result = DTOFactory.getInstance().newDTO(OctaneResponse.class);
        if (testConnectivityInfo.getScmRepository() == null || SdkStringUtils.isEmpty(testConnectivityInfo.getScmRepository().getUrl())) {
            result.setStatus(HttpStatus.SC_BAD_REQUEST);
            result.setBody("Missing input for testing");
        } else {

            try {
                PartialVcsRepositoryData vcsRepositoryData = createRepositoryData("tempRepositoryForTestingConnectivity", testConnectivityInfo.getScmRepository(), testConnectivityInfo.getUsername(), testConnectivityInfo.getPassword(), testConnectivityInfo.getCredentialsId());
                VcsRepositoryModuleDescriptor vcsDescriptor = vcsRepositoryManager.getVcsRepositoryModuleDescriptor(vcsRepositoryData.getPluginKey());
                ErrorCollection errors = vcsDescriptor.getConnectionTester().testConnection(vcsRepositoryData.getCompleteData(), 1, TimeUnit.MINUTES);
                if (errors.getErrorMessages().isEmpty()) {
                    result.setStatus(HttpStatus.SC_OK);
                } else {
                    result.setStatus(HttpStatus.SC_FORBIDDEN);
                    result.setBody(errors.getErrorMessages().iterator().next());//set only first exception
                }
            } catch (Exception e) {
                result.setStatus(HttpStatus.SC_INTERNAL_SERVER_ERROR);
                result.setBody("Exception occurred : " + e.getMessage());
            }
        }
        return result;
    }

    public void runTestDiscovery(DiscoveryInfo discoveryInfo, String impersonatedUser) {
        try {
            log.info("Creating TestDiscovery plan for " + discoveryInfo.getScmRepository().getUrl());
            BambooUser bambooUser = bambooUserManager.getBambooUser(impersonatedUser);
            Project project = getMainProject();
            VcsRepositoryData linkedRepository = getLinkedRepository(discoveryInfo.getScmRepository(), discoveryInfo.getScmRepositoryCredentialsId(), bambooUser);
            buildDiscoveryPlan(project, discoveryInfo, linkedRepository);
        } catch (Exception e) {
            throw new RuntimeException("Failed to runTestDiscovery : " + e.getMessage(), e);
        }
    }

    public void deleteExecutor(String id) {
        log.info("deleteExecutor " + id);
        String discoveryKeyPrefix = createChainBuildKey(DISCOVERY_PREFIX_KEY, id, "");
        String executionKeyPrefix = createChainBuildKey(EXECUTOR_PREFIX_KEY, id, "");
        Project project = getMainProject();
        List<TopLevelPlan> plans = planManager.getAllPlansByProject(project, TopLevelPlan.class);
        for (TopLevelPlan plan : plans) {
            if (plan.getBuildKey().startsWith(discoveryKeyPrefix) /*|| plan.getBuildKey().startsWith(executionKeyPrefix)*/) {
                planManager.markPlansForDeletion(plan.getPlanKey());
            }
        }
    }

    private String createCredentialName(String username, boolean creation) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm");
        String createOrUpdate = creation ? " , created on " : " , updated on ";
        String credentialsName = UFT_INTEGRATION_PREFIX + " - " + username + createOrUpdate + formatter.format(new Date());
        return credentialsName;
    }

    private Project getMainProject() {

        String name = "UftOctaneIntegration";
        String desc = "This project was created by the Micro Focus Octane plugin for managing execution of UFT tests and integration with ALM Octane.";
        Project project = projectManager.getProjectByKey(PROJECT_KEY);
        if (project == null) {
            project = projectManager.createProject(PROJECT_KEY, name, desc);
            projectManager.saveProject(project);
        }
        return project;
    }

    private Plan buildDiscoveryPlan(Project project, DiscoveryInfo discoveryInfo, VcsRepositoryData linkedRepository) throws PlanCreationDeniedException {
        String description = String.format("This plan was created by the Micro Focus plugin for discovery of UFT tests. It is associated with ALM Octane test runner #%s.", discoveryInfo.getExecutorId());
        String key = createChainBuildKey(DISCOVERY_PREFIX_KEY, discoveryInfo.getExecutorId(), discoveryInfo.getExecutorLogicalName());
        String name = String.format("UFT test discovery - Test Runner ID %s (%s)", discoveryInfo.getExecutorId(), discoveryInfo.getExecutorLogicalName());

        final BuildConfiguration buildConfiguration = new BuildConfiguration();
        String chainBuildKeyStr = createChain(project, linkedRepository, key, description, name, buildConfiguration);

        //create, default checkout task is configured in JobCreationServiceImpl.createDefaultCheckoutTask
        ActionParametersMap actionParametersMap = configureDefaultStageAndJob(buildConfiguration, chainBuildKeyStr);
        configureDiscoveryTask(buildConfiguration, discoveryInfo);
        this.jobCreationService.createJobAndBranches(buildConfiguration, actionParametersMap, PlanCreationService.EnablePlan.ENABLED);

        //GET CHAIN
        PlanKey chainKey = PlanKeys.getPlanKey(chainBuildKeyStr);
        Chain chain = planManager.getPlanByKeyIfOfType(chainKey, Chain.class);

        //CREATE TRIGGER
        PlanRepositoryLink repositoryLink = repositoryDefinitionManager.getPlanRepositoryLinks(chain).get(0);
        createPollTrigger(chainKey, repositoryLink.getRepositoryDataEntity().getId());

        //ADD artifact definitions to job
        Job job = chain.getAllStages().get(0).getJobs().iterator().next();
        registerArtifactForDiscovery(job);

        //SEND CREATION EVENT
        chainCreationService.triggerCreationCompleteEvents(chainKey);
        return chain;
    }

    private void configureDiscoveryTask(BuildConfiguration buildConfiguration, DiscoveryInfo discoveryInfo) {
        //discovery, example from com.atlassian.bamboo.build.creation.JobCreationServiceImpl.createDefaultCheckoutTask
        LinkedList<TaskDefinition> tasks = new LinkedList(TaskConfigurationUtils.getTaskDefinitionsFromConfig("buildTasks.", buildConfiguration));
        TaskDefinition discoveryTask = new TaskDefinitionImpl(TaskConfigurationUtils.getUniqueId(tasks), DISCOVERY_TASK_PLUGIN_KEY,
                "Discover UFT tests and data tables", true, new HashMap());
        discoveryTask.getConfiguration().put(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM, discoveryInfo.getScmRepositoryId());
        discoveryTask.getConfiguration().put(UftDiscoveryTask.TEST_RUNNER_ID_PARAM, discoveryInfo.getExecutorId());
        discoveryTask.getConfiguration().put(UftDiscoveryTask.WORKSPACE_ID_PARAM, discoveryInfo.getWorkspaceId());
        tasks.add(discoveryTask);
        buildConfiguration.clearTree(TaskConfigurationUtils.TASK_CONFIG_ROOT);
        TaskConfigurationUtils.addTaskDefinitionsToConfig(tasks, buildConfiguration, TaskConfigurationUtils.TASK_PREFIX);
    }

    private void configureExecutionTask(BuildConfiguration buildConfiguration) {
        //discovery, example from com.atlassian.bamboo.build.creation.JobCreationServiceImpl.createDefaultCheckoutTask
        LinkedList<TaskDefinition> tasks = new LinkedList(TaskConfigurationUtils.getTaskDefinitionsFromConfig("buildTasks.", buildConfiguration));

        //CONVERT task
        TaskDefinition convertTask = new TaskDefinitionImpl(1, CONVERTER_TASK_PLUGIN_KEY,
                "Uft test converter", true, new HashMap());
        convertTask.getConfiguration().put("framework", "uft");
        convertTask.getConfiguration().put("taskName", "Uft_test_converter");
        tasks.add(convertTask);

        //EXECUTION task
        TaskDefinition executionTask = new TaskDefinitionImpl(2, EXECUTION_TASK_PLUGIN_KEY,
                "UFT Executor", true, new HashMap());
        executionTask.getConfiguration().put("testPathInput", "${bamboo.testsToRunConverted}");
        executionTask.getConfiguration().put("publishMode", "RunFromFileSystemTask.publishMode.always");
        executionTask.getConfiguration().put("taskName", "File_System_Execution");
        tasks.add(executionTask);

        buildConfiguration.clearTree(TaskConfigurationUtils.TASK_CONFIG_ROOT);
        TaskConfigurationUtils.addTaskDefinitionsToConfig(tasks, buildConfiguration, TaskConfigurationUtils.TASK_PREFIX);
    }

    private void createPollTrigger(PlanKey planKey, long repositoryId) {
        TriggerModuleDescriptor triggerDescriptor = triggerTypeManager.getTriggerDescriptor(TRIGGER_POLLING_PLUGIN_KEY);
        Map<String, String> configuration = new HashMap<>();
        configuration.put("repository.change.poll.pollingPeriod", "120");//once in 120 sec
        configuration.put("repository.change.poll.type", "PERIOD");
        configuration.put("repository.change.poll.cronExpression", "0 0 0 ? * *");

        Map<String, String> triggerConditionsConfiguration = new HashMap<>();
        triggerConditionsConfiguration.put("custom.triggerrCondition.plansGreen.enabled", "false");

        triggerConfigurationService.createTrigger(planKey, triggerDescriptor, "Polling trigger", true, new HashSet<>(Arrays.asList(repositoryId)), configuration, triggerConditionsConfiguration);
    }

    private ActionParametersMap configureDefaultStageAndJob(BuildConfiguration buildConfiguration, String buildKey) {
        //set configuration fro default job
        ActionParametersMap actionParametersMap = new ActionParametersMapImpl(new HashMap());
        JobParamMapHelper.setBuildKey(actionParametersMap, buildKey);
        JobParamMapHelper.setBuildName(actionParametersMap, "Default Job");
        JobParamMapHelper.setSubBuildKey(actionParametersMap, "JOB1");
        JobParamMapHelper.setStageName(actionParametersMap, "Default Stage");
        JobParamMapHelper.setExistingStage(actionParametersMap, JobCreationConstants.NEW_STAGE_MARKER);
        buildConfiguration.setProperty("inheritRepository", "true");
        return actionParametersMap;
    }

    private String replaceSpecialCharactersInUrl(String url) {
        return url.trim().replaceAll("[<>:\"/\\|?*{}@&%!;~]", "_");
    }

    private VcsRepositoryData getLinkedRepository(SCMRepository scmRepository, String sharedCredentialsId, BambooUser impersonatedUser) {
        VcsRepositoryData result = null;
        String repositoryName = UFT_INTEGRATION_PREFIX + "_" + replaceSpecialCharactersInUrl(scmRepository.getUrl());

        //try to find existing
        List<VcsRepositoryData> repositories = repositoryDefinitionManager.getLinkedRepositories();
        for (VcsRepositoryData data : repositories) {
            if (data.getName().equalsIgnoreCase(repositoryName)) {
                result = data;
                break;
            }
        }

        //create new
        if (result == null) {
            PartialVcsRepositoryData vcsRepositoryData = createRepositoryData(repositoryName, scmRepository, null, null, sharedCredentialsId);
            PartialVcsRepositoryData temp = vcsRepositoryConfigurationService.createLinkedRepository(vcsRepositoryData, impersonatedUser, RepositoryConfigurationService.LinkedRepositoryAccess.ALL_USERS);
            result = temp.getCompleteData();
        }
        return result;
    }

    private PartialVcsRepositoryData createRepositoryData(String repositoryName, SCMRepository scmRepository, String username, String password, String credentialsId) {
        String pluginKey = null;
        HashMap<String, String> serverConfiguration = new HashMap<>();
        if (scmRepository.getType().equals(SCMType.GIT)) {
            pluginKey = "com.atlassian.bamboo.plugins.atlassian-bamboo-plugin-git:gitv2";
            serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_REPOSITORY_URL, scmRepository.getUrl());
            serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_BRANCH, "master");
            serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_USE_SHALLOW_CLONES, Boolean.toString(true));
            serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_FETCH_WHOLE_REPOSITORY, Boolean.toString(false));

            if (SdkStringUtils.isNotEmpty(credentialsId)) { //existing credentials
                serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.PASSWORD.name());
                serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_PASSWORD_CREDENTIALS_SOURCE, GitPasswordCredentialsSource.SHARED_CREDENTIALS.name());
                serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_PASSWORD_SHAREDCREDENTIALS_ID, credentialsId);
            } else if (SdkStringUtils.isNotEmpty(username)) { //new credentials
                serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.PASSWORD.name());
                serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_PASSWORD_CREDENTIALS_SOURCE, GitPasswordCredentialsSource.CUSTOM.name());
                serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_USERNAME, username);
                serverConfiguration.put(GitConfigurationConstants.TEMPORARY_GIT_PASSWORD, password);
                serverConfiguration.put(GitConfigurationConstants.TEMPORARY_GIT_PASSWORD_CHANGE, Boolean.toString(true));
            } else {//no credentials
                serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.NONE.name());
            }

        } else if (scmRepository.getType().equals(SCMType.SVN)) {
            pluginKey = "com.atlassian.bamboo.plugin.system.repository:svnv2";
            serverConfiguration.put(SvnConfigurationConstants.SVN_REPO_ROOT_URL, scmRepository.getUrl());
            serverConfiguration.put(SvnConfigurationConstants.SVN_AUTH_TYPE, AuthenticationType.PASSWORD.getKey());
            serverConfiguration.put(SvnConfigurationConstants.TAG_CREATE_AUTODETECT_PATH, Boolean.toString(true));
            serverConfiguration.put(SvnConfigurationConstants.BRANCH_CREATE_AUTODETECT_PATH, Boolean.toString(true));

            String myUsername = username;
            String myPassword = password;
            if (SdkStringUtils.isNotEmpty(credentialsId)) {//extract existing credentials
                CredentialsData cred = credentialsManager.getCredentials(Long.parseLong(credentialsId));
                myUsername = cred.getConfiguration().get(CREDENTIALS_USERNAME_FIELD);
                String encryptedPassword = cred.getConfiguration().get(CREDENTIALS_PASSWORD_FIELD);
                myPassword = encryptionService.decrypt(encryptedPassword);
            }

            serverConfiguration.put(SvnConfigurationConstants.SVN_USERNAME, myUsername);
            serverConfiguration.put(SvnConfigurationConstants.TEMPORARY_SVN_PASSWORD, myPassword);
            serverConfiguration.put(SvnConfigurationConstants.TEMPORARY_SVN_PASSWORD_CHANGE, Boolean.toString(true));
        }


        ActionParametersMap apm = new ActionParametersMapImpl(serverConfiguration);
        VcsRepositoryModuleDescriptor vcsDescriptor = vcsRepositoryManager.getVcsRepositoryModuleDescriptor(pluginKey);
        Map cfgMap;


        //Create builder
        PartialVcsRepositoryDataBuilder vcsRepositoryDataBuilder = PartialVcsRepositoryDataBuilder.newBuilder()
                .copyWithEmptyConfig(null)
                .name(repositoryName)
                .description("")
                .pluginKey(pluginKey);

        //Overrides.SERVER
        VcsLocationConfigurator vcsConfigurator = vcsDescriptor.getVcsLocationConfigurator();
        cfgMap = vcsConfigurator.generateConfigMap(apm, null);
        vcsRepositoryDataBuilder.serverConfiguration(cfgMap);

        //Overrides.CHANGE_DETECTION
        VcsChangeDetectionOptionsConfigurator changeDetectionOptionsConfigurator = vcsDescriptor.getVcsChangeDetectionOptionsConfigurator();
        cfgMap = changeDetectionOptionsConfigurator.generateConfigMap(apm, null);
        vcsRepositoryDataBuilder.changeDetectionConfiguration(cfgMap);

        //Overrides.BRANCH
        VcsBranchConfigurator branchConfigurator = vcsDescriptor.getVcsBranchConfigurator();
        cfgMap = new HashMap();
        vcsRepositoryDataBuilder.branchConfiguration(cfgMap);
        vcsRepositoryDataBuilder.vcsBranch(branchConfigurator.getVcsBranchFromConfig(cfgMap));

        //Build
        PartialVcsRepositoryData vcsRepositoryData = vcsRepositoryDataBuilder.build();
        return vcsRepositoryData;

    }


    private ImmutableChain createExecutorChain(DiscoveryInfo discoveryInfo, BambooUser bambooUser) throws PlanCreationDeniedException {
        VcsRepositoryData linkedRepository = getLinkedRepository(discoveryInfo.getScmRepository(), discoveryInfo.getScmRepositoryCredentialsId(), bambooUser);
        String chainKeyStr = createChainBuildKey(EXECUTOR_PREFIX_KEY, discoveryInfo.getExecutorId(), discoveryInfo.getExecutorLogicalName());

        log.warn("Creating execution plan for test runner " + discoveryInfo.getExecutorId());
        String description = String.format("This plan was created by the Micro Focus plugin for execution of UFT tests. It is associated with ALM Octane test runner #%s.", discoveryInfo.getExecutorId());
        String name = String.format("UFT test executor - Test Runner ID %s (%s)", discoveryInfo.getExecutorId(), discoveryInfo.getExecutorLogicalName());

        final BuildConfiguration buildConfiguration = new BuildConfiguration();
        String chainBuildKeyStr = createChain(getMainProject(), linkedRepository, chainKeyStr, description, name, buildConfiguration);

        //CREATE JOBS AND TASKS
        ActionParametersMap actionParametersMap = configureDefaultStageAndJob(buildConfiguration, chainBuildKeyStr);
        configureExecutionTask(buildConfiguration);
        //create, default checkout task is configured in JobCreationServiceImpl.createDefaultCheckoutTask
        this.jobCreationService.createJobAndBranches(buildConfiguration, actionParametersMap, PlanCreationService.EnablePlan.ENABLED);


        PlanKey chainKey = PlanKeys.getPlanKey(chainBuildKeyStr);
        Chain createdChain = planManager.getPlanByKeyIfOfType(chainKey, Chain.class);

        //add variables to chain
        createVariablesForExecution(createdChain);

        //add artifact definition and requirements for job
        Job job = createdChain.getAllStages().get(0).getJobs().iterator().next();
        registerArtifactForExecution(job);
        createRequirementsForExecution(job.getPlanKey());

        //SEND CREATION EVENT
        chainCreationService.triggerCreationCompleteEvents(chainKey);

        //RETURN
        String fullPlanKeyStr = PROJECT_KEY + "-" + chainKeyStr;
        ImmutableChain chain = cachedPlanManager.getPlanByKey(PlanKeys.getPlanKey(fullPlanKeyStr), ImmutableChain.class);
        return chain;
    }

    private void createRequirementsForExecution(PlanKey jobKey) {
        try {
            requirementService.addRequirement(jobKey, "system.builder.Micro Focus.Unified Functional Testing", ImmutableRequirement.MatchType.EXISTS, ".*");
        } catch (Exception e) {
            throw new RuntimeException("Failed to createRequirementsForExecution : " + e.getMessage());
        }

    }

    private String createChain(Project project, VcsRepositoryData linkedRepository, String chainKey, String chainDescription, String chainName, BuildConfiguration buildConfiguration) throws PlanCreationDeniedException {
        //DEFINE CHAIN
        final Map<String, Object> context = new HashMap<>();
        context.put(PlanCreationService.EXISTING_PROJECT_KEY, project.getKey());
        context.put(ChainCreationService.CHAIN_KEY, chainKey);
        context.put(ChainCreationService.CHAIN_NAME, chainName);
        context.put(ChainCreationService.CHAIN_DESCRIPTION, chainDescription);
        context.put("linkedRepositoryAccessOption", RepositoryConfigurationService.LinkedRepositoryAccess.ALL_USERS.name());
        context.put("repositoryTypeOption", "LINKED");
        context.put("selectedRepository", linkedRepository.getId());
        ActionParametersMap apm = new ActionParametersMapImpl(context);
        BuildConfigurationActionHelper.copyParamsToBuildConfiguration(apm, buildConfiguration);
        return chainCreationService.createPlan(buildConfiguration, apm, PlanCreationService.EnablePlan.ENABLED);
    }

    private String createChainBuildKey(String prefix, String executorId, String executorLogicalName) {
        return String.format("%s%sLOGICAL%s", prefix, executorId, executorLogicalName).toUpperCase();
    }

    private boolean registerArtifactForDiscovery(@NotNull Job job) {
        String name = "UFT discovery result";
        String pattern = "**/" + UftDiscoveryTask.RESULT_FILE_NAME_PREFIX + "${bamboo.buildNumber}*";
        return Utils.registerArtifactDefinition(job, name, pattern);
    }

    private boolean registerArtifactForExecution(@NotNull Job job) {
        String name = "Micro Focus Tasks Artifact Definition";
        String pattern = "UFT_Build_${bamboo.buildNumber}/**";
        return Utils.registerArtifactDefinition(job, name, pattern);
    }

    private void createVariablesForExecution(@NotNull Chain chain) {
        createVariables(chain, ParametersHelper.TESTS_TO_RUN_PARAMETER, "");
        //createVariables(chain, SUITE_ID_PARAMETER, "");
        //createVariables(chain, SUITE_RUN_ID_PARAMETER, "");
    }

    private void createVariables(@NotNull Chain chain, String key, @NotNull String value) {
        try {
            variableConfigurationService.createPlanVariable(chain, key, value);
        } catch (Exception e) {
            log.warn(String.format("Failed to add variable <%s> to chain <%s>", key, chain.getName()));
        }
    }

    public PipelineNode createExecutor(DiscoveryInfo discoveryInfo, String runAsUser) {
        BambooUser user = bambooUserManager.getBambooUser(runAsUser);
        try {
            ImmutableChain chain = createExecutorChain(discoveryInfo, user);
            return DefaultOctaneConverter.getInstance().getRootPipelineNodeFromTopLevelPlan((ImmutableTopLevelPlan) chain);
        } catch (Exception e) {
            String msg = "Failed to createExecutor : " + e.getMessage();
            log.error(msg);
            throw new RuntimeException(msg, e);
        }
    }
}
