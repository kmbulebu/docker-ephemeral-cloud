# Docker Ephemeral Cloud plugin
[![Build Status](https://drone.io/github.com/kmbulebu/docker-ephemeral-cloud/status.png)](https://drone.io/github.com/kmbulebu/docker-ephemeral-cloud/latest)

## Introduction
Ensuring Jenkins has a reliable, scalable, and flexible job slaves is a challenge. The Docker Ephemeral Cloud plugin provides
an elastic pool of slaves, using Docker containers, that live only as long as the job. 

## Motivations
- Elastic slaves. An executor pool that scales to demand.
- Perfect isolation. Builds are always executed in a clean environment, isolated from others.
- Low maintenance. No always-on slaves to maintain.
- Easy build environments. Environments packaged and shared as Docker images.
- Focus on builds and stateless jobs.

## How it works
When a job is scheduled, Jenkins checks for Docker image configurations that match the labels of the job. When one is found,
Docker is invoked via REST APIs to pull the image and run the container. Within the container, the Jenkins JNLP slave jar is
downloaded and run. Communication is established to the Jenkins master and the new slave node performs the requested job. When
complete, the container is stopped and removed.

### Features
- Use Docker containers as Jenkins slaves.
- No open ports required. Containers 'phone home' via Jenkins JNLP slave connections. 
- Supports multiple Docker images using slave label mappings.
- Connect via UNIX socket or remotely over REST. TLS supported.
- Completely stateless. One executor, one job run per container life.

## Using

### Prerequisites
 
#### Jenkins prerequisites
- Recommend Jenkins 1.609.3 or newer.
- Jenkins URL is correctly set in global configuration.
- JNLP slave ports are enabled and open.
 
#### Docker prerequisites
- Recommend Docker 1.7+
- Docker is configured and able to access the registries hosting the images.
- Recommend using TCP + TLS for secure, remote communication.
- Containers can reach the Jenkins master via the JNLP port. 
 
#### Image prerequisites
- Contains a Java install compatible with Jenkins with java on the path. Used for invoking Jenkins slave jar.
- Contains curl, on the path. Used for downloading the slave jar

### Configuring

1. Add a Docker Ephemeral Cloud configuration in your Jenkins global configuration. 
![Cloud Configuration] (cloud_config.png)

2. Add a Docker image to use as a base for slave containers. 
![Image Configuration] (image_basic_config.png)

3 Run a job. You will see slaves come move through various states as they create, run, and destroy.
![Build executors] (build_executors.png)

### Known issues and limitations

- None documented at this time.

Docker Ephemeral Cloud plugin is built using Spotify's [docker-client](https://github.com/spotify/docker-client) library. Please see their [issues page](https://github.com/spotify/docker-client/issues) for possible limitations and known issues.

## Comparative to other Docker plugins

#### [Docker Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Docker+Plugin)
- Slaves containers require image with ssh server and accompanying port mappings.
- No support for TLS and client authentication.
- Provides additional capabilities beyond slaves, such as Docker build steps. 
 
#### [CloudBees Docker Custom Build Environment Plugin](https://wiki.jenkins-ci.org/display/JENKINS/CloudBees+Docker+Custom+Build+Environment+Plugin)
- Requires a regular slave, running the Docker daemon and client.
- Allows for Docker image selection within Job configuration.
- No support for remote container clouds such as Docker Swarm, VMware Photon Platform, OpenShift, etc. 
- SCM build steps performed on Docker host and directories bind mounted to containers.
- Job and workspace state persisted on Docker host between job runs.
- May limit use of other build environment and wrapper plugins. 
- Jenkins Workflows Support 
  
#### Thanks

Special thanks to Spotify and other contributors to docker-client. The [docker-client](https://github.com/spotify/docker-client) library is clean and intuitive, making this project a pleasure to write. 

