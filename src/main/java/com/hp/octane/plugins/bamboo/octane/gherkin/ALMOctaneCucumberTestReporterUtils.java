package com.hp.octane.plugins.bamboo.octane.gherkin;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.hp.octane.integrations.dto.tests.TestRunResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ALMOctaneCucumberTestReporterUtils {
    public static String OCTANE_REPORT_XML = "cucumberReportXML";
    public static String GHERKIN_NGA_RESULTS = "OctaneGherkinResults";
    public static String CUCUMBER_XML_FILE_NAME = "mqmTests";
    public static final String DEFAULT_GLOB = "**/*" + GHERKIN_NGA_RESULTS + ".xml";


    private static String getGlobPaths(String s) {
        return s.contains(",") ? "{" + s + "}" : s;
    }

    public static void createGherkinFiles(String targetDirectoryPath, String planName, int buildNumber, BuildLogger buildLogger) throws Exception {
        List<GherkinTestResult> result = new ArrayList<>();
        int i = 0;

        //Retrieve the cucumber results xml

        File newTestResultsFile = new File(generateGherkinResultFileName(i, targetDirectoryPath));
        while (newTestResultsFile.exists()) {

            //parse the xml
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = null;

            dBuilder = dbFactory.newDocumentBuilder();
            Document doc = null;
            doc = dBuilder.parse(newTestResultsFile);
            doc.getDocumentElement().normalize();

            validateXMLVersion(doc);

            //Go over the features
            NodeList featureNodes = doc.getElementsByTagName("feature");
            for (int f = 0; f < featureNodes.getLength(); f++) {
                Element featureElement = (Element) featureNodes.item(f);
                FeatureInfo featureInfo = new FeatureInfo(featureElement);
                result.add(new GherkinTestResult(featureInfo.getName(), featureElement, featureInfo.getDuration(), featureInfo.getStatus()));
            }
            i++;
            newTestResultsFile = new File(generateGherkinResultFileName(i, targetDirectoryPath));
        } //end while

        //write new xml file
        writeXmlFile(targetDirectoryPath, planName, buildNumber, result);
    }

    private static String generateGherkinResultFileName(int index, String targetDirectoryPath) {
        return targetDirectoryPath + File.separator + GHERKIN_NGA_RESULTS + index + ".xml";
    }

    private static void addLogEntry(BuildLogger buildLogger, String message) {
        buildLogger.addBuildLogEntry("Micro Focus ALM Octane Cucumber test reporter: " + message);
    }

    public static void copyTestResults(String targetDirectoryPath, String workingDirectoryPath, String userPattern, BuildLogger buildLogger) throws IOException {
        //collect test result from working directory and move it to build directory
        addLogEntry(buildLogger,"Collecting Cucumber results");
        Path startDir = Paths.get(workingDirectoryPath);
        FileSystem fs = FileSystems.getDefault();

        if (StringUtils.isEmpty(userPattern)) {
            userPattern = DEFAULT_GLOB;
            addLogEntry(buildLogger, "Cucumber report XMLs configuration is empty. Using default user pattern : " + DEFAULT_GLOB);
        }

        userPattern = getGlobPaths(userPattern);
        final PathMatcher matcher = fs.getPathMatcher("glob:" + userPattern);

        addLogEntry(buildLogger,"Got result file content that match the pattern '" + userPattern);
        List<Path> finalCollection = new ArrayList<>();
        FileVisitor<Path> matcherVisitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attribs) {
                if (matcher.matches(file.toAbsolutePath())) {
                    finalCollection.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        };
        Files.walkFileTree(startDir, matcherVisitor);

        int i = 0;
        for (Path file : finalCollection) {
            File newGherkinTestResultsFile = new File(generateGherkinResultFileName(i++, targetDirectoryPath));
            addLogEntry(buildLogger,"Result file copied to" + targetDirectoryPath);
            addLogEntry(buildLogger,"Copying " + file.getFileName() + " to " + targetDirectoryPath + ". New file name on destination will be " + newGherkinTestResultsFile.getName());
            FileUtils.copyFile(file.toFile(), newGherkinTestResultsFile);
        }
    }

    private static void writeXmlFile(String targetDirectoryPath, String planName, int buildNumber, List<GherkinTestResult> gherkinTestResults) throws IOException, XMLStreamException {
        OutputStream outputStream = Files.newOutputStream(Paths.get(targetDirectoryPath + File.separator + CUCUMBER_XML_FILE_NAME + ".xml"));

        XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(outputStream, "UTF-8"/*StandardCharsets.UTF_8.name()*/);
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("test_result");
        writer.writeStartElement("build");
        writer.writeAttribute("server_id", "to-be-filled-in-SDK");
        writer.writeAttribute("job_id", planName);
        writer.writeAttribute("build_id", Integer.toString(buildNumber));
        writer.writeEndElement(); // build
        //  writeFields(resultFields);
        writer.writeStartElement("test_runs");

        for (GherkinTestResult g : gherkinTestResults) {
            g.writeXmlElement(writer);
        }
        writer.writeEndElement(); // test_runs
        writer.writeEndElement(); // test_result
        writer.writeEndDocument();
        writer.close();
        IOUtils.closeQuietly(outputStream);
    }

    private static void validateXMLVersion(Document doc) {
        String XML_VERSION = "1";
        NodeList featuresNodes = doc.getElementsByTagName("features");
        if (featuresNodes.getLength() > 0) {
            String versionAttr = ((Element) featuresNodes.item(0)).getAttribute("version");
            if (versionAttr == null || versionAttr.isEmpty() || versionAttr.compareTo(XML_VERSION) != 0) {
                throw new IllegalArgumentException("\n********************************************************\n" +
                        "Incompatible xml version received from the Octane formatter.\n" +
                        "expected version = " + XML_VERSION + " actual version = " + versionAttr + ".\n" +
                        "You may need to update the octane formatter version to the correct version in order to work with this jenkins plugin\n" +
                        "********************************************************");
            }
        } else {
            throw new IllegalArgumentException("The file does not contain Octane Gherkin results. Configuration error?");
        }
    }

    private static class FeatureInfo {
        private String name;
        private List<String> scenarioNames = new ArrayList<>();
        private TestRunResult status = TestRunResult.PASSED;
        private boolean statusDetermined = false;
        private long duration = 0;

        public FeatureInfo(Element featureElement) {
            name = featureElement.getAttribute("name");
            NodeList backgroundNodes = featureElement.getElementsByTagName("background");
            Element backgroundElement = backgroundNodes.getLength() > 0 ? (Element) backgroundNodes.item(0) : null;
            NodeList backgroundSteps = backgroundElement != null ? backgroundElement.getElementsByTagName("step") : null;

            //Go over the scenarios
            NodeList scenarioNodes = featureElement.getElementsByTagName("scenario");
            for (int s = 0; s < scenarioNodes.getLength(); s++) {
                Element scenarioElement = (Element) scenarioNodes.item(s);
                ScenarioInfo scenarioInfo = new ScenarioInfo(scenarioElement, backgroundSteps);
                String scenarioName = scenarioInfo.getName();
                scenarioNames.add(scenarioName);

                duration += scenarioInfo.getDuration();
                if (!statusDetermined && TestRunResult.SKIPPED.equals(scenarioInfo.getStatus())) {
                    status = TestRunResult.SKIPPED;
                    statusDetermined = true;
                } else if (!statusDetermined && TestRunResult.FAILED.equals(scenarioInfo.getStatus())) {
                    status = TestRunResult.FAILED;
                    statusDetermined = true;
                }
            }
        }

        public String getName() {
            return name;
        }

        public List<String> getScenarioNames() {
            return scenarioNames;
        }

        public TestRunResult getStatus() {
            return status;
        }

        public long getDuration() {
            return duration;
        }

        private class ScenarioInfo {
            private List<String> stepNames = new ArrayList<String>();
            private long duration = 0;
            private TestRunResult status = TestRunResult.PASSED;
            private boolean statusDetermined = false;
            private String name;

            public ScenarioInfo(Element scenarioElement, NodeList backgroundSteps) {
                name = getScenarioName(scenarioElement);

                List<Element> stepElements = getStepElements(backgroundSteps, scenarioElement);
                for (Element stepElement : stepElements) {
                    addStep(stepElement);
                }

                scenarioElement.setAttribute("status", status.name());

                //for surefire report
                stepNames.add(name);
                stepNames.add("Scenario: " + name);
            }

            public List<String> getStepNames() {
                return stepNames;
            }

            public long getDuration() {
                return duration;
            }

            public TestRunResult getStatus() {
                return status;
            }

            public String getName() {
                return name;
            }

            private void addStep(Element stepElement) {
                String stepName = stepElement.getAttribute("name");
                stepNames.add(stepName);

                String durationStr = stepElement.getAttribute("duration");
                long stepDuration = durationStr != "" ? Long.parseLong(durationStr) : 0;
                duration += stepDuration;

                String stepStatus = stepElement.getAttribute("status");
                if (!statusDetermined && ("pending".equals(stepStatus) || "skipped".equals(stepStatus))) {
                    status = TestRunResult.SKIPPED;
                    statusDetermined = true;
                } else if (!statusDetermined && "failed".equals(stepStatus)) {
                    status = TestRunResult.FAILED;
                    statusDetermined = true;
                }
            }

            private List<Element> getStepElements(NodeList backgroundSteps, Element scenarioElement) {
                List<Element> stepElements = new ArrayList<Element>();
                if (backgroundSteps != null) {
                    for (int bs = 0; bs < backgroundSteps.getLength(); bs++) {
                        Element stepElement = (Element) backgroundSteps.item(bs);
                        stepElements.add(stepElement);
                    }
                }
                NodeList stepNodes = scenarioElement.getElementsByTagName("step");
                for (int sn = 0; sn < stepNodes.getLength(); sn++) {
                    Element stepElement = (Element) stepNodes.item(sn);
                    stepElements.add(stepElement);
                }

                return stepElements;
            }

            private String getScenarioName(Element scenarioElement) {
                String scenarioName = scenarioElement.getAttribute("name");
                if (scenarioElement.hasAttribute("outlineIndex")) {
                    String outlineIndexStr = scenarioElement.getAttribute("outlineIndex");
                    if (outlineIndexStr != null && !outlineIndexStr.isEmpty()) {
                        Integer outlineIndex = Integer.valueOf(scenarioElement.getAttribute("outlineIndex"));
                        if (outlineIndex > 1) {
                            //we add the index only from 2 and upwards seeing as that is the naming convention in junit xml.
                            String delimiter = " ";
                            if (!scenarioName.contains(" ")) {
                                //we need to use the same logic as used in the junit report
                                delimiter = "_";
                            }
                            scenarioName = scenarioName + delimiter + scenarioElement.getAttribute("outlineIndex");
                        }
                    }

                }
                return scenarioName;
            }
        }
    }

    private static class GherkinTestResult {
        private Map<String, String> attributes;
        private Element contentElement;

        public GherkinTestResult(String name, Element xmlElement, long duration, TestRunResult status) {
            this.attributes = new HashMap<>();
            this.attributes.put("name", name);
            this.attributes.put("duration", String.valueOf(duration));
            this.attributes.put("status", status.name());
            this.contentElement = xmlElement;
        }

        public Map<String, String> getAttributes() {
            return attributes;
        }

        public Element getXmlElement() {
            return contentElement;
        }


        public void writeXmlElement(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeStartElement("gherkin_test_run");
            if (attributes != null) {
                for (String attrName : attributes.keySet()) {
                    writer.writeAttribute(attrName, attributes.get(attrName));
                }
            }
            writeXmlElement(writer, contentElement);
            writer.writeEndElement();
        }

        private void writeXmlElement(XMLStreamWriter writer, Element rootElement) throws XMLStreamException {
            if (rootElement != null) {
                writer.writeStartElement(rootElement.getTagName());
                for (int a = 0; a < rootElement.getAttributes().getLength(); a++) {
                    String attrName = rootElement.getAttributes().item(a).getNodeName();
                    writer.writeAttribute(attrName, rootElement.getAttribute(attrName));
                }
                NodeList childNodes = rootElement.getChildNodes();
                for (int c = 0; c < childNodes.getLength(); c++) {
                    Node child = childNodes.item(c);
                    if (child instanceof Element) {
                        writeXmlElement(writer, (Element) child);
                    } else if (child.getNodeType() == Node.CDATA_SECTION_NODE) {
                        writer.writeCharacters(child.getNodeValue());
                    }
                }
                writer.writeEndElement();
            }
        }

    }


}
