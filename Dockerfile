# https://hub.docker.com/r/jenkins/jenkins/tags/
FROM jenkins/jenkins:2.125

USER root

RUN apt-get update && apt-get install -y bash git wget openssh-server vim gettext make docker awscli ruby python-pip htop libssl-dev libreadline-dev zlib1g-dev ffmpeg
RUN apt-get install -y supervisor
RUN apt-get install -y python3

# Install pip
ADD requirements.txt /root/requirements.txt
RUN pip install -r /root/requirements.txt

# Install m2a-git-mirror
RUN virtualenv /opt/m2a-git-mirror/ -p python3.5
RUN /opt/m2a-git-mirror/bin/pip \
    install git+https://bitbucket.org/m2amedia/m2a-git-mirror.git
RUN ln -s /opt/m2a-git-mirror/bin/m2a-git-mirror /usr/bin

# Install service configurations
COPY supervisor/ /etc/supervisor/conf.d/

# Download terraform binary
ENV TERRAFORM_VERSION=0.11.7
RUN cd /tmp && \
    wget https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_linux_amd64.zip && \
    unzip terraform_${TERRAFORM_VERSION}_linux_amd64.zip -d /usr/bin && \
    rm -rf /tmp/* && \
    rm -rf /var/cache/apk/* && \
    rm -rf /var/tmp/*
RUN terraform -v

# Download packer binary
ENV PACKER_VERSION=1.2.4
RUN cd /tmp && \
    wget https://releases.hashicorp.com/packer/${PACKER_VERSION}/packer_${PACKER_VERSION}_linux_amd64.zip && \
    unzip packer_${PACKER_VERSION}_linux_amd64.zip -d /usr/bin && \
    rm -rf /tmp/* && \
    rm -rf /var/cache/apk/* && \
    rm -rf /var/tmp/*
RUN packer -v

# Initialise and configure Git mirrors
ENV GIT_HOME /var/git/
ARG git_user=git
ARG git_group=git
ARG git_uid=1001
ARG git_gid=1001
RUN mkdir -p $GIT_HOME \
  && mkdir $GIT_HOME/.ssh/ \
  && chown -R ${git_uid}:${git_gid} $GIT_HOME \
  && groupadd -g ${git_gid} ${git_group} \
  && useradd \
        -m -d "$GIT_HOME" \
        -u ${git_uid} \
        -g ${git_gid} \
        -s /bin/bash ${git_user}
VOLUME $GIT_HOME/.ssh/
USER git
RUN m2a-git-mirror initialise
RUN m2a-git-mirror add git@bitbucket.org:m2amedia/m2a-packer.git
USER root

# Allow the jenkins user to run docker
RUN groupadd docker
RUN usermod -aG docker jenkins

# Scripts
ADD scripts /usr/share/jenkins/scripts
RUN chown -R jenkins:jenkins /usr/share/jenkins/scripts
RUN chmod +x /usr/share/jenkins/scripts
RUN chmod +x /usr/share/jenkins/scripts/*
ENV PATH="/usr/share/jenkins/scripts:${PATH}"

# Drop back to the regular jenkins user
USER jenkins

# 1. Disable Jenkins setup Wizard UI. The initial user and password will be supplied by Terraform via ENV vars during infrastructure creation
# 2. Set Java DNS TTL to 60 seconds
# http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
# http://docs.oracle.com/javase/7/docs/technotes/guides/net/properties.html
# https://aws.amazon.com/articles/4035
# https://stackoverflow.com/questions/29579589/whats-the-recommended-way-to-set-networkaddress-cache-ttl-in-elastic-beanstalk
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.DNSMultiCast.disabled=true -Djava.awt.headless=true -Dsun.net.inetaddr.ttl=60 -Dorg.jenkinsci.plugins.gitclient.Git.timeOut=60"

# Preinstall plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt

# Setup Jenkins initial admin user, security mode (Matrix), and the number of job executors
# Many other Jenkins configurations could be done from the Groovy script
COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/

# Configure `Amazon EC2` plugin to start slaves on demand
COPY init-ec2.groovy /usr/share/jenkins/ref/init.groovy.d/

EXPOSE 8080

# Use Supervisor to run Jenkins and other services. Supervisor will
# handle de-escalating service permissions.
USER root
ENTRYPOINT ["/sbin/tini", "--", "/usr/bin/supervisord", "-n"]
