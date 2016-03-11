package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.logging.Logger;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;

@Extension(optional=false)
public class HurryUpRunListener extends RunListener<Run<?,?>> {

	private static final Logger LOGGER = Logger.getLogger(HurryUpRunListener.class.getName());
	
	@Override
	public void onStarted(Run<?,?> r, TaskListener listener) {
		LOGGER.info("Job run onStart detected. Suggesting nodeProvisioner evaluate for new slave node.");
		Jenkins.getInstance().unlabeledNodeProvisioner.suggestReviewNow();
		super.onStarted(r, listener);
	}
	
}
