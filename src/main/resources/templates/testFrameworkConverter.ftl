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

<div id ="tooltip" class="toolTip">
    This task is intended to support execution of automated tests from ALM Octane.</br>
    The task searches for the <i><b>testsToRun</b></i> variable which is sent from ALM Octane as part of the execution framework.</br>
    Once it is found, its value is converted to the format of the selected testing framework, and injected to the <i><b>testsToRunConverted</b></i> variable.</br>
    Later, the new variable can be used in the appropriate task.
    <ul>
        <li>For task configuration fields, use the syntax <i><b>$</b<b>{bamboo.testsToRunConverted}</b></i></li>
        <li>For inline scripts, use the syntax <i><b>$bamboo_testsToRunConverted</b></i> (Linux/Mac OS X) or <i><b>%bamboo_testsToRunConverted%</b></i> (Windows)</li>
    </ul>
    </br>
    </br>
    For <b>UFT</b> only: In order to build a UFT MTBX file, this task needs to know the test check-out directory. By default this is the build working directory.
    If tests are checked out in another directory, define in the plan a variable  <i><b>testsToRunCheckoutDirectory</b></i> with the correct directory.
</div>

<div class="control">
    [@ww.select labelKey="Testing framework" name="framework" list="supportedFrameworks" emptyOption="true" description="Select the testing framework whose format you want to convert to."/]
</div>


<script  type="text/javascript">

    var customWidth = "500px";
    document.getElementById('framework').style.maxWidth=customWidth;



</script>

<style type="text/css">
    .toolTip{

        border: solid #bbb 1px;
        background-color: #f0f0f0;
        padding: 1em;
        margin: 15px 0;
        width: 97%;
    }

</style>