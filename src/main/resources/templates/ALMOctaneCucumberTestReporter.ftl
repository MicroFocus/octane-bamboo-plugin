<div id="helpCucumber" class="toolTip">
    This action enables Bamboo to read the Cucumber test report XML structure, and then send test results to
    <a href="http://hpe-nga-staging.s3-website-us-west-1.amazonaws.com/en/munich-push-3/Online/Content/UserGuide/how_automate_gherkin.htm" target="_blank"> ALM Octane.</a> as part of the Gherkin test integration.
    <br/>
    Specify the path to the Cucumber report XML files in the Ant glob syntax.
    You can specify multiple patterns by separating them with commas.
    This path should only contain Cucumber report files. Note that no other test types will be reported from this task.
    <br/>
    <br/>
    <b>This task must be a final task.</b>
</div>

[@ww.textfield class="text long-field" name='cucumberReportXML' label='Cucumber report XMLs' required='true'/]

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
