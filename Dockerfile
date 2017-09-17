FROM jenkins/jenkins:lts

ARG BUILD_JENKINS_PLUGINS="\
      git \
      github-oauth \
      matrix-auth \
      workflow-aggregator \
      docker-workflow \
      blueocean \
      credentials-binding \
      "

ADD rootfs /

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.DNSMultiCast.disabled=true -Djava.awt.headless=true"

RUN /usr/local/bin/install-plugins.sh $BUILD_JENKINS_PLUGINS

ENV JENKINS_USER admin
ENV JENKINS_PASS sec007

COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/

EXPOSE 8080
