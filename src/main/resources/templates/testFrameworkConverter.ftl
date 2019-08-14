[#--
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
--]
<div class="helpIcon" onclick="javascript: toggle_visibility('helpConverter');">?</div>
<div id="helpConverter" class="toolTip">
    This build step is intended to support execution of automated tests from Micro Focus ALM Octane.</br>
    The builder searches for the "testsToRun" parameter which is sent from ALM Octane as part of the execution
    framework.</br>
    Once it is found, its value is converted to the format of the selected testing framework, and injected to the
    "bamboo_testsToRunConverted" environment parameter.</br>
    Later, the new parameter can be used in the appropriate execution builder.
    <ul>
        <li>For task configuration fields, use the syntax <i><b>$</b<b>{bamboo.testsToRunConverted}</b></i></li>
        <li>For inline scripts, use the syntax <i><b>$bamboo_testsToRunConverted</b></i> (Linux/Mac OS X) or <i><b>%bamboo_testsToRunConverted%</b></i>
            (Windows)
            For example:
            <ul>
                <li>(Gradle, Windows) <i>gradle test %bamboo_testsToRunConverted%</i></li>
                <li>(Protractor, Windows) <i>protractor conf.js --grep="%bamboo_testsToRunConverted%"</i></li>
                <li>(Surefire, Windows) <i>mvn clean -Dtest=%bamboo_testsToRunConverted% test </i></li>
                <li>(Failsafe, Linux) <i>mvn clean -Dit.test=bamboo_testsToRunConverted verify</i></li>
            </ul>
        </li>

            <br/>
            See examples in the tables below:
            <table border="1" width="100%" style="border-collapse: collapse;border: 1px solid black;">
                <thead style="background-color: lightgray; font-weight: bold">
                <tr>
                    <td width="20%">Framework</td>
                    <td>Linux shell</td>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td>Maven Surefire</td>
                    <td>mvn clean -Dtest=$bamboo_testsToRunConverted test</td>
                </tr>
                <tr>
                    <td>Maven Failsafe</td>
                    <td>mvn clean -Dit.test=$bamboo_testsToRunConverted verify</td>
                </tr>
                <tr>
                    <td>Gradle</td>
                    <td>gradle test $bamboo_testsToRunConverted</td>
                </tr>
                <tr>
                    <td>Protractor</td>
                    <td>protractor conf.js --grep="$bamboo_testsToRunConverted"</td>
                </tr>
                </tbody>
            </table>
            </br>
            <table border="1" width="100%" style="border-collapse: collapse;border: 1px solid black;">
                <thead style="background-color: lightgray; font-weight: bold">
                <tr>
                    <td width="20%">Framework</td>
                    <td>Windows batch command</td>
                </tr>
                </thead>
                <tbody>
                <tr>
                    <td>Maven Surefire</td>
                    <td>mvn clean -Dtest=%bamboo_testsToRunConverted% test</td>
                </tr>
                <tr>
                    <td>Maven Failsafe</td>
                    <td>mvn clean -Dit.test=%bamboo_testsToRunConverted% verify</td>
                </tr>
                <tr>
                    <td>Gradle</td>
                    <td>gradle test %bamboo_testsToRunConverted%</td>
                </tr>
                <tr>
                    <td>Protractor</td>
                    <td>protractor conf.js --grep="%bamboo_testsToRunConverted%"</td>
                </tr>
                </tbody>
            </table>
            </br>
        </li>
    </ul>


    </br>
    Notes/Limitations :
    </br>
    <ul>
        <li><b>UFT</b>only: In order to build a UFT MTBX file, this task needs to know the test check-out directory. By
            default this is the build working directory.
            If tests are checked out in another directory, define in the plan a variable
            <i><b>testsToRunCheckoutDirectory</b></i> with the correct directory.
        </li>
        <li><b>JUnit/TestNG</b>: Supported for JUnit 4.7+, Surefire Plugin 2.19+, Failsafe Plugin 2.12.1+.</li>
    </ul>
</div>
<hr>

<div class="control">
    [@ww.select labelKey="Testing framework" id="SelectFramework" onchange="enableCustomFields()" name="framework" list="supportedFrameworks" emptyOption="true" description="Select the testing framework whose format you want to convert to."/]
</div>
<hr>


<div class="control">
    [@ww.textarea name='customConverterFormat' id='customConverterFormat' class="custom-configuration-text" label='Custom Converter Format' /]

    <select class="select" id="fillSampleConfiguration" name="fillSampleConfiguration" onchange="insertFormat(this)" style="float: right;height: 40px;"
            description="fill sample configuration">
        <option value="title">Fill sample configuration ...</option>
        <option value="basic">Minimal configuration</option>
        <option value="extended">Extended configuration</option>
    </select>
</div>
<div class="helpIcon" onclick="javascript: toggle_visibility('helpCustomFormat');">?</div>
<div id="helpCustomFormat" class="toolTip">
    In the 'Custom conversion format' field, enter json that describes how to convert tests from raw format to the
    format of your testing framework.
    After conversion, the result is injected to the "bamboo_testsToRunConverted" parameter.

    <br/><br/>
    <i><b>Note</b>: Click "Validate" to check the correctness of the inserted configuration.</i>
    <br/><br/>

    The following are the components that you can use in the "Custom conversion format" :
    <ul style="padding-left: 15px;">
        <li><b>testPattern</b> - describes the pattern for converting single test. It uses reserved words
            (<b>$package</b>,<b>$class</b>,<b>$testName</b>)
            that will be
            replaced by real test data. All other characters in the pattern will appear in the final result as is.
        </li>
        <li><b>testDelimiter</b> - the delimiter used to separate different tests.</li>
        <li><b>prefix</b> - a prefix for the whole conversion result.</li>
        <li><b>suffix</b> - a suffix for the whole conversion result.</li>
        <li><b>testsToRunConvertedParameter</b> - the parameter name that will contain the conversion result.
            Default value is "testsToRunConverted".
        </li>
        <li><b>replacements</b> - the array of replace methods.</li>

    </ul>

    The minimal configuration is:
    <br/><br/>

    <pre>
{
    "testPattern": "$package.$class#$testName",
    "testDelimiter": ","
}
    </pre>

    <br/>For example:<br/><br/>
    The <i>testsToRun</i> parameter received 2 tests separated by a semicolon: <b>v1:myPackage1|myClass1|myTest1<i>;</i>myPackage2|myClass2|myTest2</b><br/>
    The defined <i>testPattern</i> is: <b>$package.$class#$testName</b><br/>
    The defined <i>testDelimiter</i> is: <b> , </b>
    <ul>
        <li><b>$package</b> variable will get a value of <i>myPackage1</i> for the first test and <i>myPackage2</i> for
            the second test.
        </li>
        <li><b>$class</b> variable will get a value of <i>myClass1</i> for the first test and <i>myClass2</i> for the
            second test.
        </li>
        <li><b>$testName</b> variable will get a value of <i>myTest1</i> for the first test and <i>myTest2</i> for the
            second test.
        </li>
    </ul>
    <br/>The <i>testsToRunConverted</i> parameter will be equal: <b>myPackage1.myClass1#myTest1,myPackage2.myClass2#myTest2</b>

    <br/>
    <br/>
    Optional:
    <br/>
    There is a possibility to alter values received from ALM Octane, for example to set lowercase to the testName,
    replace spaces by '_', and so on.
    <br>
    Here are examples of available replace methods. Each replace method contains "target" property that define what
    parts of the test pattern are affected by replace method,
    available values are $package,$class,$testName. Its possible to put several values separated by '|'. The
    replacements are executed in the order they appear in the 'Custom conversion format' json.
    <pre>
"replacements": [
{
    "type": "<b>replaceRegex</b>",
    "target": "$package|$class|$testName",
    "regex": "aaa",
    "replacement": "bbb",
    "description": "Replaces all the sequence of characters matching the regex with a replacement string."
},{
    "type": "<b>replaceRegexFirst</b>",
    "target": "$package|$class|$testName",
    "regex": "aaa",
    "replacement": "bbb",
    "description": "Replaces the first substring that matches the given regex with the given replacement."
},{
    "type": "<b>replaceString</b>",
    "target": "$package|$class|$testName",
    "string": "xxx",
    "replacement": "yyy",
    "description": "Replaces all occurrences of ‘string’ with ‘replacement’."
},{
    "type": "<b>joinString</b>",
    "target": "$package|$class|$testName",
    "prefix": "xxx",
    "suffix": "yyy",
    "description": "Add prefix and suffix to the test template."
},{
    "type": "<b>toLowerCase</b>",
    "target": "$package|$class|$testName",
    "description": "Convert test template to lower case."
},{
    "type": "<b>toUpperCase</b>",
    "target": "$package|$class|$testName",
    "description": "Convert test template to upper  case."
},{
    "type": "<b>notLatinAndDigitToOctal</b>",
    "target": "$package|$class|$testName",
    "description": "Replaces all non-latin characters and digits ^[a-zA-Z0-9] to their ASCII octal value."
}]
</pre>

</div>
<hr>

</br>
</br>
<input type="button" value="Validate..." class="aui-button" id="showValidate"
       onclick="javascript:showValidateConversion();"/>
</br>
</br>
</br>
<div class="control" id="validateConversion" style="width: max-content" hidden>
    <h1>Validate conversion</h1>
    [@ww.textfield class="text long-field" name='testsToRun' id='testsToRun' label='Tests to run' description="Enter tests to run in raw format, for example : v1:package1|className1|testName1;package2|className2|testName2" /]
    <input type="button" value="Convert" class="aui-button" id="validate" onclick="javascript:testConvert();"/>
    <br/>
    <div id="convertResult"></div>
</div>

<hr>
<script>

    window.onload = enableCustomFields();

    function testConvert() {
        var p = document.getElementById("convertResult");
        p.innerText = "";
        var e = document.getElementById("SelectFramework");
        var strUser = e.options[e.selectedIndex].value;
        var xhttp = new XMLHttpRequest();
        xhttp.onreadystatechange = function () {
            console.log("onreadystatechange");
            //   var t = document.createTextNode(this);
            if (this.readyState == 4 && this.status == 200) {
                p.style.color = "green";
            } else if (this.status == 405) {
                p.style.color = "red";
            }
            p.innerText = this.response.toString();
        };
        xhttp.open("POST", "${req.contextPath}/rest/octane-admin/1.0/converter-task/convert", true);
        xhttp.setRequestHeader('content-type', 'application/json');
        xhttp.send(
            JSON.stringify({
                testsToRun: document.getElementById("testsToRun").value,
                framework: strUser,
                customConverterFormat: document.getElementById("customConverterFormat").value
            })
        );
    }

    function showValidateConversion() {
        var validateConversion = document.getElementById("validateConversion");
        if (validateConversion.hidden == true) {
            validateConversion.hidden = false;
            document.getElementById("testsToRun").value = "";
            document.getElementById("convertResult").innerText = "";
        } else
            validateConversion.hidden = true;
    }

    function enableCustomFields() {
        var customFormat = document.getElementById("customConverterFormat");
        var autoFill = document.getElementById("fillSampleConfiguration");
        if (document.getElementById("SelectFramework").value == "custom") {
            customFormat.disabled = false;
            autoFill.hidden = false;
        } else {
            customFormat.disabled = true;
            customFormat.text = "";
            autoFill.hidden = true;
        }
    }

    function toggle_visibility(id) {
        var e = document.getElementById(id);
        if (e.style.display == 'block')
            e.style.display = 'none';
        else
            e.style.display = 'block';
    }

    function insertFormat(sender) {
        var index = sender.selectedIndex;
        var options = sender.options;

        var txtFormat = document.getElementById("customConverterFormat");

        if (options[index].value === 'basic') {
            txtFormat.value = "{\n\t\"testPattern\": \"$package.$class#$testName\",\n\t\"testDelimiter\": \",\"\n}";
        } else if (options[index].value === 'extended') {

            txtFormat.value = "{" +
                "\n\t\"testPattern\": \"$package.$class#$testName\"," +
                "\n\t\"testDelimiter\": \",\"," +
                "\n\t\"prefix\": \"\"," +
                "\n\t\"suffix\": \"\"," +
                "\n\t\"replacements\": [" +

                "\n\t\{" +
                "\n\t\t\"type\": \"replaceString\"," +
                "\n\t\t\"target\": \"$package|$class|$testName\"," +
                "\n\t\t\"string\": \"aaa\"," +
                "\n\t\t\"replacement\": \"bbb\"" +
                "\n\t\}," +

                "\n\t\{" +
                "\n\t\t\"type\": \"replaceRegexFirst\"," +
                "\n\t\t\"target\": \"$package|$class|$testName\"," +
                "\n\t\t\"regex\": \"aaa\"," +
                "\n\t\t\"replacement\": \"bbb\"" +
                "\n\t\}," +

                "\n\t\{" +
                "\n\t\t\"type\": \"joinString\"," +
                "\n\t\t\"target\": \"$package|$class|$testName\"," +
                "\n\t\t\"prefix\": \"\"," +
                "\n\t\t\"suffix\": \"\"" +
                "\n\t\}" +

                "\n\t]" +
                "\n}";
        }
        sender.selectedIndex = 0;
    }
</script>
<script type="text/javascript">
    var customWidth = "500px";
    document.getElementById('SelectFramework').style.maxWidth = customWidth;
    document.getElementById('customConverterFormat').style.maxWidth = customWidth;
</script>

<style type="text/css">
    .toolTip {
        border: solid #bbb 1px;
        background-color: #f0f0f0;
        padding: 1em;
        margin-bottom: 1em;
        width: 94%;
        float: left;
        display: none;
    }

    hr {
        clear: both;
        border: none;
    }

    .helpIcon {
        background-color: rgba(59, 115, 175, 1);
        color: white;
        width: 15px;
        border-radius: 15px;
        font-weight: bold;
        padding-left: 6px;
        cursor: pointer;
        margin: 5px;
        float: left;
    }

    .control {
        width: 500px;
        float: left;
    }

    .custom-configuration-text {
        resize: vertical;
        min-height: 90px;
        height: 120px;
    }
</style>