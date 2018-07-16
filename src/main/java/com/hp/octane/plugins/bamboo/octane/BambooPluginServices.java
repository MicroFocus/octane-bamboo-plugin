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
import com.atlassian.bamboo.plan.ExecutionRequestResult;
import com.atlassian.bamboo.plan.PlanExecutionManager;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutableTopLevelPlan;
import com.atlassian.bamboo.security.BambooPermissionManager;
import com.atlassian.bamboo.security.acegi.acls.BambooPermission;
import com.atlassian.bamboo.user.BambooUser;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.sal.api.component.ComponentLocator;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.hp.octane.integrations.OctaneSDK;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.configuration.CIProxyConfiguration;
import com.hp.octane.integrations.dto.configuration.OctaneConfiguration;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import com.hp.octane.integrations.dto.executor.CredentialsInfo;
import com.hp.octane.integrations.dto.executor.DiscoveryInfo;
import com.hp.octane.integrations.dto.executor.TestConnectivityInfo;
import com.hp.octane.integrations.dto.executor.TestSuiteExecutionInfo;
import com.hp.octane.integrations.dto.general.CIJobsList;
import com.hp.octane.integrations.dto.general.CIPluginInfo;
import com.hp.octane.integrations.dto.general.CIServerInfo;
import com.hp.octane.integrations.dto.pipelines.PipelineNode;
import com.hp.octane.integrations.dto.snapshots.SnapshotNode;
import com.hp.octane.integrations.exceptions.ConfigurationException;
import com.hp.octane.integrations.exceptions.PermissionException;
import com.hp.octane.integrations.spi.CIPluginServicesBase;
import com.hp.octane.integrations.util.CIPluginSDKUtils;
import com.hp.octane.integrations.util.SdkStringUtils;
import com.hp.octane.plugins.bamboo.api.OctaneConfigurationKeys;
import com.hp.octane.plugins.bamboo.octane.uft.UftManager;
import org.acegisecurity.acls.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.*;
import java.util.*;
import java.util.concurrent.Callable;

public class BambooPluginServices extends CIPluginServicesBase {
    private static final Logger log = LoggerFactory.getLogger(BambooPluginServices.class);
    private static final String PLUGIN_VERSION = "1.0.0-SNAPSHOT";

    private CachedPlanManager planMan;

    private ImpersonationService impService;
    private PlanExecutionManager planExecMan;

    private static DTOConverter CONVERTER = DefaultOctaneConverter.getInstance();
    private PluginSettingsFactory settingsFactory;

    public BambooPluginServices(PluginSettingsFactory settingsFactory) {
        super();
        this.settingsFactory = settingsFactory;
        this.planExecMan = ComponentLocator.getComponent(PlanExecutionManager.class);
        this.planMan = ComponentLocator.getComponent(CachedPlanManager.class);
        this.impService = ComponentLocator.getComponent(ImpersonationService.class);
    }

    // return null as we don't have file storage available
    public File getAllowedOctaneStorage() {
        return null;
    }

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

    public OctaneConfiguration getOctaneConfiguration() {
        log.info("getOctaneConfiguration");
        OctaneConfiguration result = null;
        PluginSettings settings = settingsFactory.createGlobalSettings();
        if (settings.get(OctaneConfigurationKeys.OCTANE_URL) != null && settings.get(OctaneConfigurationKeys.ACCESS_KEY) != null) {
            String url = String.valueOf(settings.get(OctaneConfigurationKeys.OCTANE_URL));
            String accessKey = String.valueOf(settings.get(OctaneConfigurationKeys.ACCESS_KEY));
            String secret = String.valueOf(settings.get(OctaneConfigurationKeys.API_SECRET));
            result = OctaneSDK.getInstance().getConfigurationService().buildConfiguration(url, accessKey, secret);
        }
        return result;
    }

    public PipelineNode getPipeline(String pipelineId) {
        //workaround for bamboo
        pipelineId = pipelineId.toUpperCase();
        log.info("get pipeline " + pipelineId);
        ImmutableTopLevelPlan plan = planMan.getPlanByKey(PlanKeys.getPlanKey(pipelineId), ImmutableTopLevelPlan.class);
        return CONVERTER.getRootPipelineNodeFromTopLevelPlan(plan);
    }

    public CIPluginInfo getPluginInfo() {
        log.info("get plugin info");
        return DTOFactory.getInstance().newDTO(CIPluginInfo.class).setVersion(PLUGIN_VERSION);
    }

    @Override
    public CIProxyConfiguration getProxyConfiguration(URL targetUrl) {
        log.info("get proxy configuration");
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

    public CIServerInfo getServerInfo() {
        PluginSettings settings = settingsFactory.createGlobalSettings();
        log.info("get ci server info");
        String instanceId = String.valueOf(settings.get(OctaneConfigurationKeys.UUID));

        String baseUrl = ComponentLocator.getComponent(AdministrationConfigurationAccessor.class)
                .getAdministrationConfiguration().getBaseUrl();
        String runAsUser = getRunAsUser();
        return CONVERTER.getServerInfo(baseUrl, instanceId, runAsUser);
    }

    public SnapshotNode getSnapshotByNumber(String pipeline, String snapshot, boolean arg2) {
        // TODO implement get snapshot
        log.info("get snapshot by number " + pipeline.toUpperCase() + " , " + snapshot);
        return null;
    }

    public SnapshotNode getSnapshotLatest(String pipeline, boolean arg1) {
        log.info("get latest snapshot  for pipeline " + pipeline);
        pipeline = pipeline.toUpperCase();
        ImmutableTopLevelPlan plan = planMan.getPlanByKey(PlanKeys.getPlanKey(pipeline), ImmutableTopLevelPlan.class);
        return CONVERTER.getSnapshot(plan, plan.getLatestResultsSummary());
    }


    public void runPipeline(final String pipeline, final String parameters) {
        // TODO implement parameters conversion
        // only execute runnable plans
        log.info("starting pipeline run");

        Callable<String> impersonated = impService.runAsUser(getRunAsUser(), new Callable<String>() {

            public String call() throws Exception {
                BambooUserManager um = ComponentLocator.getComponent(BambooUserManager.class);
                BambooUser user = um.getBambooUser(getRunAsUser());
                ImmutableChain chain = planMan.getPlanByKey(PlanKeys.getPlanKey(pipeline.toUpperCase()), ImmutableChain.class);
                log.info("plan key is " + chain.getPlanKey().getKey());
                log.info("build key is " + chain.getBuildKey());
                log.info("chain key is " + chain.getKey());

                if (!isUserHasPermission(BambooPermission.BUILD, user, chain)) {
                    throw new PermissionException(403);
                }
                ExecutionRequestResult result = planExecMan.startManualExecution(chain, user, new HashMap<String, String>(), new HashMap<String, String>());
                if (result.getErrors().getTotalErrors() > 0) {
                    throw new ConfigurationException(504);
                }

                return null;
            }
        });

        execute(impersonated);
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
        PluginSettings settings = settingsFactory.createGlobalSettings();
        return String.valueOf(settings.get(OctaneConfigurationKeys.IMPERSONATION_USER));
    }

    @Override
    public OctaneResponse checkRepositoryConnectivity(final TestConnectivityInfo testConnectivityInfo) {
        final Callable<OctaneResponse> impersonated = impService.runAsUser(getRunAsUser(), new Callable<OctaneResponse>() {
            public OctaneResponse call() {
                return getUftManager().checkRepositoryConnectivity(testConnectivityInfo);
            }
        });

        return execute(impersonated);
    }

    @Override
    public OctaneResponse upsertCredentials(final CredentialsInfo credentialsInfo) {
        final Callable<OctaneResponse> impersonated = impService.runAsUser(getRunAsUser(), new Callable<OctaneResponse>() {
            public OctaneResponse call() {
                return getUftManager().upsertCredentials(credentialsInfo);
            }
        });

        return execute(impersonated);
    }

    @Override
    public void runTestDiscovery(final DiscoveryInfo discoveryInfo) {
        final Callable<Void> impersonated = impService.runAsUser(getRunAsUser(), new Callable<Void>() {
            public Void call() {
                getUftManager().runTestDiscovery(discoveryInfo, getRunAsUser());
                return null;
            }
        });

        execute(impersonated);
    }

    @Override
    public void deleteExecutor(final String id) {
        final Callable<Void> impersonated = impService.runAsUser(getRunAsUser(), new Callable<Void>() {
            public Void call() {
                getUftManager().deleteExecutor(id);
                return null;
            }
        });

        execute(impersonated);
    }

    @Override
    public void runTestSuiteExecution(final TestSuiteExecutionInfo testSuiteExecutionInfo) {
        final Callable<Void> impersonated = impService.runAsUser(getRunAsUser(), new Callable<Void>() {
            public Void call() {
                getUftManager().runTestSuiteExecution(testSuiteExecutionInfo, getRunAsUser());
                return null;
            }
        });
        execute(impersonated);
    }


    private UftManager getUftManager() {
        return UftManager.getInstance();
    }

    private <V> V execute(Callable<V> callable) {
        log.info("Impersonated call");

        Callable<V> impersonated = impService.runAsUser(getRunAsUser(), callable);
        try {
            return impersonated.call();
        } catch (PermissionException e) {
            throw e;
        } catch (Throwable e) {
            log.info("Error impersonating : " + e.getMessage(), e);
            RuntimeException runtimeException = null;
            if (e instanceof RuntimeException) {
                runtimeException = (RuntimeException) e;
            } else {
                runtimeException = new RuntimeException(e);
            }
            throw runtimeException;
        }
    }
}
