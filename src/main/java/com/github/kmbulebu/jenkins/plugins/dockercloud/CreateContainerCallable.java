package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.Arrays;
import java.util.logging.Logger;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.exceptions.ImageNotFoundException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.ImageInfo;

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
			LOGGER.info("Finished pulling image " + dockerImage.getDockerImageName() + ".");
		} 

		final ImageInfo imageInfo = dockerClient.inspectImage(dockerImage.getDockerImageName());
		final String imageUser = imageInfo.config().user();
		
		
		final ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder().image(dockerImage.getDockerImageName());
		final HostConfig.Builder hostConfigBuilder = HostConfig.builder();
		
		LOGGER.fine("Setting cmd to 'cat'.");
		//containerConfigBuilder.user("root");
		containerConfigBuilder.attachStderr(true).attachStdout(true).tty(true);
		//containerConfigBuilder.tty(true).cmd(new String[] {"sh","-c","mkfifo /jenkins-out && chmod a+w /jenkins-out && tail -f /jenkins-out"});
		containerConfigBuilder.cmd(new String[] {"cat"});
		
		
		// Set CPU shares. Hopefully this won't be a problem on any exotic Docker platforms.
		hostConfigBuilder.cpuShares(dockerImage.getCpuShares());
		
		if (dockerImage.isMemoryLimited()) {
			final Long memory = dockerImage.getMemoryLimitMB() * 1024 * 1024; // MB to bytes.
			LOGGER.fine("Setting memory limit to '" + memory + "' for container.");
			hostConfigBuilder.memory(memory);
			
			// Can only limit swap if you limit memory.
			if (dockerImage.isSwapLimited()) {
				final Long swap = dockerImage.getSwapLimitMB() * 1024 * 1024; // MB to bytes
				final Long memorySwap = swap + memory;
				LOGGER.fine("Setting memorySwap limit to '" + memorySwap + "' for container.");
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
		
		
		// Volumes
		String[] volumeLines;
		if (dockerImage.getVolumes() == null) {
			volumeLines = new String[] {};
		} else {
			volumeLines = dockerImage.getVolumes().split("$");
		}
		
		LOGGER.fine("Adding host binds and container volumes: " + Arrays.toString(volumeLines));
		hostConfigBuilder.binds(volumeLines);
		containerConfigBuilder.volumes(volumeLines);
		
		// Add host config 
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
		} else if (imageUser != null && imageUser.trim().length() > 0) {
			// Use the user embedded in image.
			execUser = imageUser.trim();
		} else {
			// Do not specify (usually root)
			execUser = null;
		}
		
		final DockerLauncher launcher = new DockerLauncher(execUser);
		final String name = creation.id().substring(0, 12);
		final DockerSlave slave = new DockerSlave(launcher, dockerCloud, name, creation.id(), getNodeDescription(), dockerImage.getRemoteFS(), dockerImage.getMode(), dockerImage.getLabelString(), dockerImage.getNodeProperties());
		
		return slave;
	}
	
}

