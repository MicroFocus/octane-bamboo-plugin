<!--<img src="${req.contextPath}/download/resources/alm-octane-logo.png" border="0"/>-->
<script>
    function testConfiguration() {
        var p = document.getElementById("resultPNode");
        p.textContent = '';
        var xhttp = new XMLHttpRequest();
        xhttp.onreadystatechange = function () {
            if (this.readyState == 4 && this.status == 200) {
                var p = document.getElementById("resultPNode");
                // clear children
                p.textContent = '';
                var t = document.createTextNode(this.responseText);
                if (this.responseText && this.responseText.toLocaleLowerCase().indexOf("success") >= 0) {
                    p.style.color = "green";
                } else {
                    p.style.color = "red";
                }
                p.appendChild(t);
            }
        };
        xhttp.open("POST", "${req.contextPath}/rest/octane/1.0/testconnection", true);
        xhttp.setRequestHeader('content-type', 'application/json');
        xhttp.send(JSON.stringify({
            octaneUrl: document.getElementById("octaneConfigurationForm_octaneUrl").value,
            accessKey: document.getElementById("octaneConfigurationForm_accessKey").value,
            apiSecret: document.getElementById("octaneConfigurationForm_apiSecret").value,
            userName: document.getElementById("octaneConfigurationForm_userName").value
        }));
    }
</script>
<h1>ALM Octane CI Plugin</h1>
<div class="paddedClearer"></div>
[@ww.form action="/admin/nga/configureOctaneSave.action"
id="octaneConfigurationForm"
submitLabelKey='global.buttons.update' ]
    [@ui.bambooSection title="ALM Octane Server Configuration"]
        [@ww.textfield name='octaneUrl' label='Location' required='true' description='Location of the ALM Octane application' /]
        [@ww.textfield name="accessKey" label='Client ID'  required='true' description='Client ID used for logging into the ALM Octane server' /]
        [#--fake unvisible fields- in order to ignore autocomplete
        https://stackoverflow.com/questions/12374442/chrome-ignores-autocomplete-off
        https://stackoverflow.com/questions/18306052/autocomplete-off-not-working-on-google-chrome-browser
        --]
        <div id="fieldArea_octaneConfigurationForm_fakepasswordremembered" class="field-group"style="display:none">
            <input type="password" name="fakepasswordremembered" id="octaneConfigurationForm_fakepasswordremembered" class="text password" >
        </div>
        <div id="fieldArea_octaneConfigurationForm_fakeuserName" class="field-group"style="display: none">
            <input type="text" name="fakeuserName" value="" id="octaneConfigurationForm_fakeuserName" class="text " >
        </div>
        <div id="fieldArea_octaneConfigurationForm_fakepasswordremembered1" class="field-group"style="display: none">
            <input type="password" name="fakepasswordremembered1" id="octaneConfigurationForm_fakepasswordremembered1" class="text password" >
        </div>
        <div id="fieldArea_octaneConfigurationForm_fakeuserName1" class="field-group"style="display: none">
            <input type="text" name="fakeuserName1" value="" id="octaneConfigurationForm_fakeuserName1" class="text " >
        </div>
        [@ww.password name="apiSecret" label='Client secret' required='true' description='Client secret used for logging into the ALM Octane server'/]
        [@ww.textfield name="userName" label='Bamboo user' required='true' description='The account that will run jobs for ALM Octane (must have build plan permissions).'/]
    [/@ui.bambooSection]
[/@ww.form]
<script>
    // adding the check connection button
    var buttonsContainer = document.querySelector('#octaneConfigurationForm > .buttons-container > .buttons'),
        testButton = document.createElement('input');
    testButton.type = 'button';
    testButton.value = 'Test Connection';
    testButton.className = 'aui-button aui-button-primary';
    testButton.onclick = testConfiguration;
    buttonsContainer.appendChild(testButton);
    // adding the check result text
    var buttonsContainer = document.querySelector('#octaneConfigurationForm > .buttons-container '),
        pAnswer = document.createElement('p');
    pAnswer.id = "resultPNode";
    buttonsContainer.appendChild(pAnswer);
    var t = document.createTextNode("");
    pAnswer.appendChild(t);
</script>
