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
import com.spotify.docker.client.DockerClient.ListContainersParam;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
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


	private Boolean useTLS;
	private String uri;
	private String certificatesPath;
	private String containerNamePrefix;

	private List<DockerImage> images;

	@DataBoundConstructor
	public DockerCloud(String name, String instanceCapStr, Boolean useTLS, String uri, 
			String certificatesPath, List<? extends DockerImage> images, String containerNamePrefix) {
		super(name, instanceCapStr);
		this.useTLS = useTLS;
		this.uri = uri;
		this.certificatesPath = certificatesPath;
		this.containerNamePrefix = containerNamePrefix;

		if (images == null) {
			this.images = Collections.emptyList();
		} else {
			this.images = new ArrayList<DockerImage>(images);
		}
		
	}
	
	public String getName() {
		return super.name;
	}

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
	
	public String getContainerNamePrefix() {
		return containerNamePrefix;
	}
	
	@DataBoundSetter
	public void setContainerNamePrefix(String containerNamePrefix) {
		this.containerNamePrefix = containerNamePrefix;
	}

	@Override
	public Collection<PlannedNode> provision(Label label, int excessWorkload) {
		// Identify which configuration supports the specified label.
		final DockerImage foundImage = findDockerImageForLabel(label);
		
		if (foundImage == null) {
			LOGGER.severe("Asked to create node but we couldn't find a matching docker image config.");
			return Collections.emptyList();
		}
		
		// Check if we're above a container total limit.
		try {
			final int containerCount = countAllRunningContainers();
			LOGGER.log(Level.FINE, "Found " + containerCount + " total running slave containers.");
			if (containerCount >= getInstanceCap()) {
				LOGGER.log(Level.INFO, "Instance cap met. Can not provision any more.");
				return Collections.emptyList();
			}
		} catch (DockerException e) {
			LOGGER.log(Level.WARNING, "Could not count all running containers. " + e.getMessage(), e);
			return Collections.emptyList();
		} catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "Interrupted while counting all running containers.", e);
			return Collections.emptyList();
		} catch (DockerCertificateException e) {
			LOGGER.log(Level.WARNING, "Could not count all running containers. There's a problem with the TLS certificates. " + e.getMessage(), e);
			return Collections.emptyList();
		}
		
		// Check if we're above a particular image container limit.
		try {
			final int containerCount = countRunningContainers(foundImage.getName());
			LOGGER.log(Level.FINE, "Found " + containerCount + " running slave containers for this image.");
			if (containerCount >= foundImage.getInstanceCap()) {
				LOGGER.log(Level.INFO, "Image instance cap met. Can not provision any more.");
				return Collections.emptyList();
			}
		} catch (DockerException e) {
			LOGGER.log(Level.WARNING, "Could not count image's running containers. " + e.getMessage(), e);
			return Collections.emptyList();
		} catch (InterruptedException e) {
			LOGGER.log(Level.WARNING, "Interrupted while counting image's running containers.", e);
			return Collections.emptyList();
		} catch (DockerCertificateException e) {
			LOGGER.log(Level.WARNING, "Could not count image's running containers. There's a problem with the TLS certificates. " + e.getMessage(), e);
			return Collections.emptyList();
		}

		// Provision some nodes
		List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();

		for (int i = 1; i <= excessWorkload; i++) {
			DockerClient dockerClient;
			try {
				dockerClient = buildDockerClient();
			} catch (DockerCertificateException e) {
				LOGGER.log(Level.SEVERE, "Could not request a new docker container. There's a problem with the TLS certificates. " + e.getMessage(), e);
				return Collections.emptyList();
			}
			final CreateContainerCallable containerCallable = new CreateContainerCallable(dockerClient, this, foundImage);
			plannedNodes.add(new NodeProvisioner.PlannedNode(name, Computer.threadPoolForRemoting.submit(containerCallable), 1));
		}
		return plannedNodes;
	}

	@Override
	public boolean canProvision(Label label) {
		// Check if we have an image that matches the label.
		final DockerImage foundImage = findDockerImageForLabel(label);
		if (foundImage == null) {
			LOGGER.log(Level.FINE, "No matching labels.");
			return false;
		} else {	
			return true;
		}
	}
	
	private int countAllRunningContainers() throws DockerException, InterruptedException, DockerCertificateException {
		return countRunningContainers(null);
	}
	
	private int countRunningContainers(String imageName) throws DockerException, InterruptedException, DockerCertificateException {
		// The could be a performance hog in huge Docker environments. Replacing with
		// docker labels/metadata is the plan.
		DockerClient dockerClient = null;
		
		try {
			dockerClient = buildDockerClient();
			List<ListContainersParam> params = new ArrayList<ListContainersParam>(2);
			params.add(ListContainersParam.withLabel(DockerLabelsBuilder.CLOUD_NAME, DockerLabelsBuilder.sanitize(getName())));
			if (imageName != null) {
				params.add(ListContainersParam.withLabel(DockerLabelsBuilder.IMAGE_NAME, DockerLabelsBuilder.sanitize(imageName)));
			}
			return dockerClient.listContainers(params.toArray(new ListContainersParam[params.size()])).size();
		} finally {
			if (dockerClient != null) {
				dockerClient.close();
			}
		}
	}

	public List<DockerImage> getImages() {
		return images;
	}

	public synchronized void addImage(DockerImage image) {
		images.add(image);
	}

	public synchronized void removeImage(DockerImage image) {
		images.remove(image);
	}

	public Object readResolve() {
		for (DockerImage image : getImages()) {
			image.readResolve();
		}
		return this;
	}
	
	private DockerImage findDockerImageForLabel(Label label) {
		for (DockerImage image : getImages()) {
			if (dockerImageMatchesLabel(image, label)) {
				return image;
			}
		}
		return null;
	}
	
	private boolean dockerImageMatchesLabel(DockerImage image, Label label) {
		return label == null ? image.getMode() == Node.Mode.NORMAL : label.matches(Label.parse(image.getLabelString()));
	}

	@Extension
	public static class DescriptorImpl extends Descriptor<Cloud> {

		public DescriptorImpl() {
			super(DockerCloud.class);
		}

		@Override
		public String getDisplayName() {
			return "Docker Ephemeral Cloud";
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
			DockerClient client = null;
			try {
				final URI dockerUri = URI.create(uri);

				if (Boolean.TRUE.equals(useTLS)) {
					final Path certsPath = Paths.get(certificatesPath);
					final DockerCertificates dockerCerts = new DockerCertificates(certsPath);
					client = new DefaultDockerClient(dockerUri, dockerCerts);
				} else {
					client = new DefaultDockerClient(dockerUri);
				}

				result = client.ping();
			} catch (Exception e) {
				return FormValidation.error(e, e.getMessage());
			} finally {
				if (client != null) {
					client.close();
				}
			}
			return FormValidation.ok(result);
		}
		
		public FormValidation doCheckContainerNamePrefix(@QueryParameter String containerNamePrefix) {
			if (containerNamePrefix == null || containerNamePrefix.length() < 1) {
				FormValidation.error("Required");
			}
			return FormValidation.ok();
		}

	}

	
	public DockerClient buildDockerClient() throws DockerCertificateException {
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
