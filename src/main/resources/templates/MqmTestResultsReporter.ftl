<div id="help" class="toolTip">
    This action enables Bamboo to send test results to ALM Octane in  <a href="https://admhelp.microfocus.com/octane/en/latest/Online/Content/API/test-results.htm" target="_blank"> internal ALM Octane format</a>.
    To do this, you need to generate a file with results during plan execution, and define the 'Test result file pattern' pointing to the generated file.
    <br/>
    Note that by default, the ALM Octane plugin sends test results that were generated in JUnit format without needing any additional configuration.
    Use this step only if for some reason you need to pass details to ALM Octane that cannot be passed in regular JUnit format.
    This step is supported by ALM Octane 15.1.90 and higher.
    <br/>
    <br/>
    <b>This task must be a final task.</b>
</div>

[@ww.textfield class="text long-field" name='testResultFilePattern' label='Test result file pattern' description='Pattern for searching test result file. Default is **/*octaneResults*.xml' /]
[@ww.checkbox label='Publish to Bamboo' name='publishToBamboo' description='Select this if there is no task that publishes test results in JUnit format during plan execution. If selected, the supplied test result file will be converted to JUnit format and published to Bamboo.' /]

<style type="text/css">
    .toolTip {
        border: solid #bbb 1px;
        background-color: #f0f0f0;
        padding: 1em;
        margin-bottom: 1em;
        width: 94%;
        position: relative;
        z-index: 100;
    }
</style>
