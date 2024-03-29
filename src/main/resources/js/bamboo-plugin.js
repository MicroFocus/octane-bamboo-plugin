/**
 *
 * Copyright 2017-2023 Open Text
 *
 * The only warranties for products and services of Open Text and
 * its affiliates and licensors (“Open Text”) are as may be set forth
 * in the express warranty statements accompanying such products and services.
 * Nothing herein should be construed as constituting an additional warranty.
 * Open Text shall not be liable for technical or editorial errors or
 * omissions contained herein. The information contained herein is subject
 * to change without notice.
 *
 * Except as specifically indicated otherwise, this document contains
 * confidential information and a valid license is required for possession,
 * use or copying. If this work is provided to the U.S. Government,
 * consistent with FAR 12.211 and 12.212, Commercial Computer Software,
 * Computer Software Documentation, and Technical Data for Commercial Items are
 * licensed to the U.S. Government under vendor's standard commercial license.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

(function ($) { // this closure helps us keep our variables to ourselves.
    // This pattern is known as an "iife" - immediately invoked function expression
    var octanePluginContext = {};
    octanePluginContext.octaneAdminBaseUrl = AJS.contextPath() + "/rest/octane-admin/1.0/";
    var spaceTable;

    AJS.$(document).ready(function () {
        window.onbeforeunload = null;//Disable “Changes you made may not be saved” pop-up window
        configureSpaceConfigurationTable();
        configureSpaceConfigurationDialog();

    });

    function configureSpaceConfigurationDialog() {
        //show
        AJS.$("#show-add-dialog").on('click', function (e) {
            e.preventDefault();
            octanePluginContext.currentRow = null;
            showSpaceConfigurationDialog(null);
        })

        //cancel
        AJS.$("#dialog-cancel-button").on('click', function (e) {
            e.preventDefault();
            AJS.dialog2("#config-dialog").hide();
        });

        AJS.$("#dialog-testconnection-button").on('click', function (e) {
            e.preventDefault();
            if (!validateRequiredFieldsFilled()) {
                return;
            }
            $("#dialog-message").show();
            var throbber = AJS.$("#dialog-message");
            var id = octanePluginContext.currentRow != null ? octanePluginContext.currentRow.model.attributes.id : "";
            var model = {
                id: id,
                location: AJS.$("#location").val(),
                clientId: AJS.$("#clientId").val(),
                clientSecret: AJS.$("#clientSecret").val(),
                bambooUser: AJS.$("#bambooUser").val()
            };
            testConnection(throbber, model);
        });
        //save
        AJS.$("#dialog-submit-button").on('click', function (e) {
            e.preventDefault();
            if (!validateRequiredFieldsFilled()) {
                return;
            }

            var model = {
                id: "",
                location: $("#location").val(),
                clientId: $("#clientId").val(),
                clientSecret: $("#clientSecret").val(),
                bambooUser: $("#bambooUser").val()
            };
            var url;
            var type;
            if (octanePluginContext.currentRow) {//update
                model.id = octanePluginContext.currentRow.model.attributes.id;
                url = spaceTable.options.resources.all + "/" + model.id;
                type = "PUT";
            } else {//add
                url = spaceTable.options.resources.all;
                type = "POST";
            }
            var myJSON = JSON.stringify(model);
            $.ajax({
                url: url,
                type: type,
                data: myJSON,
                dataType: "json",
                contentType: "application/json"
            }).done(function (result) {
                clearStatusMessage();
                refreshRow(result);
                AJS.dialog2("#config-dialog").hide();
            }).fail(function (request) {
                setStatusMessage("Failed to save : " + request.responseText, "ERROR");
            });
        });

    }

    function clearStatusMessage() {
        setStatusMessage("");
    }

    function setStatusMessage(msg, status) {
        $("#error-message").removeClass("success-msg");
        $("#error-message").removeClass("error-msg");
        $("#error-message").text(msg);

        if(status){
            if(status==="ERROR") {
                $("#error-message").addClass("error-msg");
            }else if (status==="SUCCESS") {
                $("#error-message").addClass("success-msg");
            }
        }
    }

    function configureSpaceConfigurationTable() {
        var MyRow = AJS.RestfulTable.Row.extend({
            renderOperations: function () {
                var rowInstance = this;

                var editButtonEl = $('<button class=\"aui-button aui-button-link\">Edit</button>').click(function (e) {
                    octanePluginContext.currentRow = rowInstance;
                    showSpaceConfigurationDialog(rowInstance);
                });

                var testConnectionButtonEl = $('<button class=\"aui-button aui-button-link\">Test Connection</button>').click(function (e) {
                    var statusEl = rowInstance.$el.children().eq(5);
                    var throbber = statusEl.children().first();

                    var model = {
                        id: rowInstance.model.attributes.id,
                        location: rowInstance.model.attributes.location,
                        clientId: rowInstance.model.attributes.clientId,
                        clientSecret: rowInstance.model.attributes.clientSecret,
                        bambooUser: rowInstance.model.attributes.bambooUser
                    };
                    testConnection(throbber, model);
                });

                var deleteButtonEl = $('<button class=\"aui-button aui-button-link\">Delete</button>').click(function (e) {
                    removeSpaceConfiguration(rowInstance);
                });

                var parentEl = $('<span></span>').append(editButtonEl, deleteButtonEl, testConnectionButtonEl);
                return parentEl;
            }
        });
        spaceTable = new AJS.RestfulTable({
            el: jQuery("#configuration-rest-table"),
            resources: {
                all: octanePluginContext.octaneAdminBaseUrl + "space-configs"
            },
            columns: [
                {id: "id", header: "Instance ID"},
                {id: "location", header: "Location"},
                {id: "clientId", header: "Client ID"},
                {id: "bambooUser", header: "Bamboo user"}
            ],
            autoFocus: false,
            allowEdit: false,
            allowReorder: false,
            allowCreate: false,
            allowDelete: false,
            noEntriesMsg: "No space configuration is defined.",
            loadingMsg: "Loading ...",
            views: {
                row: MyRow
            }
        });
    }

    function validateRequiredFieldsFilled() {
        //validate
        var validationFailed = !validateMissingRequiredField($("#location").val(), "#locationError");
        validationFailed = !validateMissingRequiredField($("#clientId").val(), "#clientIdError") || validationFailed;
        validationFailed = !validateMissingRequiredField($("#clientSecret").val(), "#clientSecretError") || validationFailed;
        validationFailed = !validateMissingRequiredField($("#bambooUser").val(), "#bambooUserError") || validationFailed;
        return !validationFailed;
    }

    function validateMissingRequiredField(value, errorSelector) {
        return validateConditionAndUpdateErrorField(value, 'Value is missing', errorSelector);
    }

    function validateConditionAndUpdateErrorField(condition, errorMessage, errorSelector) {
        if (!condition) {
            $(errorSelector).text(errorMessage);
            return false;
        } else {
            $(errorSelector).text('');
            return true;
        }
    }

    function removeSpaceConfiguration(row) {
        var model = row.model.attributes;
        var n = model.location.indexOf('#');
        s = model.location.substring(0, n != -1 ? n : model.location.length);

        $("#space-to-delete").text(s);

        $( "#warning-dialog-confirm" ).off();
        $( "#warning-dialog-cancel" ).off();

        AJS.dialog2("#warning-dialog").show();
        AJS.$("#warning-dialog-confirm").click(function (e) {
            e.preventDefault();
            AJS.dialog2("#warning-dialog").hide();

            $.ajax({
                url: spaceTable.options.resources.all + "/" + model.id, type: "DELETE",
            }).done(function () {
                spaceTable.removeRow(row)
            }).fail(function (request) {
                AJS.flag({type: 'error', body: request.responseText});
            });
        });

        AJS.$("#warning-dialog-cancel").click(function (e) {
            e.preventDefault();
            AJS.dialog2("#warning-dialog").hide();
        });
    }

    function showSpaceConfigurationDialog(rowForEdit) {
        clearStatusMessage();

        var editMode = !!rowForEdit;
        var editEntity = editMode ? rowForEdit.model.attributes : null;
        AJS.$("#location").val(editEntity ? editEntity.location : "");
        AJS.$("#clientId").val(editEntity ? editEntity.clientId : "");
        AJS.$("#clientSecret").val(editEntity ? editEntity.clientSecret : "");
        AJS.$("#bambooUser").val(editEntity ? editEntity.bambooUser : "");
        $("#dialog-message").hide();
        AJS.dialog2("#config-dialog").show();
    }

    function refreshRow(model) {
        if (octanePluginContext.currentRow) {
            var rowModel = octanePluginContext.currentRow.model.attributes;
            rowModel.location = model.location;
            rowModel.clientId = model.clientId;
            rowModel.clientSecret = model.clientSecret;
            rowModel.bambooUser = model.bambooUser;
            octanePluginContext.currentRow.render();
        } else {
            spaceTable.addRow(model);
        }
    }

    function testConnection(throbber, model) {
        clearStatusMessage();
        throbber.addClass("test-connection-status");
        throbber.removeClass("test-connection-status-successful");
        throbber.removeClass("test-connection-status-failed");
        throbber.attr("title", "Testing connection ...");

        var myJSON = JSON.stringify(model);
        $.ajax({
            url: octanePluginContext.octaneAdminBaseUrl + "test/testconnection",
            type: "POST",
            data: myJSON,
            dataType: "text",
            contentType: "application/json"
        , success:function (response) {
            throbber.addClass("test-connection-status-successful");
            throbber.attr("title", "Test connection is successful. \n\n" + response);
        }, error:function (request, status, error) {
            throbber.addClass("test-connection-status-failed");
            throbber.attr("title", "Test connection is failed : " + request.responseText);

        }});
    }

}(AJS.$ || jQuery));
