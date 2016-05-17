package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.logging.Logger;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;

import hudson.model.Node;

/**
 * Docker cloud provider.
 * 
 * @author Kevin Bulebush (kmbulebu@gmail.com)
 */
public class CreateContainerCallable extends DockerClientCallable<Node> {
	
	private static final Logger LOGGER = Logger.getLogger(CreateContainerCallable.class.getName());
	
	private final DockerCloud dockerCloud;
	private final DockerImage dockerImage;

	public CreateContainerCallable(DockerClient dockerClient, DockerCloud dockerCloud, DockerImage dockerImage) {
		super(dockerClient);
		this.dockerCloud = dockerCloud;
		this.dockerImage = dockerImage;
	}
	
	private String getNodeDescription() {
		return "Docker container built from image config '" + dockerImage.getName() + "' using docker image '" + dockerImage.getDockerImageName() + "' running in the '" + dockerCloud.getDisplayName() + "' docker cloud.";
	}

	@Override
	public Node callWithDocker(DockerClient dockerClient) throws Exception {
		
	    // Pull image.
		boolean imageExists;
		try {
			LOGGER.fine("Checking if image " + dockerImage.getDockerImageName() + " exists.");
			if (dockerClient.inspectImage(dockerImage.getDockerImageName()) != null) {
				imageExists = true;
			} else {
				// Should be unreachable.
				imageExists = false;
			}
		} catch (ImageNotFoundException e) {
			imageExists = false;
		}
		
		LOGGER.fine("Image " + dockerImage + " exists? " + imageExists + ", Pull disabled? " + dockerImage.isPullDisabled());
		
		if (!imageExists || dockerImage.isPullForced()) {
			if (dockerImage.isPullDisabled()) {
				throw new IllegalStateException("Image '" + dockerImage.getDockerImageName() + "' does not exist on Docker cloud '" + dockerCloud.getDisplayName() + "' and pull is disabled.");
			} 
			LOGGER.info("Pulling image " + dockerImage.getDockerImageName() + ".");
			dockerClient.pull(dockerImage.getDockerImageName());
			LOGGER.fine("Finished pulling image " + dockerImage.getDockerImageName() + ".");
		} 

		final ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder().image(dockerImage.getDockerImageName());
		final HostConfig.Builder hostConfigBuilder = HostConfig.builder();
		
		LOGGER.info("Setting cmd to 'cat' with a pseudo tty.");
		containerConfigBuilder.tty(true).cmd(new String[] {"cat"});
		
		// Set CPU shares. Hopefully this won't be a problem on any exotic Docker platforms.
		hostConfigBuilder.cpuShares(dockerImage.getCpuShares());
		
		if (dockerImage.isMemoryLimited()) {
			final Long memory = dockerImage.getMemoryLimitMB() * 1024 * 1024; // MB to bytes.
			LOGGER.info("Setting memory limit to '" + memory + "' for container.");
			hostConfigBuilder.memory(memory);
			
			// Can only limit swap if you limit memory.
			if (dockerImage.isSwapLimited()) {
				final Long swap = dockerImage.getSwapLimitMB() * 1024 * 1024; // MB to bytes
				final Long memorySwap = swap + memory;
				LOGGER.info("Setting memorySwap limit to '" + memorySwap + "' for container.");
				hostConfigBuilder.memorySwap(memorySwap);
			}
		}
		
		// Setup working directory
		if (dockerImage.getWorkingDir() != null && dockerImage.getWorkingDir().length() > 0) {
			containerConfigBuilder.workingDir(dockerImage.getWorkingDir());
		}
		
		// Apply labels to the container to make tracking it easier.
		final DockerLabelsBuilder labelsBuilder = new DockerLabelsBuilder();
		labelsBuilder.cloudName(dockerCloud.getName());
		labelsBuilder.imageName(dockerImage.getName());
		labelsBuilder.labelString(dockerImage.getLabelString());
		containerConfigBuilder.labels(labelsBuilder.build());
		
		
		// Set privileged if requested.
		hostConfigBuilder.privileged(dockerImage.isPrivileged());
		containerConfigBuilder.hostConfig(hostConfigBuilder.build());
		
		
		LOGGER.info("Creating container from image " + dockerImage.getDockerImageName() + ".");
		final ContainerCreation creation = dockerClient.createContainer(containerConfigBuilder.build());
		
		LOGGER.info("Starting container with id " + creation.id() + ".");
		dockerClient.startContainer(creation.id());

		// Tell the launcher which user to run under
		String execUser;
		if (dockerImage.getUserOverride() != null && dockerImage.getUserOverride().trim().length() > 0) {
			LOGGER.info("Setting user to '" + dockerImage.getUserOverride() + "' for container.");
			execUser = dockerImage.getUserOverride();
		} else {
			execUser = null;
		}
		
		final DockerLauncher launcher = new DockerLauncher(execUser);
		final String name = creation.id().substring(0, 12);
		final DockerSlave slave = new DockerSlave(launcher, dockerCloud, name, creation.id(), getNodeDescription(), dockerImage.getRemoteFS(), dockerImage.getMode(), dockerImage.getLabelString(), dockerImage.getNodeProperties());
		
		return slave;
	}
	
}

