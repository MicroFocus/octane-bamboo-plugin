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

package com.hp.octane.plugins.bamboo.octane;

import com.atlassian.bamboo.fileserver.SystemDirectory;
import com.hp.octane.integrations.services.logging.CommonLoggerContextUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * SDK based (log4j brought by SDK) logger provider
 * Main purpose of this custom logger provider is to ensure correct logs location configuration at the earliest point of the plugin initialization
 */
public final class SDKBasedLoggerProvider {
	private static volatile boolean sysParamConfigured = false;
	private static volatile boolean allowedOctaneStorageExist = false;

	private SDKBasedLoggerProvider(){
		//CodeClimate  : Add a private constructor to hide the implicit public one.
	}
	public static Logger getLogger(Class<?> type) {
		initOctaneAllowedStorageProperty();
		return LogManager.getLogger(type);
	}

	public static void initOctaneAllowedStorageProperty() {
		if (!sysParamConfigured) {
			System.setProperty("octaneAllowedStorage", getAllowedStorageFile().getAbsolutePath() + File.separator);
			//CommonLoggerContextUtil.configureLogger(getAllowedStorageFile());
			sysParamConfigured = true;
		}
	}

	public static File getAllowedStorageFile() {
		File f = new File(SystemDirectory.getApplicationHome(), "octanePluginContent");
		if (!allowedOctaneStorageExist) {
			f.mkdirs();
			allowedOctaneStorageExist = true;
		}
		return f;
	}
}
