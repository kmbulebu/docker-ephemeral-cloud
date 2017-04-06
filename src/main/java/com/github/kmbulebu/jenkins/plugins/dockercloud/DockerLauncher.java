package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.ExecCreateParam;
import com.spotify.docker.client.DockerClient.ExecStartParameter;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.DelegatingComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.JenkinsLocationConfiguration;

public class DockerLauncher extends DelegatingComputerLauncher {
	
	private static final Logger LOGGER = Logger.getLogger(DockerLauncher.class.getName());
	
	private static final String WAIT_FOR_SLAVE_PROPERTY = DockerLauncher.class.getName() + ".waitforslavems";
	
	private static final long WAIT_FOR_SLAVE_PROPERTY_DEFAULT = 60000;
	
	private static final String SLAVE_JAR_DISABLE_SSL_VERIFICATION = " -disableSslVerification";
	
	private String execUser;

	@DataBoundConstructor
	public DockerLauncher(String execUser) {
		super(new JNLPLauncher());
		this.execUser = execUser;
	}
	
	public String getExecUser() {
		return execUser;
	}
	
	@DataBoundSetter
	public void setExecUser(String execUser) {
		this.execUser = execUser;
	}

	@Override
	public void launch(final SlaveComputer computer, final TaskListener listener) throws IOException, InterruptedException {
		final DockerSlave slaveNode = (DockerSlave) computer.getNode();
		
		// Start the JNLP listener.
		super.launch(computer, listener);
		
		DockerClient dockerClient = null;
		LogStream execStartStream = null;
		try {
			dockerClient = slaveNode.getDockerCloud().buildDockerClient();
			final boolean disableSslVerification = System.getProperties().containsKey("docker.launcher.slave.disablesslverification");
			final String slaveOptions = "-jnlpUrl " + getSlaveJnlpUrl(computer) + " -secret " + getSlaveSecret(computer) + (disableSslVerification ? SLAVE_JAR_DISABLE_SSL_VERIFICATION : "");
			final String[] command = new String[] {"sh", "-c", "curl -o slave.jar " + getSlaveJarUrl() + " && java -jar slave.jar " + slaveOptions};
			
			final ExecCreateParam[] params;
			
			if (execUser == null) {
				params = new ExecCreateParam[] {ExecCreateParam.attachStderr(false), ExecCreateParam.attachStdout(false)};
			} else {
				params = new ExecCreateParam[] {ExecCreateParam.user(execUser), ExecCreateParam.attachStderr(false), ExecCreateParam.attachStdout(false)};
			}
			LOGGER.fine("Creating exec for container " + slaveNode.getDockerId() + ".");
			final String execId = dockerClient.execCreate(slaveNode.getDockerId(), command, params);
			listener.getLogger().println("Created Docker exec with id " + execId);
			LOGGER.info("Starting exec for container " + slaveNode.getDockerId() + ".");
			execStartStream = dockerClient.execStart(execId, ExecStartParameter.DETACH);
			LOGGER.fine("Completed exec start for container " + slaveNode.getDockerId() + ".");
			LOGGER.fine("Beginning sleep while waiting for slave in container " + slaveNode.getDockerId() + ".");
			// Give time for slaves to connect. If it's not online and we exit this method, Jenkins will kill it soon.
			Thread.sleep(Long.getLong(WAIT_FOR_SLAVE_PROPERTY, WAIT_FOR_SLAVE_PROPERTY_DEFAULT));
			LOGGER.fine("Finished sleeping while waiting for slave in container " + slaveNode.getDockerId() + ".");
			
			
			// Try and read the output, if possible
			/*try {
				do {
					listener.getLogger().print(execStartStream.readFully());
					Thread.sleep(5000);
				} while (true);
			} catch (RuntimeException e) {
				LOGGER.log(Level.FINE, "Error while streaming output from exec start." + e.getMessage(), e);
			}*/

		} catch (DockerCertificateException e) {
			LOGGER.log(Level.WARNING, "Could not launcher Docker exec on container. There's a problem with the TLS certificates. " + e.getMessage(), e);
		} catch (DockerException e) {
			LOGGER.log(Level.WARNING, "Could not launcher Docker exec on container " + slaveNode.getDockerId() + ". " + e.getMessage(), e);
		} catch (InterruptedException e) {
			LOGGER.fine("Received interrupt. Exiting launcher for container " + slaveNode.getDockerId() + ".");
			throw e;
		}finally {
			if (execStartStream != null) {
				execStartStream.close();
			}
			if (dockerClient != null) {
				dockerClient.close();
			}
		}
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
            return "Docker Container Launcher";
        }
    };
    
	
}




