package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.task.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ALMOctaneCucumberTestReporter implements TaskType {

    @NotNull
    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {
        final BuildLogger buildLogger = taskContext.getBuildLogger();


        if(!taskContext.isFinalising()){
            buildLogger.addBuildLogEntry("***************ALM octane cucumber test reporter task must be final*************");
        }

        File targetDirectory = new File(taskContext.getWorkingDirectory(), "Gherkin_Build_" + taskContext.getBuildContext().getBuildNumber());
        targetDirectory.mkdir();

        try {
            ALMOctaneCucumberTestReporterUtils.copyTestResults(targetDirectory.getAbsolutePath(), taskContext.getWorkingDirectory().getAbsolutePath(), taskContext.getConfigurationMap().get(ALMOctaneCucumberTestReporterUtils.OCTANE_REPORT_XML), buildLogger);
            ALMOctaneCucumberTestReporterUtils.createGherkinFiles(targetDirectory.getAbsolutePath(), taskContext.getBuildContext().getShortName(), taskContext.getBuildContext().getBuildNumber(), buildLogger);
        } catch (Exception e) {
            buildLogger.addBuildLogEntry("Exception running cucumber task : " + e.getMessage());

        }
        return TaskResultBuilder.newBuilder(taskContext).success().build();
    }


}