package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.ImageNotFoundException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.HostConfig;

import hudson.model.Node;
import hudson.model.Slave;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;

/**
 * Docker cloud provider.
 * 
 * @author Kevin Bulebush (kmbulebu@gmail.com)
 */
public class CreateContainerCallable implements Callable<Node> {
	
	private static final Logger LOGGER = Logger.getLogger(CreateContainerCallable.class.getName());
	
	// Multiply the two for the time in ms we wait for the container to start.
	private static final int CONTAINER_START_WAIT_INTERVAL_MS = 1000;
	private static final int CONTAINER_START_WAIT_MAX_COUNT = 60;
	
	private final String name;
	
	private final DockerCloud dockerCloud;
	private final DockerImage dockerImage;

	public CreateContainerCallable(DockerCloud dockerCloud, DockerImage dockerImage) {
		super();
		this.name = UUID.randomUUID().toString();
		this.dockerCloud = dockerCloud;
		this.dockerImage = dockerImage;
	}
	
	private String getNodeDescription() {
		return "Docker container built from image config '" + dockerImage.getName() + "' using docker image '" + dockerImage.getDockerImageName() + "' running in the '" + dockerCloud.getDisplayName() + "' docker cloud.";
	}

	@Override
	public Node call() throws Exception {
		LOGGER.fine("Creating docker slave node with name " + name);
		final DockerSlave slave = new DockerSlave(dockerCloud, name, getNodeDescription(), dockerImage.getRemoteFS(), dockerImage.getMode(), dockerImage.getLabelString());
		final DockerClient docker = dockerCloud.getDockerClient();
		
	    // Pull image.
		boolean imageExists;
		try {
			LOGGER.fine("Checking if image " + dockerImage.getDockerImageName() + " exists.");
			if (docker.inspectImage(dockerImage.getDockerImageName()) != null) {
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
			docker.pull(dockerImage.getDockerImageName());
			LOGGER.fine("Finished pulling image " + dockerImage.getDockerImageName() + ".");
		} 
		
		// This ensures a Computer is created so that the slave url is available.
		Jenkins.getInstance().addNode(slave);
		
		// Create and start container
		final String[] command = new String[] {"sh", "-c", "curl -o slave.jar " + getSlaveJarUrl() + " && java -jar slave.jar -jnlpUrl " + getSlaveJnlpUrl(slave)};
		final ContainerConfig.Builder containerConfigBuilder = ContainerConfig.builder().image(dockerImage.getDockerImageName()).cmd(command);
		final HostConfig.Builder hostConfigBuilder = HostConfig.builder();
		
		// Check for User override.
		if (dockerImage.getUserOverride() != null && dockerImage.getUserOverride().trim().length() > 0) {
			LOGGER.info("Setting user to '" + dockerImage.getUserOverride() + "' for container " + name + ".");
			containerConfigBuilder.user(dockerImage.getUserOverride());
		}
		
		// Set CPU shares. Hopefully this won't be a problem on any exotic Docker platforms.
		containerConfigBuilder.cpuShares(dockerImage.getCpuShares());
		
		if (dockerImage.isMemoryLimited()) {
			final Long memory = dockerImage.getMemoryLimitMB() * 1024 * 1024; // MB to bytes.
			LOGGER.info("Setting memory limit to '" + memory + "' for container " + name + ".");
			containerConfigBuilder.memory(memory);
			
			// Can only limit swap if you limit memory.
			if (dockerImage.isSwapLimited()) {
				final Long swap = dockerImage.getSwapLimitMB() * 1024 * 1024; // MB to bytes
				final Long memorySwap = swap + memory;
				LOGGER.info("Setting memorySwap limit to '" + memorySwap + "' for container " + name + ".");
				containerConfigBuilder.memorySwap(memorySwap);
			}
		}
		
		// Setup working directory
		if (dockerImage.getWorkingDir() != null && dockerImage.getWorkingDir().length() > 0) {
			containerConfigBuilder.workingDir(dockerImage.getWorkingDir());
		}
		
		// Set privileged if requested.
		hostConfigBuilder.privileged(dockerImage.isPrivileged());
		
		LOGGER.info("Creating container " + name + " from image " + dockerImage.getDockerImageName() + ".");
		ContainerCreation creation = docker.createContainer(containerConfigBuilder.build(), name);
		slave.setDockerId(creation.id());
		LOGGER.info("Starting container " + name + " with id " + creation.id() + ".");
		docker.startContainer(creation.id(), hostConfigBuilder.build());
		
		// Wait for Jenkins to get Computer via Launcher online
		int elapsed = 0;
        do {
            Thread.sleep(CONTAINER_START_WAIT_INTERVAL_MS);
            elapsed++;
            LOGGER.info("Waiting for slave on container " + name + " with id " + creation.id() + "...");
        } while (slave.getComputer() != null && !slave.getComputer().isOnline() && elapsed < CONTAINER_START_WAIT_MAX_COUNT);
        
        if (slave.getComputer() == null) {
        	LOGGER.info("slave.getComputer() is null for container " + name + " with id " + creation.id() + ".");
            throw new IllegalStateException("Node was deleted, computer is null");
        }	
        
        if (!slave.getComputer().isOnline()) {
        	LOGGER.info("Slave is not online yet for container " + name + " with id " + creation.id() + ". Giving up.");
            throw new IllegalStateException("Timed out waiting for slave container to come online.");
        }
        
        // Make sure JNLP is connected before returning our slave.
        slave.toComputer().connect(false);

        return slave;
	}
	
	/*
	 *  Get a Jenkins Base URL ending with /
	 */
	private String getJenkinsBaseUrl() {
		String url = JenkinsLocationConfiguration.get().getUrl();
		if (url.endsWith("/")) {
			return url;
		} else {
			return url + '/';
		}
	}
	
	/*
	 * Get the slave jar URL.
	 */
	private String getSlaveJarUrl() {
		return getJenkinsBaseUrl() + "jnlpJars/slave.jar";
	}
	
	/*
	 * Get the JNLP URL for the slave.
	 */
	private String getSlaveJnlpUrl(Slave slave) {
		return getJenkinsBaseUrl() + slave.getComputer().getUrl() + "slave-agent.jnlp";
		
	}

}
