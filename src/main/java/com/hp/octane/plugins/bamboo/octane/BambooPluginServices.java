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

import com.atlassian.bamboo.applinks.ImpersonationService;
import com.atlassian.bamboo.build.LogEntry;
import com.atlassian.bamboo.build.logger.BuildLogFileAccessor;
import com.atlassian.bamboo.build.logger.BuildLogFileAccessorFactory;
import com.atlassian.bamboo.chains.ChainResultsSummary;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.configuration.ConcurrentBuildConfig;
import com.atlassian.bamboo.plan.*;
import com.atlassian.bamboo.plan.branch.ChainBranchManager;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.bamboo.plugin.BambooApplication;
import com.atlassian.bamboo.resultsummary.BuildResultsSummary;
import com.atlassian.bamboo.resultsummary.BuildResultsSummaryManager;
import com.atlassian.bamboo.resultsummary.ResultsSummary;
import com.atlassian.bamboo.resultsummary.variables.ResultsSummaryVariableAccessor;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.bamboo.user.BambooUser;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.atlassian.bamboo.vcs.configuration.PlanRepositoryDefinition;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.CIPluginServices;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.configuration.CIProxyConfiguration;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import com.hp.octane.integrations.dto.executor.CredentialsInfo;
import com.hp.octane.integrations.dto.executor.DiscoveryInfo;
import com.hp.octane.integrations.dto.executor.TestConnectivityInfo;
import com.hp.octane.integrations.dto.general.*;
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.parameters.CIParameters;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.scm.Branch;
import com.hp.octane.integrations.dto.snapshots.CIBuildResult;
import com.hp.octane.integrations.dto.snapshots.CIBuildStatus;
import com.hp.octane.integrations.exceptions.ConfigurationException;
import com.hp.octane.integrations.exceptions.PermissionException;
import com.hp.octane.integrations.utils.CIPluginSDKUtils;
import com.hp.octane.integrations.utils.SdkConstants;
import com.hp.octane.integrations.utils.SdkStringUtils;
import com.hp.octane.plugins.bamboo.octane.uft.UftManager;
import com.hp.octane.plugins.bamboo.rest.OctaneConnection;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.acegisecurity.acls.Permission;
import org.acegisecurity.userdetails.UserDetails;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class BambooPluginServices extends CIPluginServices {
    private static final Logger log = SDKBasedLoggerProvider.getLogger(BambooPluginServices.class);
    private final String pluginVersion;
    private final String bambooVersion;
    public final static String PLUGIN_KEY = "com.hpe.adm.octane.ciplugins.bamboo-ci-plugin";

    private CachedPlanManager planMan;

    private ImpersonationService impService;
    private PlanExecutionManager planExecMan;
    private BuildQueueManager buildQueueManager;
    private BambooUserManager bambooUserManager;
    private ResultsSummaryVariableAccessor accessor;
    private BuildResultsSummaryManager resultsSummaryManager;
    private ChainBranchManager chainBranchManager;

    private BuildLogFileAccessorFactory buildLogFileAccessorFactory;

    Pattern parentExtractorRegex = Pattern.compile("^(.*?)[0-9]+$");//SIM-STM1 => SIM-STM

    private static DefaultOctaneConverter CONVERTER = DefaultOctaneConverter.getInstance();

    public BambooPluginServices() {
        this.planExecMan = ComponentLocator.getComponent(PlanExecutionManager.class);
        this.planMan = ComponentLocator.getComponent(CachedPlanManager.class);
        this.impService = ComponentLocator.getComponent(ImpersonationService.class);
        this.buildQueueManager = ComponentLocator.getComponent(BuildQueueManager.class);
        this.bambooUserManager = ComponentLocator.getComponent(BambooUserManager.class);
        pluginVersion = ComponentLocator.getComponent(PluginAccessor.class).getPlugin(PLUGIN_KEY).getPluginInformation().getVersion();
        bambooVersion = ComponentLocator.getComponent(BambooApplication.class).getVersion();
        this.accessor = ComponentLocator.getComponent(ResultsSummaryVariableAccessor.class);
        this.resultsSummaryManager = ComponentLocator.getComponent(BuildResultsSummaryManager.class);
        this.chainBranchManager = ComponentLocator.getComponent(ChainBranchManager.class);
        this.buildLogFileAccessorFactory = ComponentLocator.getComponent(BuildLogFileAccessorFactory.class);
    }

    @Override
    public InputStream getBuildLog(String jobId, String buildId) {
        InputStream result = null;
        BuildLogFileAccessor fileAccessor = null;
        try {
            PlanKey planKey = PlanKeys.getPlanKey(jobId);
            int buildNumber = PlanKeys.getPlanResultKey(buildId).getBuildNumber();

            fileAccessor = buildLogFileAccessorFactory.createBuildLogFileAccessor(planKey, buildNumber);
            if (fileAccessor.openFileForIteration()) {
                List<LogEntry> resultEntries = fileAccessor.getLastNLogs(fileAccessor.getNumberOfLinesInFile());
                if (resultEntries != null && !resultEntries.isEmpty()) {
                    result = new ByteArrayInputStream(
                            resultEntries.stream().map(LogEntry::getLog).collect(Collectors.joining("\n"))
                                    .getBytes());
                }
            }
        } catch (IOException ioe) {
            log.error("cannot get build logger Job Id = {} Build Id = {}", jobId, buildId);
        } catch (IllegalArgumentException iae) {
            log.error(iae.getMessage());
        } finally {
            if (fileAccessor != null) {
                fileAccessor.closeFileForIteration();
            }
        }
        return result;
    }

    @Override
    public File getAllowedOctaneStorage() {
        return SDKBasedLoggerProvider.getAllowedStorageFile();
    }

    @Override
    public CIJobsList getJobsList(boolean includeParameters, Long workspaceId) {
        log.info("Get jobs list");

        Callable<List<ImmutableTopLevelPlan>> plansGetter = () -> planMan.getPlans();
        List<ImmutableTopLevelPlan> plans = executeImpersonatedCall(plansGetter, "getJobsList");
        return CONVERTER.getRootJobsList(plans, includeParameters);
    }

    @Override
    public PipelineNode getPipeline(String pipelineId) {
        //workaround for bamboo
        final String pipelineIdUpper = pipelineId.toUpperCase();
        log.info("get pipeline " + pipelineIdUpper);
        Callable<ImmutableTopLevelPlan> planGetter = () -> planMan.getPlanByKey(PlanKeys.getPlanKey(pipelineIdUpper), ImmutableTopLevelPlan.class);
        ImmutableTopLevelPlan plan = executeImpersonatedCall(planGetter, "getPipeline");
        PipelineNode pipelineNode = CONVERTER.getRootPipelineNodeFromTopLevelPlan(plan);
        pipelineNode.setDefaultBranchName(getDefaultDisplayName(plan));
        return pipelineNode;
    }

    @Override
    public CIPluginInfo getPluginInfo() {
        log.debug("get plugin info");
        return DefaultOctaneConverter.getDTOFactory().newDTO(CIPluginInfo.class).setVersion(pluginVersion);
    }

    @Override
    public CIProxyConfiguration getProxyConfiguration(URL targetUrl) {
        log.debug("get proxy configuration");
        CIProxyConfiguration result = null;

        if (isProxyNeeded(targetUrl)) {
            log.debug("proxy is required for host " + targetUrl.getHost());
            String protocol = targetUrl.getProtocol();

            result = CONVERTER.getProxyCconfiguration(getProxyProperty(protocol + ".proxyHost", null),
                    Integer.parseInt(getProxyProperty(protocol + ".proxyPort", null)),
                    System.getProperty(protocol + ".proxyUser", ""),
                    System.getProperty(protocol + ".proxyPassword", ""));
        }
        return result;
    }

    private String getProxyProperty(String propKey, String def) {
        if (def == null) def = "";
        return System.getProperty(propKey) != null ? System.getProperty(propKey).trim() : def;
    }

    private boolean isProxyNeeded(URL targetHostUrl) {
        boolean result = false;
        String proxyHost = getProxyProperty(targetHostUrl.getProtocol() + ".proxyHost", "");
        String nonProxyHostsStr = getProxyProperty(targetHostUrl.getProtocol() + ".nonProxyHosts", "");

        if (SdkStringUtils.isNotEmpty(proxyHost) && !CIPluginSDKUtils.isNonProxyHost(targetHostUrl.getHost(), nonProxyHostsStr)) {
            result = true;
        }

        return result;
    }

    @Override
    public CIServerInfo getServerInfo() {
        log.debug("get ci server info");
        String baseUrl = getBambooServerBaseUrl();
        return CONVERTER.getServerInfo(baseUrl, bambooVersion);
    }

    @Override
    public void stopPipelineRun(String pipeline, CIParameters ciParameters) {
        log.info("starting pipeline stop");

        Callable<String> action = () -> {
            BambooUserManager um = ComponentLocator.getComponent(BambooUserManager.class);
            BambooUser user = um.getBambooUser(getRunAsUser());
            PlanKey planKey = PlanKeys.getPlanKey(pipeline.toUpperCase());
            ImmutableChain chain = planMan.getPlanByKey(planKey, ImmutableChain.class);

            if (chain != null) {
                if (!isUserHasPermission(BambooPermission.BUILD, user, chain)) {
                    throw new PermissionException(HttpStatus.SC_FORBIDDEN);
                }

                log.info(String.format("plan key=%s ,build key=%s ,chain key=%s", chain.getPlanKey().getKey(), chain.getBuildKey(), chain.getKey()));

                CIParameter octaneExecutionId = ciParameters.getParameters().stream()
                        .filter(parameter -> parameter.getName().equals(SdkConstants.JobParameters.OCTANE_AUTO_ACTION_EXECUTION_ID_PARAMETER_NAME))
                        .findFirst().orElse(null);

                if (octaneExecutionId == null) {
                    planExecMan.stopPlan(chain.getPlanKey(), true, user.getName());
                } else {
                    List<BuildResultsSummary> allQueuedSummaries = (ArrayList<BuildResultsSummary>) resultsSummaryManager.getAllQueuedResultSummaries(BuildResultsSummary.class);
                    List<BuildResultsSummary> allInProgressSummaries = (ArrayList<BuildResultsSummary>) resultsSummaryManager.getAllInProgressResultSummaries(BuildResultsSummary.class);
                    Boolean stoppedInQueued = stopBuildWithOctaneId(user, planKey, octaneExecutionId, allQueuedSummaries);
                    if (!stoppedInQueued) {
                        stopBuildWithOctaneId(user, planKey, octaneExecutionId, allInProgressSummaries);
                    }
                }
            } else {
                throw new ConfigurationException(HttpStatus.SC_NOT_FOUND);
            }
            return null;
        };
        executeImpersonatedCall(action, "Stop Pipeline");
    }

    private Boolean stopBuildWithOctaneId(BambooUser user, PlanKey planKey, CIParameter octaneExecutionId, List<BuildResultsSummary> resultSummaries) {
        return resultSummaries.parallelStream().filter(resultSummary -> resultSummary.getPlanKey().getKey().contains(planKey.getKey()))
                .map(resultsSummary -> {
                    int buildId = resultsSummary.getBuildNumber();
                    PlanResultKey planResultKey = PlanKeys.getPlanResultKey(planKey, buildId);
                    Map<String, VariableDefinitionContext> contextMap = accessor.calculateCurrentVariablesState(planResultKey);
                    VariableDefinitionContext variable = contextMap.getOrDefault(SdkConstants.JobParameters.OCTANE_AUTO_ACTION_EXECUTION_ID_PARAMETER_NAME, null);

                    if (variable != null && octaneExecutionId.getValue().equals(variable.getValue())) {
                        planExecMan.stopPlan(planResultKey, true, user.getName());
                        return true;
                    }
                    return false;
                }).filter(flag -> flag).findAny().orElse(false);
    }

    @Override
    public CIBuildStatusInfo getJobBuildStatus(String pipeline, String parameterName, String parameterValue) {
        log.info("getting pipeline build status");

        Callable<CIBuildStatusInfo> action = () -> {
            BambooUserManager um = ComponentLocator.getComponent(BambooUserManager.class);
            BambooUser user = um.getBambooUser(getRunAsUser());
            PlanKey planKey = PlanKeys.getPlanKey(pipeline.toUpperCase());
            ImmutableChain chain = planMan.getPlanByKey(planKey, ImmutableChain.class);

            if (chain != null) {
                if (!isUserHasPermission(BambooPermission.READ, user, chain)) {
                    throw new PermissionException(HttpStatus.SC_FORBIDDEN);
                }

                CIParameter ciParameter = DTOFactory.getInstance().newDTO(CIParameter.class)
                        .setName(parameterName)
                        .setValue(parameterValue);

                List<ChainResultsSummary> allSummaries = resultsSummaryManager.getResultSummariesForPlan(chain, 0, 0);
                ResultsSummary buildToCheck = allSummaries.parallelStream().map(resultsSummary -> {
                    int buildId = resultsSummary.getBuildNumber();
                    PlanResultKey planResultKey = PlanKeys.getPlanResultKey(planKey, buildId);
                    Map<String, VariableDefinitionContext> contextMap = accessor.calculateCurrentVariablesState(planResultKey);
                    VariableDefinitionContext variable = contextMap.getOrDefault(SdkConstants.JobParameters.OCTANE_AUTO_ACTION_EXECUTION_ID_PARAMETER_NAME, null);

                    if (variable != null && ciParameter.getValue().equals(variable.getValue())) {
                        return resultsSummary;
                    }
                    return null;
                }).filter(Objects::nonNull).findAny().orElse(null);

                CIBuildStatus buildStatus = CIBuildStatus.UNAVAILABLE;
                CIBuildResult buildResult = CIBuildResult.UNAVAILABLE;
                String buildCiId = null;
                if (buildToCheck != null) {
                    buildStatus = CONVERTER.getCIBuildStatus(buildToCheck.getLifeCycleState());
                    buildCiId = PlanKeys.getPlanResultKey(planKey, buildToCheck.getBuildNumber()).getKey();
                    buildResult = CONVERTER.getJobResult(buildToCheck.getBuildState());
                }

                return DTOFactory.getInstance().newDTO(CIBuildStatusInfo.class)
                        .setJobCiId(planKey.getKey())
                        .setBuildStatus(buildStatus)
                        .setBuildCiId(buildCiId)
                        .setParamName(parameterName)
                        .setParamValue(parameterValue)
                        .setResult(buildResult);
            } else {
                throw new ConfigurationException(HttpStatus.SC_NOT_FOUND);
            }
        };

        return executeImpersonatedCall(action, "Get Job Build Status");
    }

    @Override
    public CIBranchesList getBranchesList(String jobCiId, String filterBranchName) {
        log.info("getting pipeline build status");

        Callable<CIBranchesList> action = () -> {
            ImmutableChain chain = planMan.getPlanByKey(PlanKeys.getPlanKey(jobCiId.toUpperCase()), ImmutableChain.class);
            if (chain != null) {
                List<Branch> branches = new ArrayList<>();
                Branch branch = chainBranchManager.getBranchesForChain(chain).parallelStream()
                        .filter(chainBranch -> chainBranch.getBuildName().equals(filterBranchName))
                        .map(chainBranch -> DTOFactory.getInstance().newDTO(Branch.class)
                                .setName(chainBranch.getBuildName())
                                .setInternalId(chainBranch.getPlanKey().getKey()))
                        .findAny().orElse(null);

                if (branch == null) {
                    String defaultDisplayName = getDefaultDisplayName(chain);
                    if (Objects.equals(defaultDisplayName, filterBranchName)) {
                        branch = DTOFactory.getInstance().newDTO(Branch.class)
                                .setName(defaultDisplayName)
                                .setInternalId(chain.getPlanKey().getKey());
                    }
                }

                if (branch != null) branches.add(branch);
                return DTOFactory.getInstance().newDTO(CIBranchesList.class)
                        .setBranches(branches);
            } else {
                throw new ConfigurationException(HttpStatus.SC_NOT_FOUND);
            }
        };
        return executeImpersonatedCall(action, "Get Branches List");
    }

    @NotNull
    private String getDefaultDisplayName(ImmutableChain chain) {
        PlanRepositoryDefinition repositoryDefinition = PlanHelper.getDefaultPlanRepositoryDefinition(chain);
        return repositoryDefinition != null ? repositoryDefinition.getBranch().getVcsBranch().getDisplayName() : "";
    }

    @Override
    public void runPipeline(final String pipeline, CIParameters ciParameters) {
        log.info("starting pipeline run");

        Callable<String> impersonated = () -> {
            BambooUserManager um = ComponentLocator.getComponent(BambooUserManager.class);
            BambooUser user = um.getBambooUser(getRunAsUser());
            ImmutableChain chain = planMan.getPlanByKey(PlanKeys.getPlanKey(pipeline.toUpperCase()), ImmutableChain.class);
            if (chain == null || chain.isSuspendedFromBuilding()) {
                throw new ConfigurationException(HttpStatus.SC_NOT_FOUND);
            }
            log.info(String.format("plan key=%s ,build key=%s ,chain key=%s", chain.getPlanKey().getKey(), chain.getBuildKey(), chain.getKey()));

            if (!isUserHasPermission(BambooPermission.BUILD, user, chain)) {
                throw new PermissionException(HttpStatus.SC_FORBIDDEN);
            }

            HashMap<String, String> variables = new HashMap<>();
            HashMap<String, String> params = new HashMap<>();
            if (ciParameters != null) {
                for (CIParameter param : ciParameters.getParameters()) {
                    //if testsToRun parameter more then 3900 ,split it for many variables
                    if (param.getName().equals(OctaneConstants.TESTS_TO_RUN_PARAMETER) && param.getValue().toString().length() > OctaneConstants.BAMBOO_MAX_FIELD_CAPACITY) {
                        String[] split = param.getValue().toString().split("(?<=\\G.{3900})");
                        log.info("testsToRun parameter is too long, split it to " + split.length);
                        for (int i = 0; i < split.length; i++) {
                            variables.put(param.getName() + i, split[i]);
                        }
                        variables.put(OctaneConstants.TEST_TO_RUN_SPLIT_COUNT, split.length + "");
                        param.setValue("value is too long and splitted to " + split.length + " parts");
                    } else {
                        variables.put(param.getName(), param.getValue().toString());
                    }
                }
            }

            try {//check if start a new job will not exceed concurrent build capacity. only print it to log
                ConcurrentBuildConfig concurrentBuildConfig = ComponentLocator.getComponent(AdministrationConfigurationAccessor.class).getAdministrationConfiguration().getConcurrentBuildConfig();
                log.info(String.format("concurrentBuildConfig: isEnabled=%s, NumberConcurrentBuilds=%d", concurrentBuildConfig.isEnabled(), concurrentBuildConfig.getNumberConcurrentBuilds()));
                long queueCount = StreamSupport.stream(buildQueueManager.getQueuedExecutables().spliterator(), false).count();
                long inProcessCount = (planExecMan.isBusy() ? 1 : 0) + queueCount;
                long capacityCount = concurrentBuildConfig.isEnabled() ? concurrentBuildConfig.getNumberConcurrentBuilds() : 1;
                log.info(String.format("in process count=%d, capacity count=%d, execution plan is busy=%s", inProcessCount, capacityCount, planExecMan.isBusy()));
                if (inProcessCount >= capacityCount) {
                    log.warn("POSSIBLY RUN WILL FAIL because of queue limit. Check Concurrent builds configuration");
                }
            } catch (Exception e) {
                log.warn("Fail in logging queue information : " + e.getMessage());
            }

            ExecutionRequestResult result = planExecMan.startManualExecution(chain, user, params, variables);
            if (result.getErrors().getTotalErrors() > 0) {
                throw new ConfigurationException(504);
            }

            return null;
        };

        executeImpersonatedCall(impersonated, "runPipeline");
    }

    private boolean isUserHasPermission(Permission permissionType, BambooUser user, ImmutableChain chain) {
        Collection<Permission> permissionForPlan = ComponentLocator.getComponent(BambooPermissionManager.class).getPermissionsForPlan(chain.getPlanKey());
        for (Permission permission : permissionForPlan) {
            if (permission.equals(permissionType)) {
                return true;
            }
        }
        return false;
    }

    private String getRunAsUser() {
        return getConnection().getBambooUser();
    }

    private OctaneConnection getConnection() {
        return OctaneConnectionManager.getInstance().getConnectionById(getInstanceId());
    }

    @Override
    public InputStream getTestsResult(String jobId, String buildId) {
        //  retrieve test results by build IDs
        InputStream output = null;
        PlanResultKey planResultKey = PlanKeys.getPlanResultKey(buildId);

        File mqmResultFile = MqmResultsHelper.getMqmResultFilePath(planResultKey).toFile();
        log.info(String.format("getTestsResult of %s from  %s, file exist=%s", planResultKey.toString(), mqmResultFile.getAbsolutePath(), mqmResultFile.exists()));
        try {
            output = mqmResultFile.exists() && mqmResultFile.length() > 0 ? new FileInputStream(mqmResultFile.getAbsolutePath()) : null;
        } catch (IOException e) {
            log.error("failed to get test results for  " + jobId + " #" + buildId + " from " + mqmResultFile.getAbsolutePath());
        }
        return output;
    }

    @Override
    public InputStream getSCMData(String jobId, String buildId) {
        InputStream output = null;
        PlanResultKey planResultKey = PlanKeys.getPlanResultKey(buildId);

        File scmDataFile = MqmResultsHelper.getScmDataFilePath(planResultKey).toFile();
        try {
            output = scmDataFile.exists() && scmDataFile.length() > 0 ? new FileInputStream(scmDataFile.getAbsolutePath()) : null;
        } catch (IOException e) {
            log.error("failed to get scm data for  " + jobId + " #" + buildId + " from " + scmDataFile.getAbsolutePath());
        }
        return output;
    }

    @Override
    public OctaneResponse checkRepositoryConnectivity(final TestConnectivityInfo testConnectivityInfo) {
        final Callable<OctaneResponse> action = () -> getUftManager().checkRepositoryConnectivity(testConnectivityInfo);
        return executeImpersonatedCall(action, "checkRepositoryConnectivity");
    }

    @Override
    public OctaneResponse upsertCredentials(final CredentialsInfo credentialsInfo) {
        final Callable<OctaneResponse> action = () -> getUftManager().upsertCredentials(credentialsInfo);
        return executeImpersonatedCall(action, "upsertCredentials");
    }

    @Override
    public List<CredentialsInfo> getCredentials() {
        final Callable<List<CredentialsInfo>> action = () -> getUftManager().getCredentials();
        return executeImpersonatedCall(action, "getCredentials");
    }

    @Override
    public void runTestDiscovery(final DiscoveryInfo discoveryInfo) {
        final Callable<Void> action = () -> {
            getUftManager().runTestDiscovery(discoveryInfo, getRunAsUser());
            return null;
        };

        executeImpersonatedCall(action, "runTestDiscovery");
    }

    @Override
    public PipelineNode createExecutor(DiscoveryInfo discoveryInfo) {
        final Callable<PipelineNode> action = () -> {
            PipelineNode node = getUftManager().createExecutor(discoveryInfo, getRunAsUser());
            return node;
        };
        return executeImpersonatedCall(action, "createExecutor");
    }

    @Override
    public void deleteExecutor(final String id) {
        final Callable<Void> action = () -> {
            getUftManager().deleteExecutor(id);
            return null;
        };

        executeImpersonatedCall(action, "deleteExecutor");
    }


    private UftManager getUftManager() {
        return UftManager.getInstance();
    }

    private <V> V executeImpersonatedCall(Callable<V> callable, String actionName) {
        log.info("Impersonated call : " + actionName);

        UserDetails ud = bambooUserManager.loadUserByUsername(getRunAsUser());
        if (ud == null) {
            throw new PermissionException(HttpStatus.SC_UNAUTHORIZED);
        }

        Callable<V> impersonated = impService.runAsUser(getRunAsUser(), callable);
        try {
            return impersonated.call();
        } catch (PermissionException e) {
            log.warn("PermissionException to executeImpersonatedCall " + actionName + " : " + e.getMessage());
            throw e;
        } catch (Throwable e) {
            log.warn("Failed to executeImpersonatedCall " + actionName + " : " + e.getMessage(), e);
            RuntimeException runtimeException;
            if (e instanceof RuntimeException) {
                runtimeException = (RuntimeException) e;
            } else {
                runtimeException = new RuntimeException(e);
            }
            throw runtimeException;
        }
    }

    public static String getBambooServerBaseUrl() {
        String baseUrl = ComponentLocator.getComponent(AdministrationConfigurationAccessor.class)
                .getAdministrationConfiguration().getBaseUrl();
        return baseUrl;
    }

    @Override
    public String getParentJobName(String jobId) {

        try {
            PlanKey planKey = PlanKeys.getPlanKey(jobId);
            ImmutablePlan plan = planMan.getPlanByKey(planKey);
            if (plan != null && plan.getMaster() != null) {
                return plan.getMaster().getKey();
            }
        } catch (IllegalArgumentException e){
            log.warn("Cannot get plan from job ci ID will take using regex");
        }
        Matcher m = parentExtractorRegex.matcher(jobId);
        if (m.matches()) {
            return m.group(1);
        }

        return null;
    }
}
