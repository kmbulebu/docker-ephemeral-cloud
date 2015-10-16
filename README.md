# Docker Ephemeral Cloud plugin

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
TODO

## Other Docker cloud slave plugins

### Why not [Docker Plugin](https://wiki.jenkins-ci.org/display/JENKINS/Docker+Plugin)?
The Docker Plugin is a great plugin both for creating a slave cloud, as well as for Docker build tasks. However, the Docker Plugin
did not satisfy all needs in a Docker slave cloud.
- Requires ssh server and port mapping. In some corporate environments, obtaining firewall exceptions for a large range of ports
 to establish SSH connections is difficult. A solution is needed to avoid port mappings.
- It doesn't support TLS and client authentication.
 
### What about the [CloudBees Docker Custom Build Environment Plugin](https://wiki.jenkins-ci.org/display/JENKINS/CloudBees+Docker+Custom+Build+Environment+Plugin)?
The concept of this plugin is excellent, especially when combined with the Docker build and publish plugin. It gives the project admins and builders the flexibility to choose any Docker image as the
build environment for their job. However, today there are some shortcomings.
- It requires a real slave, running the Docker daemon. You will still need to maintain a slave with SCM tools, etc. Targetting a 
 container cloud, such as Docker Swarm, VMware Photon Platform, OpenShift is not possible with this plugin.
- It requires the docker cli client on the slave. Docker is strict
 about requiring a client with a version that matches the server, further complicating slave and tool maintenance. 
- Unless using Workflows, SCM checkouts are performed in the host slave before launching the container. Your SCM is not part of the custom build environment.
- It maintains job and workspace state on Docker host between job runs.
- Odd behaviors when used with other build environment plugins. 
  
 
