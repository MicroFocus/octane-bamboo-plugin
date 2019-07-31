/*
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
 */

package com.hp.octane.plugins.bamboo.octane;

public class MqmProject {
	private final String location;
	private final String sharedSpace;
	private String errorMsg;

	public MqmProject(String location, String sharedSpace) {
		this.location = location;
		this.sharedSpace = sharedSpace;
	}

	public MqmProject(String errorMsg) {
		this.location = "";
		this.sharedSpace = "";
		this.errorMsg = errorMsg;
	}

	public String getLocation() {
		return location;
	}

	public String getSharedSpace() {
		return sharedSpace;
	}

	public String getErrorMsg() {
		return errorMsg;
	}

	public void setErrorMsg(String errorMsg) {
		this.errorMsg = errorMsg;
	}

	public boolean hasError() {
		if(errorMsg == null || errorMsg.isEmpty() ){
			return false;
		}
		return true;
	}
}
