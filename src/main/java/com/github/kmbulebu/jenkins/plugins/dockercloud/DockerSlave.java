package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.spotify.docker.client.exceptions.ContainerNotFoundException;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.exceptions.DockerException;

import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Descriptor.FormException;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.NodeProperty;

/**
 * Docker cloud provider.
 * 
 * @author Kevin Bulebush (kmbulebu@gmail.com)
 */
public class DockerSlave extends AbstractCloudSlave {

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
	public DockerSlave(DockerLauncher launcher, DockerCloud dockerCloud, String name, String dockerId, String nodeDescription, String remoteFS, Mode mode, String labelString, List<? extends NodeProperty<?>> nodeProperties) throws FormException, IOException {
		super(name, nodeDescription, remoteFS, 1, mode, labelString, launcher, new OnceRetentionStrategy(0), nodeProperties);
		this.dockerCloud = dockerCloud;
		this.dockerId = dockerId;
	}
	
	public DockerCloud getDockerCloud() {
		return dockerCloud;
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
			DockerClient docker = null;
			try {
				docker = dockerCloud.buildDockerClient();
				listener.getLogger().println("Stopping container " + dockerId);
				docker.stopContainer(dockerId, 1);
				listener.getLogger().println("Removing container " + dockerId + " and volumes.");
				docker.removeContainer(dockerId, RemoveContainerParam.forceKill(true), RemoveContainerParam.removeVolumes(true));
			} catch (ContainerNotFoundException e) {
				LOGGER.log(Level.INFO, "Container " + dockerId + " not found. Ignoring.");
			} catch (DockerException e) {
				LOGGER.log(Level.SEVERE, "Error while stopping and removing container " + dockerId, e);
				throw new IOException(e.getMessage(), e);
			} catch (DockerCertificateException e) {
				LOGGER.log(Level.SEVERE, "Certificate error while stopping and removing container " + dockerId, e);
				throw new IOException(e.getMessage(), e);
			} finally {
				if (docker != null) {
					docker.close();
				}
			}
		}
		listener.getLogger().println("Slave node terminated in container " + dockerId + ".");
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
