package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.task.*;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Paths;

public class ALMOctaneCucumberTestReporterTask implements TaskType {

    @NotNull
    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {
        final BuildLogger buildLogger = taskContext.getBuildLogger();

        if (!taskContext.isFinalising()) {
            buildLogger.addBuildLogEntry("***************ALM octane cucumber test reporter task must be final*************");
        }
        File targetDirectory = Paths.get(taskContext.getWorkingDirectory().getAbsolutePath(),
                ALMOctaneCucumberTestReporterConfigurator. MQM_RESULT_FOLDER_PREFIX,
                "Build_" + taskContext.getBuildContext().getBuildNumber()).toFile();
        targetDirectory.mkdirs();

        try {
            ALMOctaneCucumberTestReporterUtils.copyTestResults(targetDirectory.getAbsolutePath(), taskContext.getWorkingDirectory().getAbsolutePath(), taskContext.getConfigurationMap().get(ALMOctaneCucumberTestReporterConfigurator.CUCUMBER_REPORT_PATTERN_FIELD), buildLogger);
            ALMOctaneCucumberTestReporterUtils.createGherkinFiles(targetDirectory.getAbsolutePath(), taskContext.getBuildContext().getShortName(), taskContext.getBuildContext().getBuildNumber(), buildLogger);
        } catch (Exception e) {
            buildLogger.addBuildLogEntry("Exception running cucumber task : " + e.getMessage());
        }

        return TaskResultBuilder.newBuilder(taskContext).success().build();
    }

}
