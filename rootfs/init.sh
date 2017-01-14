#!/usr/bin/env bash
find /cloudposse/jenkins/plugins.d/ -type f -exec /usr/local/bin/install-plugins.sh {} \;

exec /usr/local/bin/jenkins.sh
