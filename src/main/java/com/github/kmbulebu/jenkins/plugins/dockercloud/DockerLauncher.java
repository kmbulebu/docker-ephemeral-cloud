package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.spotify.docker.client.DockerCertificateException;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ExecCreateParam;
import com.spotify.docker.client.DockerClient.ExecStartParameter;
import com.spotify.docker.client.DockerException;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.JenkinsLocationConfiguration;

public class DockerLauncher extends ComputerLauncher {
	
	private static final Logger LOGGER = Logger.getLogger(DockerLauncher.class.getName());
	
	private final ComputerLauncher childLauncher;
	
	private String execUser;

	@DataBoundConstructor
	public DockerLauncher(String execUser) {
		super();
		this.childLauncher = new JNLPLauncher();
		this.execUser = execUser;
	}

	@Override
	public void afterDisconnect(SlaveComputer computer, TaskListener listener) {
		childLauncher.afterDisconnect(computer, listener);
	}

	@Override
	public void beforeDisconnect(SlaveComputer computer, TaskListener listener) {
		childLauncher.beforeDisconnect(computer, listener);
	}

	@Override
	public boolean isLaunchSupported() {
		return childLauncher.isLaunchSupported();
	}
	
	public String getExecUser() {
		return execUser;
	}
	
	@DataBoundSetter
	public void setExecUser(String execUser) {
		this.execUser = execUser;
	}

	@Override
	public void launch(SlaveComputer computer, TaskListener listener) throws IOException, InterruptedException {
		final DockerSlave slaveNode = (DockerSlave) computer.getNode();
		
		DockerClient dockerClient = null;
		
		try {
			dockerClient = slaveNode.getDockerCloud().buildDockerClient();
			final String additionalSlaveOptions = "-noReconnect";
			final String slaveOptions = "-jnlpUrl " + getSlaveJnlpUrl(computer) + " -secret " + getSlaveSecret(computer) + " " + additionalSlaveOptions;
			final String[] command = new String[] {"sh", "-c", "curl -o slave.jar " + getSlaveJarUrl() + " && java -jar slave.jar " + slaveOptions};
			
			final ExecCreateParam[] params;
			
			if (execUser == null) {
				params = new ExecCreateParam[] {};
			} else {
				params = new ExecCreateParam[] {ExecCreateParam.user(execUser)};
			}
			LOGGER.info("Building command exec for container.");
			final String execId = dockerClient.execCreate(slaveNode.getDockerId(), command, params);
			LOGGER.info("Starting command exec for container.");
			dockerClient.execStart(execId, ExecStartParameter.DETACH);
		} catch (DockerCertificateException e) {
			LOGGER.log(Level.WARNING, "Could not launcher Docker exec on container. There's a problem with the TLS certificates. " + e.getMessage(), e);
		} catch (DockerException e) {
			LOGGER.log(Level.WARNING, "Could not launcher Docker exec on container. " + e.getMessage(), e);
		} finally {
			if (dockerClient != null) {
				dockerClient.close();
			}
		}
			// TODO Auto-generated method stub
		childLauncher.launch(computer, listener);
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
	private String getSlaveJnlpUrl(SlaveComputer slaveComputer) {
		return getJenkinsBaseUrl() + slaveComputer.getUrl() + "slave-agent.jnlp";
		
	}
	
	private String getSlaveSecret(SlaveComputer slaveComputer) {
		return slaveComputer.getJnlpMac();
		
	}
	
	@Extension
    public static final Descriptor<ComputerLauncher> DESCRIPTOR = new Descriptor<ComputerLauncher>() {
        public String getDisplayName() {
            return "Docker Ephemeral Cloud Launcher";
        }
    };
    
	
}




