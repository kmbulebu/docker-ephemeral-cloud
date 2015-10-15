package com.github.kmbulebu.jenkins.plugins.dockercloud;

import hudson.slaves.AbstractCloudComputer;

public class DockerComputer extends AbstractCloudComputer<DockerSlave> {

	public DockerComputer(DockerSlave slave) {
		super(slave);
	}
	
	

}
