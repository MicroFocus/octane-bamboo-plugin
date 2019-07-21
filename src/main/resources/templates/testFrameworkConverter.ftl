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
    This task is intended to support execution of automated tests from ALM Octane.</br>
    The task searches for the <i><b>testsToRun</b></i> variable which is sent from ALM Octane as part of the execution
    framework.</br>
    Once it is found, its name is converted to the format of the selected testing framework, and injected to the <i><b>testsToRunConverted</b></i>
    variable.</br>
    Later, the new variable can be used in the appropriate task.
    <ul>
        <li>For task configuration fields, use the syntax <i><b>$</b<b>{bamboo.testsToRunConverted}</b></i></li>
        <li>For inline scripts, use the syntax <i><b>$bamboo_testsToRunConverted</b></i> (Linux/Mac OS X) or <i><b>%bamboo_testsToRunConverted%</b></i>
            (Windows)
            For example:
            <ul>
                <li>(Gradle, Windows) <i>gradle test %bamboo_testsToRunConverted%</i></li>
                <li>(Protractor, Windows) <i>protractor conf.js --grep="%bamboo_testsToRunConverted%"</i></li>
                <li>(Surefire, Windows) <i>clean -Dtest=%bamboo_testsToRunConverted% test </i></li>
                <li>(Failsafe, Linux) <i> clean -Dit.test=bamboo_testsToRunConverted verify</i></li>
            </ul>
        </li>

    </ul>
    </br>
    </br>
    For <b>UFT</b> only: In order to build a UFT MTBX file, this task needs to know the test check-out directory. By
    default this is the build working directory.
    If tests are checked out in another directory, define in the plan a variable
    <i><b>testsToRunCheckoutDirectory</b></i> with the correct directory.
</div>
<hr>

<div class="control">
    [@ww.select labelKey="Testing framework" id="SelectFramework" onchange="enableCustomFields()" name="framework" list="supportedFrameworks" emptyOption="true" description="Select the testing framework whose format you want to convert to."/]
</div>
<hr>

<div style="display: none;">
    <div class="control">
        [@ww.textfield name='customConverterFormat' id='customConverterFormat' label='Format' /]
    </div>
    <div class="helpIcon" onclick="javascript: toggle_visibility('helpCustomFormat');">?</div>
    <div id="helpCustomFormat" class="toolTip">
        The format used to convert single test received in "testsToRun" parameter to the format of the Custom testing
        framework, and inject it to the "testsToRunConverted" parameter<br/>
        The format syntax is:
        <ul>
            <li><b>$package</b> - to use the test package name in format</li>
            <li><b>$class</b> - to use the test class name in format</li>
            <li><b>$testName</b> - to use the test name in format</li>
        </ul>

        <br/>For example:<br/><br/>
        <i>testsToRun</i> parameter received 2 tests semicolon separated: <b>v1:MF.simple.tests|AppTest|testA<i>;</i>MF.simple.tests|App2Test|testSendGet</b><br/>
        The defined <i>format</i> is: <b>$package.$class#$testName</b><br/>
        The defined <i>delimiter</i> is: <b> , </b>
        <ul>
            <li><b>$package</b> variable will get a name: <i>MF.simple.tests</i></li>
            <li><b>$class</b> variable will get a name: <i>AppTest</i></li>
            <li><b>$testName</b> variable will get a name: <i>testAl</i></li>
        </ul>
        <br/>The <i>testsToRunConverted</i> will be equal: <b>MF.simple.tests.AppTest#testA,MF.simple.tests.App2Test#testSendGet</b>

    </div>
    <hr>


    <div class="control">
        [@ww.textfield name='customConverterDelimiter' id='customConverterDelimiter' label='Delimiter' /]
    </div>
    <div class="helpIcon" onclick="javascript: toggle_visibility('helpCustomDelimiter');">?</div>
    <div id="helpCustomDelimiter" class="toolTip">
        The delimiter used to separate different tests during conversion to "testsToRun" parameter to the format of the
        selected testing framework conversion<br/>

        For usage details look at help for format field

    </div>
</div>
<hr>
<script>
    window.onload = enableCustomFields();

    function enableCustomFields() {
        var format = document.getElementById("customConverterFormat");
        var delimiter = document.getElementById("customConverterDelimiter");
        if (document.getElementById("SelectFramework").value == "custom") {
            format.disabled = false;
            delimiter.disabled = false;
        } else {
            format.disabled = true;
            format.value = "";
            delimiter.disabled = true;
            delimiter.value = "";
        }
    }

    function toggle_visibility(id) {
        var e = document.getElementById(id);
        if (e.style.display == 'block')
            e.style.display = 'none';
        else
            e.style.display = 'block';
    }
</script>
<script type="text/javascript">
    var customWidth = "500px";
    document.getElementById('SelectFramework').style.maxWidth = customWidth;
    document.getElementById('customConverterFormat').style.maxWidth = customWidth;
    document.getElementById('customConverterDelimiter').style.maxWidth = customWidth;
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
</style>