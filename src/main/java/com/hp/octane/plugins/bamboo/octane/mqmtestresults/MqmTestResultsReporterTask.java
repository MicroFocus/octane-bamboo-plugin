package com.hp.octane.plugins.bamboo.octane.mqmtestresults;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.build.test.TestCollationService;
import com.atlassian.bamboo.build.test.TestCollectionResult;
import com.atlassian.bamboo.build.test.TestCollectionResultBuilder;
import com.atlassian.bamboo.build.test.TestReportProvider;
import com.atlassian.bamboo.results.tests.TestResults;
import com.atlassian.bamboo.resultsummary.tests.TestCaseResultError;
import com.atlassian.bamboo.resultsummary.tests.TestCaseResultErrorImpl;
import com.atlassian.bamboo.resultsummary.tests.TestState;
import com.atlassian.bamboo.task.*;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.sal.api.component.ComponentLocator;
import com.hp.octane.integrations.dto.DTOFactory;
import com.hp.octane.integrations.dto.tests.TestRun;
import com.hp.octane.integrations.dto.tests.TestRunResult;
import com.hp.octane.integrations.dto.tests.TestsResult;
import com.hp.octane.plugins.bamboo.octane.OctaneConstants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MqmTestResultsReporterTask implements TaskType {

    public static final String DEFAULT_GLOB = "**/*octaneResults*.xml";
    private TestCollationService testCollationService;

    public MqmTestResultsReporterTask(TestCollationService testCollationService) {
        this.testCollationService = testCollationService;
    }

    @NotNull
    @Override
    public TaskResult execute(@NotNull TaskContext taskContext) throws TaskException {
        final BuildLogger buildLogger = taskContext.getBuildLogger();

        if (!taskContext.isFinalising()) {
            buildLogger.addBuildLogEntry("***************ALM octane test reporter task must be final*************");
        }
        File targetDirectory = Paths.get(taskContext.getWorkingDirectory().getAbsolutePath(),
                OctaneConstants.MQM_RESULT_FOLDER,
                "Build_" + taskContext.getBuildContext().getBuildNumber()).toFile();
        targetDirectory.mkdirs();

        try {
            addLogEntry(buildLogger, "Collecting results");
            String pattern = taskContext.getConfigurationMap().get(MqmTestResultsReporterConfigurator.RESULT_FILE_PATTERN_FIELD);
            Path found = findResultFile(taskContext.getWorkingDirectory().getAbsolutePath(), pattern, buildLogger,
                    taskContext.getBuildContext().getBuildResult().getTasksStartDate());
            if (found == null) {
                addLogEntry(buildLogger, "No appropriate test result file is found");
                return TaskResultBuilder.newBuilder(taskContext).failed().build();
            } else {
                addLogEntry(buildLogger, "Test result file is found : " + found);
                copyTestResults(found, targetDirectory.getAbsolutePath(), buildLogger);

                boolean publishToBamboo = Boolean.parseBoolean(taskContext.getConfigurationMap().get(MqmTestResultsReporterConfigurator.PUBLISH_TO_BAMBOO));
                if (publishToBamboo) {
                    publishToBamboo(taskContext, found.toFile(), buildLogger);
                }
                return TaskResultBuilder.newBuilder(taskContext).success().build();
            }

        } catch (Exception e) {
            StringWriter errors = new StringWriter();
            e.printStackTrace(new PrintWriter(errors));

            buildLogger.addBuildLogEntry("Exception running ALM Octane test reporter task : " + e.getMessage() + ", " + errors.toString());

            e.printStackTrace();
            return TaskResultBuilder.newBuilder(taskContext).failedWithError().build();
        }
    }

    private static void addLogEntry(BuildLogger buildLogger, String message) {
        buildLogger.addBuildLogEntry("Micro Focus ALM Octane test reporter: " + message);
    }

    private static Path findResultFile(String workingDirectoryPath, String userPattern, BuildLogger buildLogger, Date taskStartDate) throws IOException {
        Path startDir = Paths.get(workingDirectoryPath);
        FileSystem fs = FileSystems.getDefault();

        if (StringUtils.isEmpty(userPattern)) {
            userPattern = DEFAULT_GLOB;
        }
        addLogEntry(buildLogger, "Using search pattern : " + userPattern);

        //https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)
        userPattern = "{" + userPattern + "}";
        final PathMatcher matcher = fs.getPathMatcher("glob:" + userPattern);
        final PathMatcher exclude = fs.getPathMatcher("glob:" + "**/" + OctaneConstants.MQM_RESULT_FOLDER + "/**");
        List<Path> finalCollection = new ArrayList<>();

        //see example : TestCollationServiceImpl
        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
                if (matcher.matches(file.toAbsolutePath()) && !exclude.matches(file.toAbsolutePath())) {
                    File tempFile = file.toFile();
                    boolean isFileRecentEnough = this.isFileRecentEnough(tempFile);

                    if (!isFileRecentEnough) {
                        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                        String msg = String.format("File %s was ignored because it was modified (%s) before task started (%s)", file, dateFormat.format(new Date(tempFile.lastModified())), dateFormat.format(taskStartDate));
                        addLogEntry(buildLogger, msg);
                    } else {
                        finalCollection.add(file);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            private boolean isFileRecentEnough(File file) {
                return file.lastModified() >= taskStartDate.getTime() - SystemProperty.FS_TIMESTAMP_RESOLUTION_MS.getTypedValue();
            }
        };
        Files.walkFileTree(startDir, matcherVisitor);
        return finalCollection.isEmpty() ? null : finalCollection.get(0);
    }

    private static void copyTestResults(Path source, String targetDirectoryPath, BuildLogger buildLogger) throws IOException {
        String targetFilePath = targetDirectoryPath + File.separator + "mqmTests.xml";
        File newTestResultsFile = new File(targetFilePath);
        addLogEntry(buildLogger, "Copying " + source.getFileName() + " to " + newTestResultsFile.getAbsolutePath());
        FileUtils.copyFile(source.toFile(), newTestResultsFile);
    }

    private void publishToBamboo(TaskContext taskContext, File file, BuildLogger buildLogger) {
        addLogEntry(buildLogger, "Publish test results to Bamboo ");
        TestsResult result = DTOFactory.getInstance().dtoFromXmlFile(file, TestsResult.class);
        testCollationService.collateTestResults(taskContext, new TestReportProvider() {

            @NotNull
            @Override
            public TestCollectionResult getTestCollectionResult() {
                Map<TestState, List<TestResults>> map = result.getTestRuns().stream().map(t -> convertToBambooResult(t)).collect(Collectors.groupingBy(TestResults::getState));
                TestCollectionResultBuilder builder = new TestCollectionResultBuilder();
                if (map.containsKey(TestState.SUCCESS)) {
                    builder.addSuccessfulTestResults(map.get(TestState.SUCCESS));
                }
                if (map.containsKey(TestState.FAILED)) {
                    builder.addFailedTestResults(map.get(TestState.FAILED));
                }
                if (map.containsKey(TestState.SKIPPED)) {
                    builder.addSkippedTestResults(map.get(TestState.SKIPPED));
                }
                return builder.build();
            }

            private TestResults convertToBambooResult(TestRun run) {
                String className = StringUtils.isNotEmpty(run.getPackageName()) && StringUtils.isNotEmpty(run.getClassName()) ?
                        run.getPackageName() + "." + run.getClassName() : run.getClassName();

                TestResults bambooRun = new TestResults(className, run.getTestName(), run.getDuration());
                if (run.getError() != null) {
                    String errorContent;
                    if (StringUtils.isNotEmpty(run.getError().getStackTrace())) {
                        errorContent = run.getError().getStackTrace();
                    } else {
                        errorContent = StringUtils.isNotEmpty(run.getError().getErrorType())
                                ? run.getError().getErrorType() + ": " + run.getError().getErrorMessage()
                                : run.getError().getErrorMessage();
                    }

                    TestCaseResultError er = new TestCaseResultErrorImpl(errorContent);
                    bambooRun.addError(er);
                }

                TestState state = null;
                if (TestRunResult.PASSED.equals(run.getResult())) {
                    state = TestState.SUCCESS;
                } else if (TestRunResult.FAILED.equals(run.getResult())) {
                    state = TestState.FAILED;
                } else if (TestRunResult.SKIPPED.equals(run.getResult())) {
                    state = TestState.SKIPPED;
                }
                bambooRun.setState(state);
                return bambooRun;
            }
        });
    }
}
