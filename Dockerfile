# https://hub.docker.com/r/jenkins/jenkins/tags/
FROM jenkins/jenkins:2.82-alpine

# http://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-jvm-ttl.html
# http://docs.oracle.com/javase/7/docs/technotes/guides/net/properties.html
# https://aws.amazon.com/articles/4035
# https://stackoverflow.com/questions/29579589/whats-the-recommended-way-to-set-networkaddress-cache-ttl-in-elastic-beanstalk
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.DNSMultiCast.disabled=true -Djava.awt.headless=true -Dsun.net.inetaddr.ttl=60 -Duser.timezone=PST -Dorg.jenkinsci.plugins.gitclient.Git.timeOut=60"

# Install plugins
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt

# Setup Jenkins initial admin user, security mode (Matrix), and the number of job executors
# Many other Jenkins configurations could be done using Groovy API
COPY init.groovy /usr/share/jenkins/ref/init.groovy.d/

EXPOSE 8080
