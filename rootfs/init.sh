#!/usr/bin/env bash
ls -l /cloudposse/jenkins/plugins.d | xargs cat | xargs /usr/local/bin/install-plugins.sh

/usr/local/bin/jenkins.sh