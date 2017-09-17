FROM jenkins/jenkins:lts

#USER root

ARG BUILD_JENKINS_PLUGINS="\
      git:3.5.1 \
      github-oauth:0.27 \
      "

ADD rootfs /

RUN /usr/local/bin/install-plugins.sh $BUILD_JENKINS_PLUGINS

EXPOSE 8080

ENV JVM_OPTS="-Dhudson.udp=-1                                  \
               -Djava.awt.headless=true                        \
               -Dhudson.DNSMultiCast.disabled=true             \
               -Djenkins.install.runSetupWizard=false          \
               "

ENV JENKINS_OPTS="--argumentsRealm.passwd.admin=admin          \
                   --argumentsRealm.roles.user=admin           \
                   --argumentsRealm.roles.admin=admin          \
                   "

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.DNSMultiCast.disabled=true -Djava.awt.headless=true --argumentsRealm.passwd.admin=admin --argumentsRealm.roles.user=admin --argumentsRealm.roles.admin=admin"

#ENTRYPOINT ["/bin/tini", "--", "/init.sh"]
