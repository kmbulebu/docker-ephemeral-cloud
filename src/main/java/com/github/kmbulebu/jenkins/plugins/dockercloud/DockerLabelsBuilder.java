package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DockerLabelsBuilder {
	
	private Map<String, String> labelsMap = new HashMap<String, String>();
	
	public static final String PLUGIN_NAME = "jenkins.plugin_name";
	public static final String PLUGIN_NAME_VAL = "docker_ephemeral_cloud";
	public static final String CLOUD_NAME = "jenkins.cloud_name";
	public static final String IMAGE_NAME = "jenkins.image_name";
	public static final String LABEL_STRING = "jenkins.label_string";

	public DockerLabelsBuilder() {
		labelsMap.put(PLUGIN_NAME, PLUGIN_NAME_VAL);
	}
	
	public Map<String, String> build() {
		return Collections.unmodifiableMap(labelsMap);
	}
	
	public DockerLabelsBuilder cloudName(String cloudName) {
		labelsMap.put(CLOUD_NAME, sanitize(cloudName));
		return this;
	}
	
	public DockerLabelsBuilder imageName(String imageName) {
		labelsMap.put(IMAGE_NAME, sanitize(imageName));
		return this;
	}
	
	public DockerLabelsBuilder labelString(String labelString) {
		labelsMap.put(LABEL_STRING, labelString);
		return this;
	}
	
	public static String sanitize(String value) {
		// Docker library is escaping spaces and they end up as + in the filter.
		return value.replace(' ', '_');
	}

}
