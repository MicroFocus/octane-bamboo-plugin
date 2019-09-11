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
import com.atlassian.bamboo.configuration.AdministrationConfigurationAccessor;
import com.atlassian.bamboo.configuration.ConcurrentBuildConfig;
import com.atlassian.bamboo.plan.ExecutionRequestResult;
import com.atlassian.bamboo.plan.PlanExecutionManager;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.PlanResultKey;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.bamboo.plugin.BambooApplication;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.bamboo.user.BambooUser;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.bamboo.v2.build.queue.BuildQueueManager;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.sal.api.component.ComponentLocator;
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
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.parameters.CIParameters;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.snapshots.SnapshotNode;
import com.hp.octane.integrations.exceptions.ConfigurationException;
import com.hp.octane.integrations.exceptions.PermissionException;
import com.hp.octane.integrations.utils.CIPluginSDKUtils;
import com.hp.octane.integrations.utils.SdkStringUtils;
import com.hp.octane.plugins.bamboo.listener.MultibranchHelper;
import com.hp.octane.plugins.bamboo.octane.uft.UftManager;
import com.hp.octane.plugins.bamboo.rest.OctaneConnection;
import com.hp.octane.plugins.bamboo.rest.OctaneConnectionManager;
import org.acegisecurity.acls.Permission;
import org.acegisecurity.userdetails.UserDetails;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Callable;
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

    private static DTOConverter CONVERTER = DefaultOctaneConverter.getInstance();

    public BambooPluginServices() {
        this.planExecMan = ComponentLocator.getComponent(PlanExecutionManager.class);
        this.planMan = ComponentLocator.getComponent(CachedPlanManager.class);
        this.impService = ComponentLocator.getComponent(ImpersonationService.class);
        this.buildQueueManager = ComponentLocator.getComponent(BuildQueueManager.class);
        this.bambooUserManager = ComponentLocator.getComponent(BambooUserManager.class);
        pluginVersion = ComponentLocator.getComponent(PluginAccessor.class).getPlugin(PLUGIN_KEY).getPluginInformation().getVersion();
        bambooVersion = ComponentLocator.getComponent(BambooApplication.class).getVersion();

    }

    @Override
    public File getAllowedOctaneStorage() {
        return SDKBasedLoggerProvider.getAllowedStorageFile();
    }

    @Override
    public CIJobsList getJobsList(boolean arg0) {
        log.info("Get jobs list");

        Callable<List<ImmutableTopLevelPlan>> plansGetter = () -> planMan.getPlans();
        List<ImmutableTopLevelPlan> plans = executeImpersonatedCall(plansGetter, "getJobsList");
        return CONVERTER.getRootJobsList(plans);
    }

    @Override
    public PipelineNode getPipeline(String pipelineId) {
        //workaround for bamboo
        final String pipelineIdUpper = pipelineId.toUpperCase();
        log.info("get pipeline " + pipelineIdUpper);
        Callable<ImmutableTopLevelPlan> planGetter = () -> planMan.getPlanByKey(PlanKeys.getPlanKey(pipelineIdUpper), ImmutableTopLevelPlan.class);
        ImmutableTopLevelPlan plan = executeImpersonatedCall(planGetter, "getPipeline");
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
            log.debug("proxy is required for host " + targetUrl.getHost());
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
        Callable<String> action = () -> {
            BambooUserManager um = ComponentLocator.getComponent(BambooUserManager.class);
            BambooUser user = um.getBambooUser(getRunAsUser());
            ImmutableChain chain = planMan.getPlanByKey(PlanKeys.getPlanKey(pipeline.toUpperCase()), ImmutableChain.class);

            if (chain != null) {
                if (!isUserHasPermission(BambooPermission.BUILD, user, chain)) {
                    throw new PermissionException(HttpStatus.SC_FORBIDDEN);
                }

                log.info(String.format("plan key=%s ,build key=%s ,chain key=%s", chain.getPlanKey().getKey(), chain.getBuildKey(), chain.getKey()));
                try {
                    planExecMan.stopPlan(chain.getPlanKey(), true, user.getName());
                } catch (InterruptedException e) {
                    log.error("Failed to stop:" + e.getMessage(), e);
                }
            } else {
                throw new ConfigurationException(HttpStatus.SC_NOT_FOUND);
            }
            return null;
        };
        executeImpersonatedCall(action, "Stop Pipeline");
    }


    @Override
    public void runPipeline(final String pipeline, final String parametersJson) {
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
}
