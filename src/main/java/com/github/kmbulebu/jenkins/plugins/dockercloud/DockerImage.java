package com.github.kmbulebu.jenkins.plugins.dockercloud;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

/**
 * Docker cloud provider.
 * 
 * @author Kevin Bulebush (kmbulebu@gmail.com)
 */
public class DockerImage implements Describable<DockerImage> {
	
	private String name;
	private String labelString;
	private Node.Mode mode;
	private int instanceCap;
	private String dockerImageName;
	private String remoteFS;
	
	// Advanced
	private boolean pullForced;
	private boolean pullDisabled;
	private String userOverride;
	private long cpuShares;
	private boolean memoryLimited;
	private long memoryLimitMB;
	private boolean swapLimited;
	private long swapLimitMB;
	private boolean privileged;
	
	// cpuset
	// domain name
	// hostname
	// working directory
	// privileged
	// dns
	// Registery auth
	// ports
	// volumes
	
	@DataBoundConstructor
	public DockerImage(String name, String labelString, Node.Mode mode, String instanceCapStr, String dockerImageName, 
			String remoteFS, boolean pullForced, boolean pullDisabled, String userOverride, long cpuShares,
			boolean memoryLimited, long memoryLimitMB, boolean swapLimited, long swapLimitMB, boolean privileged) {
		this.name = name;
		this.labelString = labelString;
		this.mode = mode;
		this.dockerImageName = dockerImageName;
		this.remoteFS = remoteFS;
		
		this.pullForced = pullForced;
		this.pullDisabled = pullDisabled;
		this.userOverride = userOverride;
		
		// For migrating existing configs without CPU shares.
		if (this.cpuShares == 0) {
			this.cpuShares = 1024;
		} else {
			this.cpuShares = cpuShares;
		}
		
		this.memoryLimited = memoryLimited;
		this.memoryLimitMB = memoryLimitMB;
		this.swapLimited = swapLimited;
		this.swapLimitMB = swapLimitMB;
		
		this.privileged = privileged;
		
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
	
	public boolean isPullForced() {
		return pullForced;
	}
	
	@DataBoundSetter
	public void setPullForced(boolean pullForced) {
		this.pullForced = pullForced;
	}
	
	public boolean isPullDisabled() {
		return pullDisabled;
	}
	
	@DataBoundSetter
	public void setPullDisabled(boolean pullDisabled) {
		this.pullDisabled = pullDisabled;
	}
	
	public String getUserOverride() {
		return userOverride;
	}
	
	@DataBoundSetter
	public void setUserOverride(String userOverride) {
		this.userOverride = userOverride;
	}
	
	public long getCpuShares() {
		return cpuShares;
	}
	
	@DataBoundSetter
	public void setCpuShares(long cpuShares) {
		this.cpuShares = cpuShares;
	}

	public boolean isMemoryLimited() {
		return memoryLimited;
	}

	@DataBoundSetter
	public void setMemoryLimited(boolean memoryLimited) {
		this.memoryLimited = memoryLimited;
	}

	public long getMemoryLimitMB() {
		return memoryLimitMB;
	}

	@DataBoundSetter
	public void setMemoryLimitMB(long memoryLimitMB) {
		this.memoryLimitMB = memoryLimitMB;
	}

	public boolean isSwapLimited() {
		return swapLimited;
	}

	@DataBoundSetter
	public void setSwapLimited(boolean swapLimited) {
		this.swapLimited = swapLimited;
	}

	public long getSwapLimitMB() {
		return swapLimitMB;
	}

	@DataBoundSetter
	public void setSwapLimitMB(long swapLimitMB) {
		this.swapLimitMB = swapLimitMB;
	}
	
	public boolean isPrivileged() {
		return privileged;
	}

	@DataBoundSetter
	public void setPrivileged(boolean privileged) {
		this.privileged = privileged;
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
		
		public FormValidation doCheckCpuShares(@QueryParameter long cpuShares) {
			if (cpuShares < 1l) {
				return FormValidation.error("Must be a positive value.");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckMemoryLimitMB(@QueryParameter boolean memoryLimited, @QueryParameter long memoryLimitMB) {
			if (memoryLimited) {
				if (memoryLimitMB < 1l) {
					return FormValidation.error("Must be a positive value.");
				}
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckSwapLimitMB(@QueryParameter boolean memoryLimited, @QueryParameter boolean swapLimited, @QueryParameter long swapLimitMB) {
			if (memoryLimited && swapLimited) {
				if (swapLimitMB < 1l) {
					return FormValidation.error("Must be a positive value.");
				}
			}
			return FormValidation.ok();
		}
		
		
		
	}

}
