package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node.Mode;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;

/**
 * Docker cloud provider.
 * 
 * @author Kevin Bulebush (kmbulebu@gmail.com)
 */
public class DockerCloud extends AbstractCloudImpl {

	private static final Logger LOGGER = Logger.getLogger(DockerCloud.class.getName());

	private transient DockerClient dockerClient;

	private Boolean useTLS;
	private String uri;
	private String certificatesPath;

	// private List<DockerImage> images;

	@DataBoundConstructor
	public DockerCloud(String name, String instanceCapStr, Boolean useTLS, String uri, String certificatesPath) {
		super(name, instanceCapStr);
		this.useTLS = useTLS;
		this.uri = uri;
		this.certificatesPath = certificatesPath;
	}
	
	

	/*
	 * public DockerCloud(String name, String instanceCapStr, List<? extends
	 * DockerImage> images) { super(name, instanceCapStr); this.server = new
	 * DockerServerEndpoint(null, null);
	 * 
	 * if (images == null) { this.images = Collections.emptyList(); } else {
	 * this.images = new ArrayList<DockerImage>(images); }
	 * 
	 * }
	 */





	public Boolean getUseTLS() {
		return useTLS;
	}


	@DataBoundSetter
	public void setUseTLS(Boolean useTLS) {
		this.useTLS = useTLS;
	}



	public String getUri() {
		return uri;
	}


	@DataBoundSetter
	public void setUri(String uri) {
		this.uri = uri;
	}



	public String getCertificatesPath() {
		return certificatesPath;
	}


	@DataBoundSetter
	public void setCertificatesPath(String certificatesPath) {
		this.certificatesPath = certificatesPath;
	}



	@Override
	public Collection<PlannedNode> provision(Label label, int excessWorkload) {
		try {
			// TODO ?? Count containers in progress but not yet read, subtract
			// from excessWorkload.

			// TODO Identify which configuration supports the specified label.

			List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();

			for (int i = 1; i <= excessWorkload; i++) {

				LOGGER.warning("Call to create node");
				CreateContainerCallable containerCallable = new CreateContainerCallable("/", Mode.NORMAL, label == null ? "" : label.toString(), this, "java:8");
				plannedNodes.add(new NodeProvisioner.PlannedNode(name, Computer.threadPoolForRemoting.submit(containerCallable), 1));
			}
			return plannedNodes;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Docker", e);
			return Collections.emptyList();
		}
	}

	protected synchronized DockerClient getDockerClient() {
		try {
			if (dockerClient == null) {
				dockerClient = buildDockerClient(uri, useTLS, certificatesPath);
			}
			return dockerClient;
		} catch (Exception e) {
			// Wrap in unchecked exception.
			throw new IllegalArgumentException(e);
		}
	}

	@Override
	public boolean canProvision(Label label) {
		// Check if we're above a container total limit.

		// Check if we have an image that matches the label.

		// Check if we're above that image's container limit.

		// TODO Auto-generated method stub
		return true;
	}


	/*
	 * public List<DockerImage> getImages() { return images; }
	 * 
	 * public synchronized void addImage(DockerImage image) { images.add(image);
	 * }
	 * 
	 * public synchronized void removeImage(DockerImage image) {
	 * images.remove(image); }
	 * 
	 * public Object readResolve() { for (DockerImage image : getImages()) {
	 * image.readResolve(); } return this; }
	 */

	@Extension
	public static class DescriptorImpl extends Descriptor<Cloud> {

		public DescriptorImpl() {
			super(DockerCloud.class);
		}

		@Override
		public String getDisplayName() {
			return "Docker Cloud";
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
		
		public FormValidation doCheckUri(@QueryParameter String uri) {
			if (uri == null || uri.length() < 1) {
				return FormValidation.error("Required");
			} 
			try {
				new URI(uri);
			} catch (URISyntaxException e) {
				return FormValidation.error("Invalid URI.");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doCheckCertificatesPath(@QueryParameter String certificatesPath, @QueryParameter Boolean useTLS) {
			if (useTLS == null || !useTLS) {
				return FormValidation.ok();
			}
			if (certificatesPath == null || certificatesPath.length() < 1) {
				return FormValidation.error("Required for TLS.");
			} 
			
			try {
				final Path certsPath = Paths.get(certificatesPath);
				if (Files.notExists(certsPath)) {
					return FormValidation.error("Does not exist.");
				}
				if (!Files.isDirectory(certsPath)) {
					return FormValidation.error("Not a directory.");
				}
			} catch (InvalidPathException e) {
				return FormValidation.error("Invalid path.");
			}
			return FormValidation.ok();
		}
		
		public FormValidation doTestConnection(@QueryParameter Boolean useTLS, @QueryParameter String uri, @QueryParameter String certificatesPath) {
			String result;
			try {
				result = buildDockerClient(uri, useTLS, certificatesPath).ping();
			} catch (Exception e) {
				return FormValidation.error(e, e.getMessage());
			}
			return FormValidation.ok(result);
		}

	}

	 protected static DockerClient buildDockerClient(String uri, Boolean useTLS, String certificatesPath) throws Exception {
		
		final URI dockerUri = URI.create(uri); 
		
		DockerClient dockerClient;
		
		if (Boolean.TRUE.equals(useTLS)) {
			final Path certsPath = Paths.get(certificatesPath);
			final DockerCertificates dockerCerts = new DockerCertificates(certsPath);
			dockerClient = new DefaultDockerClient(dockerUri, dockerCerts);
		} else {
			dockerClient = new DefaultDockerClient(dockerUri);
		}
		
		return dockerClient;
	  
	}
	 

}
