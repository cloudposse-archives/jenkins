FROM jenkins/jenkins:lts

#USER root

ARG BUILD_JENKINS_PLUGINS="\
      git:3.5.1 \
      github-oauth:0.27 \
      "
ADD rootfs /

RUN /usr/local/bin/install-plugins.sh $BUILD_JENKINS_PLUGINS

EXPOSE 8080

#ENTRYPOINT ["/bin/tini", "--", "/init.sh"]
