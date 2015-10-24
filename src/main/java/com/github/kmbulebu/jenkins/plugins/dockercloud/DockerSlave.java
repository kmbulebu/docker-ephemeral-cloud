package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.spotify.docker.client.ContainerNotFoundException;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerException;

import hudson.Extension;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.NodeProperty;

/**
 * Docker cloud provider.
 * 
 * @author Kevin Bulebush (kmbulebu@gmail.com)
 */
public class DockerSlave extends AbstractCloudSlave {

	private final static ResourceBundleHolder RESOURCE_BUNDLE = ResourceBundleHolder.get(Messages.class);

	private static final Logger LOGGER = Logger.getLogger(DockerSlave.class.getName());

	/**
	 * Track the container id so that it can be deleted by id later.
	 */
	private String dockerId;

	private DockerCloud dockerCloud;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@DataBoundConstructor
	public DockerSlave(DockerCloud dockerCloud, String name, String nodeDescription, String remoteFS, Mode mode, String labelString) throws FormException, IOException {
		super(name, nodeDescription, remoteFS, 1, mode, labelString, new JNLPLauncher(), new OnceRetentionStrategy(0), Collections.<NodeProperty<Node>> emptyList());
		this.dockerCloud = dockerCloud;
	}

	public String getDockerId() {
		return dockerId;
	}

	@DataBoundSetter
	public void setDockerId(String dockerId) {
		this.dockerId = dockerId;
	}

	@Override
	public DockerComputer createComputer() {
		return new DockerComputer(this);
	}


	@Override
	protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
		if (dockerId == null) {
			listener.getLogger().println("No container id exists to remove.");
		} else {
			// Delete from docker.
			try {
				final DockerClient docker = dockerCloud.getDockerClient();
				listener.getLogger().println("Stopping container " + dockerId);
				docker.stopContainer(dockerId, 1);
				listener.getLogger().println("Removing container " + dockerId + " and volumes.");
				docker.removeContainer(dockerId, true);
			} catch (ContainerNotFoundException e) {
				LOGGER.log(Level.INFO, "Container " + dockerId + " not found. Ignoring.");
			} catch (DockerException e) {
				LOGGER.log(Level.SEVERE, "Error while stopping and removing container " + dockerId, e);
				throw new IOException(e.getMessage(), e);
			}
		}
		listener.getLogger().println("Slave node terminated.");
	}

	@Extension
	public static final class DescriptorImpl extends SlaveDescriptor {

		@Override
		public String getDisplayName() {
			return "Docker Container Slave";
		};

		@Override
		public boolean isInstantiable() {
			return false;
		}

	}
}
