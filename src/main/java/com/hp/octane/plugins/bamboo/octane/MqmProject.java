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
