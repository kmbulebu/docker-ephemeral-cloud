package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

public class DockerImage implements Describable<DockerImage> {
	
	private static final Logger LOGGER = Logger.getLogger(DockerImage.class.getName());
	
	private String name;
	private String labelString;
	private Node.Mode mode;
	private int instanceCap;
	private String dockerImageName;
	private String remoteFS;
	
	// force pull
	// disable pull
	// Registery auth
	// user
	// ports
	// volumes
	// cpu shares
	// memory and swap
	
	@DataBoundConstructor
	public DockerImage(String name, String labelString, Node.Mode mode, String instanceCapStr, String dockerImageName, String remoteFS) {
		this.name = name;
		this.labelString = labelString;
		this.mode = mode;
		this.dockerImageName = dockerImageName;
		this.remoteFS = remoteFS;
		
		if (instanceCapStr == null || "".equals(instanceCapStr)) {
			instanceCap = Integer.MAX_VALUE;
		} else {
			instanceCap = Integer.parseInt(instanceCapStr);
		}
	}
	
	public String getName() {
		return name;
	}

	@DataBoundSetter
	public void setName(String name) {
		this.name = name;
	}

	public String getLabelString() {
		return labelString;
	}
	
	@DataBoundSetter
	public void setLabelString(String labelString) {
		this.labelString = labelString;
	}
	
	public Node.Mode getMode() {
		return mode;
	}
	
	@DataBoundSetter
	public void setMode(Node.Mode mode) {
		this.mode = mode;
	}

	public int getInstanceCap() {
		return instanceCap;
	}

	@DataBoundSetter
	public void setInstanceCap(int instanceCap) {
		this.instanceCap = instanceCap;
	}

	public String getDockerImageName() {
		return dockerImageName;
	}

	@DataBoundSetter
	public void setDockerImageName(String dockerImageName) {
		this.dockerImageName = dockerImageName;
	}
	
	public String getRemoteFS() {
		return remoteFS;
	}
	
	@DataBoundSetter
	public void setRemoteFS(String remoteFS) {
		this.remoteFS = remoteFS;
	}

	@Override
	public Descriptor<DockerImage> getDescriptor() {
		return (DescriptorImpl) Jenkins.getInstance().getDescriptor(getClass());	
	}
	
	public Object readResolve() {
		return this;
	}
	
	@Extension
    public static final class DescriptorImpl extends Descriptor<DockerImage> {
		
		public DescriptorImpl() {
			super(DockerImage.class);
		}

		@Override
		public String getDisplayName() {
			return "Docker Image";
		}
		
		public FormValidation doCheckName(@QueryParameter String name) {
			if (name == null || name.length() < 1) {
				return FormValidation.error("Required");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckInstanceCapStr(@QueryParameter String instanceCapStr) {
			if (instanceCapStr == null || instanceCapStr.length() < 1) {
				return FormValidation.error("Required");
			}
			if (!instanceCapStr.matches("\\d+")) {
				return FormValidation.error("Must be a number");
			}
			final int number = Integer.parseInt(instanceCapStr);
			if (number < 1) {
				return FormValidation.error("Must be at least one.");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckDockerImageName(@QueryParameter String dockerImageName) {
			if (dockerImageName == null || dockerImageName.length() < 1) {
				return FormValidation.error("Required");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckRemoteFS(@QueryParameter String remoteFS) {
			if (remoteFS == null || remoteFS.length() < 1) {
				return FormValidation.error("Required");
			}
			return FormValidation.ok();
		}
		
	}

}
