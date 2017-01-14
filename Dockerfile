FROM jenkins:2.32.1-alpine

ARG BUILD_JENKINS_PLUGINS="\
      kubernetes:0.10 \
      kubernetes-ci:1.2 \
      workflow-aggregator:2.4 \
      credentials-binding:1.10 \
      blueocean:1.0.0-b17 \
      git:3.0.1 \
      github-oauth:0.25 \
      pipeline-githubnotify-step:1.0.1 \
      blueocean-github-pipeline:1.0.0-b17 \
      blueocean-display-url:1.3 \
      slack:2.1 \
      greenballs:1.15 \
      "
#ADD rootfs /

RUN /usr/local/bin/install-plugins.sh $BUILD_JENKINS_PLUGINS

#ENTRYPOINT ["/bin/tini", "--", "/init.sh"]
