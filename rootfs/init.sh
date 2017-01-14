#!/usr/bin/env bash
[ -d /cloudposse/jenkins/plugins.d/ ] && \
  find /cloudposse/jenkins/plugins.d/ -type f -exec cat {} \; | \
  xargs /usr/local/bin/install-plugins.sh

exec /usr/local/bin/jenkins.sh "$*"
