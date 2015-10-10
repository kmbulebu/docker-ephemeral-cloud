package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.Collection;
import java.util.Collections;
import java.util.logging.Logger;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.util.FormValidation;

/**
 * Docker cloud provider.
 * 
 * @author Kevin Bulebush (kmbulebu@gmail.com)
 */
public class DockerCloud extends AbstractCloudImpl {

    private static final Logger LOGGER = Logger.getLogger(DockerCloud.class.getName());

    @DataBoundConstructor
    public DockerCloud(String name, String instanceCapStr) {
        super(name, instanceCapStr);

    }

	@Override
	public Collection<PlannedNode> provision(Label label, int excessWorkload) {
		// TODO Auto-generated method stub
		return Collections.emptyList();
	}


	@Override
	public boolean canProvision(Label label) {
		// TODO Auto-generated method stub
		return true;
	}
	
	@DataBoundSetter
	@Override
	protected void setInstanceCapStr(String value) {
		super.setInstanceCapStr(value);
	}
	

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
    	
    	public DescriptorImpl() {
    		super(DockerCloud.class);
		}
    	
    	
        @Override
        public String getDisplayName() {
            return "Docker Cloud";
        }
        
        public FormValidation doTestConnection(@QueryParameter String instanceCapStr) {
        	return FormValidation.ok("Okay");
        }
        

    }


}
