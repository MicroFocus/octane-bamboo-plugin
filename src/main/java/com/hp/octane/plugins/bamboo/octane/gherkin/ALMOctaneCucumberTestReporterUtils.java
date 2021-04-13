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

package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.utils.SystemProperty;
import com.hp.octane.integrations.testresults.GherkinUtils;
import com.hp.octane.integrations.utils.SdkConstants;
import com.hp.octane.plugins.bamboo.octane.OctaneConstants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ALMOctaneCucumberTestReporterUtils {
    public static final String GHERKIN_NGA_RESULTS = "OctaneGherkinResults";
    public static final String DEFAULT_GLOB = "**/*" + GHERKIN_NGA_RESULTS + ".xml";

    public static void aggregateGherkinFilesToMqmResultFile(String targetDirectoryPath, String planName, int buildNumber, BuildLogger buildLogger) throws Exception {
        List<File> gherkinFiles = GherkinUtils.findGherkinFilesByTemplateWithCounter(targetDirectoryPath, GHERKIN_NGA_RESULTS + "%s.xml", 0);

        File mqmFile = new File(targetDirectoryPath , SdkConstants.General.MQM_TESTS_FILE_NAME);
        addLogEntry(buildLogger, "Creating mqm test result file : " + mqmFile.getAbsolutePath());
        GherkinUtils.aggregateGherkinFilesToMqmResultFile(gherkinFiles, mqmFile, planName, Integer.toString(buildNumber));
    }

    private static String generateGherkinResultFileName(int index, String targetDirectoryPath) {
        return targetDirectoryPath + File.separator + GHERKIN_NGA_RESULTS + index + ".xml";
    }

    private static void addLogEntry(BuildLogger buildLogger, String message) {
        buildLogger.addBuildLogEntry("Micro Focus ALM Octane Cucumber test reporter: " + message);
    }

    public static void copyTestResults(String targetDirectoryPath, String workingDirectoryPath, String userPattern, BuildLogger buildLogger, Date taskStartDate) throws IOException {
        //collect test result from working directory and move it to build directory
        addLogEntry(buildLogger, "Collecting results");
        Path startDir = Paths.get(workingDirectoryPath);
        FileSystem fs = FileSystems.getDefault();

        if (StringUtils.isEmpty(userPattern)) {
            userPattern = DEFAULT_GLOB;
            addLogEntry(buildLogger, "Cucumber report XMLs configuration is empty. Using default pattern : " + DEFAULT_GLOB);
        }

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
        addLogEntry(buildLogger, "Search for result files that match the pattern '" + userPattern + "'. Found " + finalCollection.size() + " files.");

        int i = 0;
        addLogEntry(buildLogger, "Copy result files to " + targetDirectoryPath);
        for (Path file : finalCollection) {
            File newGherkinTestResultsFile = new File(generateGherkinResultFileName(i++, targetDirectoryPath));
            addLogEntry(buildLogger, "Copying " + file.getFileName() + " to " + newGherkinTestResultsFile.getName());
            FileUtils.copyFile(file.toFile(), newGherkinTestResultsFile);
        }
    }
}
