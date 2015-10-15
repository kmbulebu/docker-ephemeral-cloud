package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.docker.commons.credentials.DockerServerEndpoint;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificateException;
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
    
    private DockerServerEndpoint server;
    
    private transient DockerClient dockerClient;
    
  //  private List<DockerImage> images;

    @DataBoundConstructor
    public DockerCloud(String name, String instanceCapStr) {
        super(name, instanceCapStr);
        this.server = new DockerServerEndpoint(null, null);
    }
    
   /* public DockerCloud(String name, String instanceCapStr, List<? extends DockerImage> images) {
        super(name, instanceCapStr);
        this.server = new DockerServerEndpoint(null, null);
        
        if (images == null) {
            this.images = Collections.emptyList();
        } else {
            this.images = new ArrayList<DockerImage>(images);
        }

    }*/

	@Override
	public Collection<PlannedNode> provision(Label label, int excessWorkload) {
		try {
			// TODO ?? Count containers in progress but not yet read, subtract from excessWorkload.

			// TODO Identify which configuration supports the specified label.

            List<NodeProvisioner.PlannedNode> plannedNodes = new ArrayList<NodeProvisioner.PlannedNode>();

            for (int i = 1; i <= excessWorkload; i++) {
    		
	            LOGGER.warning("Call to create node");
	            CreateContainerCallable containerCallable = new CreateContainerCallable("/", Mode.NORMAL, label == null ? "" : label.toString(), this, "java:8");
	            	plannedNodes.add(new NodeProvisioner.PlannedNode(name, Computer.threadPoolForRemoting
	                        .submit(containerCallable), 1)); 
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
				final URI dockerUri = URI.create("https://sparky:2376/");
				DockerCertificates dockerCerts = new DockerCertificates(Paths.get("/Users/kmbulebu/.docker/"));
				dockerClient = new DefaultDockerClient(dockerUri, dockerCerts);
			}
			return dockerClient;
		} catch (DockerCertificateException e) {
			// TODO Build certificates somewhere else.
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
	
	@DataBoundSetter
	@Override
	protected void setInstanceCapStr(String value) {
		super.setInstanceCapStr(value);
	}
	
	public DockerServerEndpoint getServer() {
        return server;
    }
	
	@DataBoundSetter
    public void setServer(DockerServerEndpoint server) {
        this.server = server;
    }
	
	/*public List<DockerImage> getImages() {
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
	}*/
	
	
	
    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
    	
    	public DescriptorImpl() {
    		super(DockerCloud.class);
    		LOGGER.warning("Descriptor constructor");
		}
    	
        @Override
        public String getDisplayName() {
            return "Docker Cloud";
        }
        
        public FormValidation doTestConnection(@QueryParameter String instanceCapStr) {
        	return FormValidation.ok("Okay");
        }
        

    }
    
   /* protected static DockerClient buildDockerClient(String uri) {
    	final URI dockerUri = URI.create(uri);
		DockerCertificates dockerCerts = new DockerCertificates(Paths.get("/Users/kmbulebu/.docker/"));
		dockerClient = new DefaultDockerClient(dockerUri, dockerCerts);
    }*/


}
