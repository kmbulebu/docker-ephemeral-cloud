package com.github.kmbulebu.jenkins.plugins.dockercloud;

import java.util.concurrent.Callable;

import com.spotify.docker.client.DockerClient;

public abstract class DockerClientCallable<V> implements Callable<V> {
	
	private final DockerClient dockerClient;
	
	public DockerClientCallable(DockerClient dockerClient) {
		this.dockerClient = dockerClient;
	}

	@Override
	public V call() throws Exception {
		try {
			return callWithDocker(dockerClient);
		} finally {
			if (dockerClient != null) {
				dockerClient.close();
			}
		}
	}
	
	public abstract V callWithDocker(final DockerClient dockerClient) throws Exception;

}
