# https://hub.docker.com/r/jenkins/jenkins/tags/
FROM jenkins/jenkins:2.82-alpine

ARG BUILD_JENKINS_PLUGINS="\
      git:3.5.1 \
      github-oauth:0.27 \
      matrix-auth:1.7 \
      workflow-aggregator:2.5 \
      docker-workflow:1.13 \
      credentials-binding:1.13 \
      kubernetes:1.0 \
      build-token-root:1.4 \
      envinject:2.1.3 \
      text-file-operations:1.3.2 \
      pipeline-githubnotify-step:1.0.3 \
      blueocean:1.2.4 \
      blueocean-github-pipeline:1.2.4 \
      blueocean-display-url:2.1.0 \
      slack:2.3 \
      greenballs:1.15 \
      "

# http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
# http://docs.oracle.com/javase/7/docs/technotes/guides/net/properties.html
# https://aws.amazon.com/articles/4035
# https://stackoverflow.com/questions/29579589/whats-the-recommended-way-to-set-networkaddress-cache-ttl-in-elastic-beanstalk

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.DNSMultiCast.disabled=true -Djava.awt.headless=true -Dsun.net.inetaddr.ttl=60 -Duser.timezone=PST -Dorg.jenkinsci.plugins.gitclient.Git.timeOut=60"

RUN /usr/local/bin/install-plugins.sh $BUILD_JENKINS_PLUGINS

COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/

EXPOSE 8080
