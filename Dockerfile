# https://hub.docker.com/r/jenkins/jenkins/tags/
FROM jenkins/jenkins:2.104-alpine

# set maintainer
LABEL maintainer "@cloudposse"

# change user
USER root

# install required packages
RUN readonly PACKAGES=" \
      bash=4.4.12-r2 \
      docker=17.10.0-r0 \
      gettext=0.19.8.1-r1 \
      git=2.15.0-r1 \
      make=4.2.1-r0 \
      openssh=7.5_p1-r8 \
      openssl=1.0.2n-r0 \
    " \
    && apk update \
    && apk upgrade \
    && apk add --no-cache ${PACKAGES}

# Allow the jenkins user to run docker
RUN adduser jenkins docker

# generate a self-signed certificate and configure HTTPS
ARG JENKINS_URL="jenkins.local"
ARG COMPANY_NAME="Cloud Posse, LLC"
ARG COUNTRY_CODE="US"

ENV JENKINS_URL=${JENKINS_URL} \
    COMPANY_NAME=${COMPANY_NAME} \
    COUNTRY_CODE=${COUNTRY_CODE}

RUN mkdir --parents /var/lib/jenkins \
    && openssl genrsa -out /var/lib/jenkins/key.pem \
    && openssl req -new \
                   -subj "/CN=${JENKINS_URL}/O=${COMPANY_NAME}/C=${COUNTRY_CODE}" \
                   -key /var/lib/jenkins/key.pem \
                   -out /var/lib/jenkins/csr.pem \
    && openssl x509 -req \
                    -days 365 \
                    -in /var/lib/jenkins/csr.pem \
                    -signkey /var/lib/jenkins/key.pem \
                    -out /var/lib/jenkins/cert.pem \
    && chown jenkins:jenkins /var/lib/jenkins/*.pem

ENV JENKINS_OPTS --httpPort=8080 \
                 --httpsPort=8083 \
                 --httpsCertificate=/var/lib/jenkins/cert.pem \
                 --httpsPrivateKey=/var/lib/jenkins/key.pem


# Drop back to the regular jenkins user
USER jenkins

# 1. Disable Jenkins setup Wizard UI. The initial user and password will be supplied by Terraform via ENV vars during infrastructure creation
# 2. Set Java DNS TTL to 60 seconds
# http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
# http://docs.oracle.com/javase/7/docs/technotes/guides/net/properties.html
# https://aws.amazon.com/articles/4035
# https://stackoverflow.com/questions/29579589/whats-the-recommended-way-to-set-networkaddress-cache-ttl-in-elastic-beanstalk
ARG TIME_ZONE="PST"
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false \
               -Dhudson.DNSMultiCast.disabled=true \
               -Djava.awt.headless=true \
               -Dsun.net.inetaddr.ttl=60 \
               -Duser.timezone=${TIME_ZONE} \
               -Dorg.jenkinsci.plugins.gitclient.Git.timeOut=60"

# Preinstall plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt

# Setup Jenkins initial admin user, security mode (Matrix), and the number of job executors
# Many other Jenkins configurations could be done from the Groovy script
COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/

# Configure `Amazon EC2` plugin to start slaves on demand
COPY init-ec2.groovy /usr/share/jenkins/ref/init.groovy.d/

# HTTP 8080 - HTTPS 8083
EXPOSE 8080 8083
