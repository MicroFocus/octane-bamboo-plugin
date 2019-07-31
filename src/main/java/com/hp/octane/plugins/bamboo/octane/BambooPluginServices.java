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
import com.atlassian.bamboo.chains.BuildExecution;
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.configuration.ConcurrentBuildConfig;
import com.atlassian.bamboo.fileserver.SystemDirectory;
import com.atlassian.bamboo.plan.ExecutionRequestResult;
import com.atlassian.bamboo.plan.PlanExecutionManager;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.bamboo.plugin.BambooApplication;
import com.atlassian.bamboo.results.tests.TestResults;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.bamboo.user.BambooUser;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.bamboo.v2.build.CurrentBuildResult;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.hp.octane.integrations.CIPluginServices;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.configuration.CIProxyConfiguration;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import com.hp.octane.integrations.dto.executor.CredentialsInfo;
import com.hp.octane.integrations.dto.executor.DiscoveryInfo;
import com.hp.octane.integrations.dto.executor.TestConnectivityInfo;
import com.hp.octane.integrations.dto.general.CIJobsList;
import com.hp.octane.integrations.dto.general.CIPluginInfo;
import com.hp.octane.integrations.dto.general.CIServerInfo;
import com.hp.octane.integrations.dto.general.CIServerTypes;
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.parameters.CIParameters;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.snapshots.SnapshotNode;
import com.hp.octane.integrations.dto.tests.*;
import com.hp.octane.integrations.exceptions.ConfigurationException;
import com.hp.octane.integrations.exceptions.PermissionException;
import com.hp.octane.integrations.utils.CIPluginSDKUtils;
import com.hp.octane.integrations.utils.SdkStringUtils;
import com.hp.octane.plugins.bamboo.listener.GeneralEventsListener;
import com.hp.octane.plugins.bamboo.listener.MultibranchHelper;
import com.hp.octane.plugins.bamboo.octane.gherkin.ALMOctaneCucumberTestReporterConfigurator;
import com.hp.octane.plugins.bamboo.octane.uft.UftManager;
import com.hp.octane.plugins.bamboo.rest.OctaneConnection;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.acegisecurity.acls.Permission;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.StreamSupport;

public class BambooPluginServices extends CIPluginServices {
    private static final Logger log = LogManager.getLogger(BambooPluginServices.class);
    private static final DTOFactory dtoFactory = DTOFactory.getInstance();
    private final String pluginVersion;
    private final String bambooVersion;
    public final static String PLUGIN_KEY = "com.hpe.adm.octane.ciplugins.bamboo-ci-plugin";

    private CachedPlanManager planMan;

    private ImpersonationService impService;
    private PlanExecutionManager planExecMan;
    private BuildQueueManager buildQueueManager;
    private static boolean allowedOctaneStorageExist = false;

    private static DTOConverter CONVERTER = DefaultOctaneConverter.getInstance();
    private PluginSettingsFactory settingsFactory;

    public BambooPluginServices() {
        this.planExecMan = ComponentLocator.getComponent(PlanExecutionManager.class);
        this.planMan = ComponentLocator.getComponent(CachedPlanManager.class);
        this.impService = ComponentLocator.getComponent(ImpersonationService.class);
        this.buildQueueManager = ComponentLocator.getComponent(BuildQueueManager.class);
        pluginVersion = ComponentLocator.getComponent(PluginAccessor.class).getPlugin(PLUGIN_KEY).getPluginInformation().getVersion();
        bambooVersion = ComponentLocator.getComponent(BambooApplication.class).getVersion();

    }


    @Override
    public File getAllowedOctaneStorage() {
        return getAllowedStorageFile();
    }

    public static File getAllowedStorageFile() {
        File f = new File(SystemDirectory.getApplicationHome(), "octanePluginContent");
        if (!allowedOctaneStorageExist) {
            f.mkdirs();
            allowedOctaneStorageExist = true;
        }
        return f;
    }

    @Override
    public CIJobsList getJobsList(boolean arg0) {
        log.info("Get jobs list");
        Callable<List<ImmutableTopLevelPlan>> plansGetter = impService.runAsUser(getRunAsUser(), new Callable<List<ImmutableTopLevelPlan>>() {

            public List<ImmutableTopLevelPlan> call() throws Exception {
                return planMan.getPlans();
            }
        });
        try {
            List<ImmutableTopLevelPlan> plans = plansGetter.call();
            return CONVERTER.getRootJobsList(plans);
        } catch (Exception e) {
            log.error("Error while retrieving top level plans", e);
        }
        return CONVERTER.getRootJobsList(Collections.<ImmutableTopLevelPlan>emptyList());
    }

    @Override
    public PipelineNode getPipeline(String pipelineId) {
        //workaround for bamboo
        pipelineId = pipelineId.toUpperCase();
        log.info("get pipeline " + pipelineId);
        ImmutableTopLevelPlan plan = planMan.getPlanByKey(PlanKeys.getPlanKey(pipelineId), ImmutableTopLevelPlan.class);
        PipelineNode pipelineNode = CONVERTER.getRootPipelineNodeFromTopLevelPlan(plan);
        MultibranchHelper.enrichMultiBranchParentPipeline(plan, pipelineNode);
        return pipelineNode;
    }

    @Override
    public CIPluginInfo getPluginInfo() {
        log.debug("get plugin info");
        return DTOFactory.getInstance().newDTO(CIPluginInfo.class).setVersion(pluginVersion);
    }

    @Override
    public CIProxyConfiguration getProxyConfiguration(URL targetUrl) {
        log.debug("get proxy configuration");
        CIProxyConfiguration result = null;

        if (isProxyNeeded(targetUrl)) {
            log.info("proxy is required for host " + targetUrl.getHost());
            String protocol = targetUrl.getProtocol();

            return CONVERTER.getProxyCconfiguration(getProxyProperty(protocol + ".proxyHost", null),
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
    public SnapshotNode getSnapshotByNumber(String pipeline, String snapshot, boolean arg2) {
        // TODO implement get snapshot
        log.info("get snapshot by number " + pipeline.toUpperCase() + " , " + snapshot);
        return null;
    }

    @Override
    public SnapshotNode getSnapshotLatest(String pipeline, boolean arg1) {
        log.info("get latest snapshot  for pipeline " + pipeline);
        pipeline = pipeline.toUpperCase();
        ImmutableTopLevelPlan plan = planMan.getPlanByKey(PlanKeys.getPlanKey(pipeline), ImmutableTopLevelPlan.class);
        return CONVERTER.getSnapshot(plan, plan.getLatestResultsSummary());
    }

    @Override
    public void stopPipelineRun(String pipeline, String paramsJson) {
        log.info("starting pipeline stop");
        Callable<String> impersonated = impService.runAsUser(getRunAsUser(), new Callable<String>() {

            public String call() throws Exception {
                BambooUserManager um = ComponentLocator.getComponent(BambooUserManager.class);
                BambooUser user = um.getBambooUser(getRunAsUser());
                ImmutableChain chain = planMan.getPlanByKey(PlanKeys.getPlanKey(pipeline.toUpperCase()), ImmutableChain.class);

                if (chain != null) {
                    if (!isUserHasPermission(BambooPermission.BUILD, user, chain)) {
                        throw new PermissionException(403);
                    }
                    log.info(String.format("plan key=%s ,build key=%s ,chain key=%s", chain.getPlanKey().getKey(), chain.getBuildKey(), chain.getKey()));
                    try {
                        planExecMan.stopPlan(chain.getPlanKey(), true, user.getName());
                    } catch (InterruptedException e) {
                        log.error("Failed to stop:" + e.getMessage(), e);
                    }
                } else {
                    throw new ConfigurationException(404);
                }
                return null;
            }
        });
        execute(impersonated, "Stop Pipeline");
    }


    @Override
    public void runPipeline(final String pipeline, final String parametersJson) {
        // TODO implement parameters conversion
        // only execute runnable plans
        log.info("starting pipeline run");

        Callable<String> impersonated = impService.runAsUser(getRunAsUser(), new Callable<String>() {

            public String call() throws Exception {
                BambooUserManager um = ComponentLocator.getComponent(BambooUserManager.class);
                BambooUser user = um.getBambooUser(getRunAsUser());
                ImmutableChain chain = planMan.getPlanByKey(PlanKeys.getPlanKey(pipeline.toUpperCase()), ImmutableChain.class);
                if (chain == null || chain.isSuspendedFromBuilding()) {
                    throw new ConfigurationException(404);
                }
                log.info(String.format("plan key=%s ,build key=%s ,chain key=%s", chain.getPlanKey().getKey(), chain.getBuildKey(), chain.getKey()));

                if (!isUserHasPermission(BambooPermission.BUILD, user, chain)) {
                    throw new PermissionException(403);
                }

                HashMap<String, String> variables = new HashMap<>();
                HashMap<String, String> params = new HashMap<>();
                if (SdkStringUtils.isNotEmpty(parametersJson)) {

                    CIParameters parameters = DTOFactory.getInstance().dtoFromJson(parametersJson, CIParameters.class);
                    for (CIParameter param : parameters.getParameters()) {
                        variables.put(param.getName(), param.getValue().toString());
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
            }
        });

        execute(impersonated, "runPipeline");
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

    private PluginSettingsFactory getPluginSettingsFactory() {
        if (settingsFactory == null) {
            settingsFactory = ComponentLocator.getComponent(PluginSettingsFactory.class);
        }
        return settingsFactory;
    }

    @Override
    public InputStream getTestsResult(String jobId, String buildId) {
        //  retrieve test results by build IDs
        InputStream output = null;
        List<TestRun> testRuns = new ArrayList<>();
        PlanResultKey planResultKey = PlanKeys.getPlanResultKey(buildId);
        com.atlassian.bamboo.v2.build.BuildContext buildContext = BuildContextCache.extract(buildId);
        if (buildContext == null) {
            BuildExecution buildExecution = planExecMan.getJobExecution(planResultKey);
            if (buildExecution == null) {
                log.info("failed to find build execution for " + jobId + " #" + buildId);
                return null;
                //throw new IllegalStateException("failed to find build execution for " + jobId + " #" + buildId);
            }
            buildContext = buildExecution.getBuildContext();
        }


        String workingDirectory = buildContext.getBuildResult().getCustomBuildData().get("working.directory");
        String mqmResultFilePath = workingDirectory + File.separator + ALMOctaneCucumberTestReporterConfigurator.MQM_RESULT_FOLDER_PREFIX + File.separator + "Build_" + buildContext.getBuildNumber() + File.separator + "mqmTests.xml";
        File mqmResultFile = new File(mqmResultFilePath);
        if (mqmResultFile.exists()) {
            try {
                output = mqmResultFile.length() > 0 ? new FileInputStream(mqmResultFile.getAbsolutePath()) : null;
            } catch (IOException e) {
                log.error("failed to get test results for  " + jobId + " #" + buildId + " from " + mqmResultFilePath);
            }
        } else {
            HPRunnerType runnerType = HPRunnerTypeUtils.getHPRunnerType(buildContext.getRuntimeTaskDefinitions());
            CurrentBuildResult results = buildContext.getBuildResult();

            if (results.getFailedTestResults() != null) {
                for (TestResults currentTestResult : results.getFailedTestResults()) {
                    testRuns.add(CONVERTER.getTestRunFromTestResult(buildContext, runnerType, currentTestResult, TestRunResult.FAILED,
                            results.getTasksStartDate().getTime()));
                }
            }
            if (results.getSkippedTestResults() != null) {
                for (TestResults currentTestResult : results.getSkippedTestResults()) {
                    testRuns.add(CONVERTER.getTestRunFromTestResult(buildContext, runnerType, currentTestResult, TestRunResult.SKIPPED,
                            results.getTasksStartDate().getTime()));
                }
            }
            if (results.getSuccessfulTestResults() != null) {
                for (TestResults currentTestResult : results.getSuccessfulTestResults()) {
                    testRuns.add(CONVERTER.getTestRunFromTestResult(buildContext, runnerType, currentTestResult, TestRunResult.PASSED,
                            results.getTasksStartDate().getTime()));
                }
            }


            if (!testRuns.isEmpty()) {
                List<TestField> testFields = runnerType.getTestFields();
                BuildContext context = CONVERTER.getBuildContext(
                        String.valueOf(getConnection().getId()),
                        jobId,
                        buildId);
                TestsResult testsResult = DTOFactory.getInstance().newDTO(TestsResult.class).setTestRuns(testRuns)
                        .setBuildContext(context).setTestFields(testFields);

                //  return stream to SDK
                output = dtoFactory.dtoToXmlStream(testsResult);
            }
        }
        return output;
    }

    @Override
    public OctaneResponse checkRepositoryConnectivity(final TestConnectivityInfo testConnectivityInfo) {
        final Callable<OctaneResponse> impersonated = impService.runAsUser(getRunAsUser(), new Callable<OctaneResponse>() {
            public OctaneResponse call() {
                return getUftManager().checkRepositoryConnectivity(testConnectivityInfo);
            }
        });

        return execute(impersonated, "checkRepositoryConnectivity");
    }

    @Override
    public OctaneResponse upsertCredentials(final CredentialsInfo credentialsInfo) {
        final Callable<OctaneResponse> impersonated = impService.runAsUser(getRunAsUser(), new Callable<OctaneResponse>() {
            public OctaneResponse call() {
                return getUftManager().upsertCredentials(credentialsInfo);
            }
        });

        return execute(impersonated, "upsertCredentials");
    }

    @Override
    public void runTestDiscovery(final DiscoveryInfo discoveryInfo) {
        final Callable<Void> impersonated = impService.runAsUser(getRunAsUser(), new Callable<Void>() {
            public Void call() {
                getUftManager().runTestDiscovery(discoveryInfo, getRunAsUser());
                return null;
            }
        });

        execute(impersonated, "runTestDiscovery");
    }

    @Override
    public PipelineNode createExecutor(DiscoveryInfo discoveryInfo) {
        final Callable<PipelineNode> impersonated = impService.runAsUser(getRunAsUser(), new Callable<PipelineNode>() {
            public PipelineNode call() {
                PipelineNode node = getUftManager().createExecutor(discoveryInfo, getRunAsUser());
                return node;
            }
        });
        return execute(impersonated, "createExecutor");
    }

    @Override
    public void deleteExecutor(final String id) {
        final Callable<Void> impersonated = impService.runAsUser(getRunAsUser(), new Callable<Void>() {
            public Void call() {
                getUftManager().deleteExecutor(id);
                return null;
            }
        });

        execute(impersonated, "deleteExecutor");
    }


    private UftManager getUftManager() {
        return UftManager.getInstance();
    }

    private <V> V execute(Callable<V> callable, String actionName) {
        log.info("Impersonated call : " + actionName);

        Callable<V> impersonated = impService.runAsUser(getRunAsUser(), callable);
        try {
            return impersonated.call();
        } catch (PermissionException e) {
            log.warn("PermissionException : " + e.getMessage());
            throw e;
        } catch (Throwable e) {
            log.warn("Failed to execute " + actionName + " : " + e.getMessage(), e);
            RuntimeException runtimeException = null;
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
}
