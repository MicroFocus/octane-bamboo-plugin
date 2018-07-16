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

import com.atlassian.bamboo.build.BuildDefinitionManager;
import com.atlassian.bamboo.build.Job;
import com.atlassian.bamboo.build.PlanCreationDeniedException;
import com.atlassian.bamboo.build.creation.*;
import com.atlassian.bamboo.collections.ActionParametersMap;
import com.atlassian.bamboo.credentials.CredentialTypeModuleDescriptor;
import com.atlassian.bamboo.credentials.CredentialsData;
import com.atlassian.bamboo.credentials.CredentialsManager;
import com.atlassian.bamboo.fieldvalue.TaskConfigurationUtils;
import com.atlassian.bamboo.plan.*;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionImpl;
import com.atlassian.bamboo.plan.artifact.ArtifactDefinitionManager;
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
import com.atlassian.bamboo.task.*;
import com.atlassian.bamboo.trigger.TriggerConfigurationService;
import com.atlassian.bamboo.trigger.TriggerModuleDescriptor;
import com.atlassian.bamboo.trigger.TriggerTypeManager;
import com.atlassian.bamboo.user.BambooUser;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
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
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.executor.CredentialsInfo;
import com.hp.octane.integrations.dto.executor.DiscoveryInfo;
import com.hp.octane.integrations.dto.executor.TestConnectivityInfo;
import com.hp.octane.integrations.dto.executor.TestSuiteExecutionInfo;
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.parameters.CIParameterType;
import com.hp.octane.integrations.dto.scm.SCMRepository;
import com.hp.octane.integrations.dto.scm.SCMType;
import com.hp.octane.integrations.util.SdkStringUtils;
import org.apache.http.HttpStatus;
import org.jetbrains.annotations.NotNull;
import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class UftManager {

    private BambooUserManager bambooUserManager;
    private ChainCreationService chainCreationService;
    private CredentialsManager credentialsManager;
    private EncryptionService encryptionService;
    private PlanExecutionManager planExecManager;
    private PlanManager planManager;
    private ProjectManager projectManager;
    private JobCreationService jobCreationService;
    private TaskConfigurationService taskConfigurationService;
    private TaskManager taskManager;
    private BuildDefinitionManager buildDefinitionManager;


    private TriggerTypeManager triggerTypeManager;
    private TriggerConfigurationService triggerConfigurationService;

    private RepositoryDefinitionManager repositoryDefinitionManager;
    private VcsRepositoryManager vcsRepositoryManager;
    private VcsRepositoryConfigurationService vcsRepositoryConfigurationService;

    private static String CREDENTIALS_USERNAME_FIELD = "username";
    private static String CREDENTIALS_PASSWORD_FIELD = "password";

    private static String USERNAME_PASSWORD_PLUGIN_KEY = "com.atlassian.bamboo.plugin.sharedCredentials:usernamePasswordCredentials";
    private static String DISCOVERY_TASK_PLUGIN_KEY = "com.hpe.adm.octane.ciplugins.bamboo-ci-plugin:octaneUftTestDiscovery";
    private static final String TRIGGER_POLLING_PLUGIN_KEY = "com.atlassian.bamboo.triggers.atlassian-bamboo-triggers:poll";
    public static final String PROJECT_KEY = "UOI";
    public static final String DISCOVERY_KEY_PREFIX = "DISCOVERY";

    private static final Logger log = LoggerFactory.getLogger(UftManager.class);

    public static final String SUITE_ID_PARAMETER = "suiteId";
    public static final String SUITE_RUN_ID_PARAMETER = "suiteRunId";

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
        planExecManager = ComponentLocator.getComponent(PlanExecutionManager.class);
        planManager = ComponentLocator.getComponent(PlanManager.class);
        projectManager = ComponentLocator.getComponent(ProjectManager.class);
        repositoryDefinitionManager = ComponentLocator.getComponent(RepositoryDefinitionManager.class);
        vcsRepositoryConfigurationService = ComponentLocator.getComponent(VcsRepositoryConfigurationService.class);
        vcsRepositoryManager = ComponentLocator.getComponent(VcsRepositoryManager.class);
        jobCreationService = ComponentLocator.getComponent(JobCreationService.class);
        taskConfigurationService = ComponentLocator.getComponent(TaskConfigurationService.class);
        triggerTypeManager = ComponentLocator.getComponent(TriggerTypeManager.class);
        triggerConfigurationService = ComponentLocator.getComponent(TriggerConfigurationService.class);
        buildDefinitionManager = ComponentLocator.getComponent(BuildDefinitionManager.class);
        taskManager = ComponentLocator.getComponent(TaskManager.class);
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
                    Log.error("Failed to update credentials " + e.getMessage(), e);
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
                    result.setStatus(org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR);
                    result.setBody("Failed to create credentials " + e.getMessage());
                    Log.error("Failed to create credentials " + e.getMessage(), e);
                }
            }
        }

        return result;
    }

    private String createCredentialName(String username, boolean creation) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm");
        String createOrUpdate = creation ? " , created on " : " , updated on ";
        String credentialsName = UFT_INTEGRATION_PREFIX + " - " + username + createOrUpdate + formatter.format(new Date());
        return credentialsName;
    }

    private Project getMainProject() {

        String name = "UftOctaneIntegration";
        String desc = "This project was created by the Micro Focus Octane plugin for managing execution of UFT tests and integration with Octane.";
        Project project = projectManager.getProjectByKey(PROJECT_KEY);
        if (project == null) {
            project = projectManager.createProject(PROJECT_KEY, name, desc);
            projectManager.saveProject(project);
        }
        return project;
    }

    private Plan getDiscoveryPlan(Project project, DiscoveryInfo discoveryInfo, VcsRepositoryData linkedRepository) throws PlanCreationDeniedException {
        String description = String.format("This plan was created by the Micro Focus plugin for discovery of UFT tests. It is associated with ALM Octane testing tool connection #%s.", discoveryInfo.getExecutorId());
        String key = createBuildKey(discoveryInfo.getExecutorId(), discoveryInfo.getExecutorLogicalName());
        String name = String.format("UFT test discovery - Connection ID %s (%s)", discoveryInfo.getExecutorId(), discoveryInfo.getExecutorLogicalName());

        final Map<String, Object> context = new HashMap<>();
        context.put(PlanCreationService.EXISTING_PROJECT_KEY, project.getKey());
        context.put(ChainCreationService.CHAIN_KEY, key);
        context.put(ChainCreationService.CHAIN_NAME, name);
        context.put(ChainCreationService.CHAIN_DESCRIPTION, description);


        context.put("linkedRepositoryAccessOption", RepositoryConfigurationService.LinkedRepositoryAccess.ALL_USERS.name());
        context.put("repositoryTypeOption", "LINKED");
        context.put("selectedRepository", linkedRepository.getId());
        ActionParametersMap apm = new ActionParametersMapImpl(context);

        final BuildConfiguration buildConfiguration = new BuildConfiguration();
        BuildConfigurationActionHelper.copyParamsToBuildConfiguration(apm, buildConfiguration);

        String buildKey = chainCreationService.createPlan(buildConfiguration, apm, PlanCreationService.EnablePlan.ENABLED);
        PlanKey jobKey = createDefaultJobAndTasks(buildConfiguration, buildKey, discoveryInfo);
        Plan job = planManager.getPlanByKey(jobKey);


        /*taskConfigurationService.deleteTask(jobPlanKey,1);
        HashMap<String,String> conf = new HashMap<>();
        conf.put("cleanCheckout","");
        conf.put("selectedRepository_0","defaultRepository");
        conf.put("checkoutDir_0" ,"src");
        TaskDefinition newDef = this.taskConfigurationService.editTask(jobPlanKey, 1, "Checkout Default Repository",true, conf, new TaskRootDirectorySelector());
        */

        PlanKey planKey = PlanKeys.getPlanKey(buildKey);
        Plan plan = planManager.getPlanByKey(planKey);

        PlanRepositoryLink repositoryLink = repositoryDefinitionManager.getPlanRepositoryLinks(plan).get(0);
        createPollTrigger(planKey, repositoryLink.getRepositoryDataEntity().getId());

        chainCreationService.triggerCreationCompleteEvents(planKey);
        return plan;
    }

    private void configureDiscoveryTask(BuildConfiguration buildConfiguration, DiscoveryInfo discoveryInfo) {
        //discovery, example from com.atlassian.bamboo.build.creation.JobCreationServiceImpl.createDefaultCheckoutTask
        LinkedList<TaskDefinition> tasks = new LinkedList(TaskConfigurationUtils.getTaskDefinitionsFromConfig("buildTasks.", buildConfiguration));
        TaskDefinition discoveryTask = new TaskDefinitionImpl(TaskConfigurationUtils.getUniqueId(tasks), DISCOVERY_TASK_PLUGIN_KEY,
                "Discover UFT tests and data tables", true, new HashMap());
        discoveryTask.getConfiguration().put(UftDiscoveryTask.SCM_REPOSITORY_ID_PARAM, discoveryInfo.getScmRepositoryId());
        discoveryTask.getConfiguration().put(UftDiscoveryTask.WORKSPACE_ID_PARAM, discoveryInfo.getWorkspaceId());
        tasks.add(discoveryTask);
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

    private PlanKey createDefaultJobAndTasks(BuildConfiguration buildConfiguration, String buildKey, DiscoveryInfo discoveryInfo) throws PlanCreationDeniedException {
        //set configuration fro default job
        ActionParametersMap actionParametersMap = new ActionParametersMapImpl(new HashMap());
        JobParamMapHelper.setBuildKey(actionParametersMap, buildKey);
        JobParamMapHelper.setBuildName(actionParametersMap, "Default Job");
        JobParamMapHelper.setSubBuildKey(actionParametersMap, "JOB1");
        JobParamMapHelper.setStageName(actionParametersMap, "Default Stage");
        JobParamMapHelper.setExistingStage(actionParametersMap, JobCreationConstants.NEW_STAGE_MARKER);
        buildConfiguration.setProperty("inheritRepository", "true");

        //add configuration of discovery task
        configureDiscoveryTask(buildConfiguration, discoveryInfo);

        //create, default checkout task is configured in JobCreationServiceImpl.createDefaultCheckoutTask
        List<PlanKey> jobKeys = this.jobCreationService.createJobAndBranches(buildConfiguration, actionParametersMap, PlanCreationService.EnablePlan.ENABLED);
        return jobKeys.get(0);
    }

    private VcsRepositoryData getLinkedRepository(SCMRepository scmRepository, String sharedCredentialsId, BambooUser impersonatedUser) {
        VcsRepositoryData result = null;
        String repositoryName = UFT_INTEGRATION_PREFIX + "_" + scmRepository.getUrl().trim().replaceAll("[<>:\"/\\|?*]", "_");
        String repositoryNameLowerCase = repositoryName.toLowerCase();
        //try to find existing
        List<VcsRepositoryData> repositories = repositoryDefinitionManager.getLinkedRepositories();
        for (VcsRepositoryData data : repositories) {
            if (data.getName().toLowerCase().equals(repositoryNameLowerCase)) {
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

    public OctaneResponse checkRepositoryConnectivity(TestConnectivityInfo testConnectivityInfo) {
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

    private PartialVcsRepositoryData createRepositoryData(String repositoryName, SCMRepository scmRepository, String username, String password, String credentialsId) {
        String pluginKey = null;
        HashMap<String, String> serverConfiguration = new HashMap<>();
        if (scmRepository.getType().equals(SCMType.GIT)) {
            pluginKey = "com.atlassian.bamboo.plugins.atlassian-bamboo-plugin-git:gitv2";
            serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_REPOSITORY_URL, scmRepository.getUrl());
            serverConfiguration.put(GitConfigurationConstants.REPOSITORY_GIT_BRANCH, "master");
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

    public void runTestDiscovery(DiscoveryInfo discoveryInfo, String impersonatedUser) {
        try {
            BambooUser bambooUser = bambooUserManager.getBambooUser(impersonatedUser);
            Project project = getMainProject();
            VcsRepositoryData linkedRepository = getLinkedRepository(discoveryInfo.getScmRepository(), discoveryInfo.getScmRepositoryCredentialsId(), bambooUser);
            Plan plan = getDiscoveryPlan(project, discoveryInfo, linkedRepository);
        } catch (Exception e) {
            throw new RuntimeException((e));
        }
    }

    public void deleteExecutor(String id) {
        String buildKeyPrefix = createBuildKey(id, "");
        Project project = getMainProject();
        List<TopLevelPlan> plans = planManager.getAllPlansByProject(project, TopLevelPlan.class);
        for (TopLevelPlan plan : plans) {
            if (plan.getBuildKey().startsWith(buildKeyPrefix)) {
                planManager.markPlansForDeletion(plan.getPlanKey());
            }
        }
    }

    private String createBuildKey(String executorId, String executorLogicalName) {
        return String.format("%s%sLOGICAL%s", DISCOVERY_KEY_PREFIX, executorId, executorLogicalName);
    }

    public void runTestSuiteExecution(TestSuiteExecutionInfo testSuiteExecutionInfo, String impersonatedUser) {

    }

    public static void addUftParametersToEvent(CIEvent ciEvent, com.atlassian.bamboo.v2.build.BuildContext buildContext) {
        try {
            Map<String, VariableDefinitionContext> variables = buildContext.getVariableContext().getEffectiveVariables();
            List<CIParameter> parameters = new ArrayList<>();

            if (variables.containsKey(SUITE_ID_PARAMETER)) {
                String value = variables.get(SUITE_ID_PARAMETER).getValue();
                parameters.add(DTOFactory.getInstance().newDTO(CIParameter.class).setName(SUITE_ID_PARAMETER).setValue(value).setType(CIParameterType.STRING));
            }
            if (variables.containsKey(UftManager.SUITE_RUN_ID_PARAMETER)) {
                String value = variables.get(UftManager.SUITE_RUN_ID_PARAMETER).getValue();
                parameters.add(DTOFactory.getInstance().newDTO(CIParameter.class).setName(SUITE_RUN_ID_PARAMETER).setValue(value).setType(CIParameterType.STRING));
            }
            if (!parameters.isEmpty()) {
                ciEvent.setParameters(parameters);
            }
        } catch (Exception e) {
            //do nothing - try/catch just to be on safe side for all other plans
        }
    }

    public boolean completeDiscoveryJob(Plan plan) {
        boolean executed = registerArtifact((Job) plan);
        return executed;
    }

    private boolean registerArtifact(@NotNull Job job) {

        ArtifactDefinitionManager artifactDefinitionManager = ComponentLocator.getComponent(ArtifactDefinitionManager.class);
        String name = "UFT discovery result";
        String ARTIFACT_COPY_PATTERN = "**/" + UftDiscoveryTask.RESULT_FILE_NAME_PREFIX + "${bamboo.buildNumber}*";
        if (artifactDefinitionManager.findArtifactDefinition(job, name) == null) {
            ArtifactDefinitionImpl artifactDefinition = new ArtifactDefinitionImpl(name, "", ARTIFACT_COPY_PATTERN);
            artifactDefinition.setProducerJob(job);
            artifactDefinitionManager.saveArtifactDefinition(artifactDefinition);
            return true;

        } else {
            return false;
        }

    }


}
