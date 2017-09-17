FROM jenkins/jenkins:lts

ARG BUILD_JENKINS_PLUGINS="\
      git:3.5.1 \
      github-oauth:0.27 \
      matrix-auth \
      "

ADD rootfs /

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.DNSMultiCast.disabled=true -Djava.awt.headless=true"

RUN /usr/local/bin/install-plugins.sh $BUILD_JENKINS_PLUGINS

ENV JENKINS_USER admin
ENV JENKINS_PASS sec007

COPY jenkins_init.groovy /usr/share/jenkins/ref/init.groovy.d/

EXPOSE 8080
