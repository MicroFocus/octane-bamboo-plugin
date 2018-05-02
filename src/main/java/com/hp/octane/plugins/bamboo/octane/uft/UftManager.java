package com.hp.octane.plugins.bamboo.octane.uft;

import com.atlassian.bamboo.build.DefaultJob;
import com.atlassian.bamboo.plan.*;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.project.Project;
import com.atlassian.bamboo.project.ProjectManager;
import com.atlassian.bamboo.user.BambooUserManager;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.connectivity.OctaneResponse;
import com.hp.octane.integrations.dto.events.CIEvent;
import com.hp.octane.integrations.dto.executor.DiscoveryInfo;
import com.hp.octane.integrations.dto.executor.TestConnectivityInfo;

import com.hp.octane.integrations.dto.executor.TestSuiteExecutionInfo;
import com.hp.octane.integrations.dto.parameters.CIParameter;
import com.hp.octane.integrations.dto.parameters.CIParameterType;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UftManager {

    ProjectManager projectManager;
    PlanManager planManager;
    PlanExecutionManager planExecManager;
    BambooUserManager bambooUserManager;
    CachedPlanManager cachedPlanManager;

    public static final String SUITE_ID_PARAMETER = "suiteId";
    public static final String SUITE_RUN_ID_PARAMETER = "suiteRunId";

    public UftManager() {
        projectManager = ComponentLocator.getComponent(ProjectManager.class);
        planManager = ComponentLocator.getComponent(PlanManager.class);
        planExecManager = ComponentLocator.getComponent(PlanExecutionManager.class);
        bambooUserManager = ComponentLocator.getComponent(BambooUserManager.class);
        cachedPlanManager = ComponentLocator.getComponent(CachedPlanManager.class);
    }

    private Project getMainProject() {
        String key = "UOI";
        String name = "UftOctaneIntegration";
        String desc = "This project was created by the Microfocus Octane plugin for managing execution of UFT tests and integration with Octane.";
        Project project = projectManager.getProjectByKey(key);
        if (project == null) {
            project = projectManager.createProject(key, name, desc);
            projectManager.saveProject(project);
        }
        return project;

    }

    private Plan getDiscoveryPlan(Project project, DiscoveryInfo discoveryInfo) {

        String key = "UFTDiscovery-" + discoveryInfo.getExecutorLogicalName();
        String name = String.format("UFT test discovery job - Connection ID %s (%s)", discoveryInfo.getExecutorId(), discoveryInfo.getExecutorLogicalName());
        PlanKey planKey = PlanKeys.getPlanKey(key);
        Plan plan = planManager.getPlanByKey(planKey);
        if (plan == null) {
            plan = new DefaultJob();
            plan.setKey(key);
            plan.setName(name);
            plan.setProject(project);
            planManager.createPlan(plan);
        }

        return plan;
    }

    public OctaneResponse checkRepositoryConnectivity(TestConnectivityInfo testConnectivityInfo) {
        OctaneResponse result = DTOFactory.getInstance().newDTO(OctaneResponse.class);
        result.setStatus(HttpStatus.OK.value());
        return result;
    }

    public void runTestDiscovery(DiscoveryInfo discoveryInfo, String impersonatedUser) {
        /*try {
            Project project = getMainProject();
            Plan plan = getDiscoveryPlan(project, discoveryInfo);

            BambooUser bambooUser = bambooUserManager.getBambooUser(impersonatedUser);
            ImmutableChain chain = cachedPlanManager.getPlanByKey(plan.getPlanKey(), ImmutableChain.class);
            ExecutionRequestResult result = planExecManager.startManualExecution(chain, bambooUser, new HashMap<String, String>(), new HashMap<String, String>());
        } catch (Exception e) {
            //throw e;
        }*/
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

}
