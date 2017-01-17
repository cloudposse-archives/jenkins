#!/usr/bin/env bash
[ -d /cloudposse/jenkins/plugins.d/ ] && \
  find /cloudposse/jenkins/plugins.d/ -type f -exec cat {} \; | \
  xargs /usr/local/bin/install-plugins.sh

[ -d /cloudposse/jenkins/jobs.d/ ] && \
  (ls  /cloudposse/jenkins/jobs.d | xargs -I {} mkdir -p /var/jenkins_home/jobs/{})  && \
  (ls  /cloudposse/jenkins/jobs.d | xargs -I {} cp -n /cloudposse/jenkins/jobs.d/{} /var/jenkins_home/jobs/{}/config.xml)

exec /usr/local/bin/jenkins.sh "$*"
