<div id="help" class="toolTip">
    This action enables Bamboo to send to ALM Octane test results in
    <a href="https://admhelp.microfocus.com/octane/en/latest/Online/Content/API/test-results.htm" target="_blank"> internal ALM Octane format.</a>. You need to generate file with results during plan execution and define 'Test result file pattern' pointing to generated file.
    <br/>
    ALM Octane plugin knows to send test results that were generated in JUnit format without any additional configuration.
    Use this step only if you need to pass to ALM Octane details that cannot be passed in regular Junit format.
    This step supported by ALM Octane 15.1.90 and higher.
    <br/>
    <br/>
    <b>This task must be a final task.</b>
</div>

[@ww.textfield class="text long-field" name='testResultFilePattern' label='Test result file pattern' description='Pattern for searching test result files. Default is **/*octaneResults*.xml' /]
[@ww.checkbox label='Publish to Bamboo' name='publishToBamboo' description='Select this if no task doesnot publish test results in JUnit format during plan execution. If selected, supplied test result file will be converted to JUnit format and published to Bamboo.' /]

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
