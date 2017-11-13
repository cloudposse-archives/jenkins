#!groovy

import hudson.security.*
import jenkins.model.*
import jenkins.security.s2m.AdminWhitelistRule

def env = System.getenv()

if (!env.JENKINS_USER) {
    throw new Throwable("`JENKINS_USER' ENV variable is required to create the initial admin user")
}

if (!env.JENKINS_PASS) {
    throw new Throwable("`JENKINS_PASS' ENV variable required to create the initial admin user")
}

int num_executors = 4
if (env.JENKINS_NUM_EXECUTORS) {
    num_executors = env.JENKINS_NUM_EXECUTORS.toInteger()
}

def jenkins = Jenkins.getInstance()
jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false))
jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy())

def user = jenkins.getSecurityRealm().createAccount(env.JENKINS_USER, env.JENKINS_PASS)
user.save()

jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, env.JENKINS_USER)
jenkins.save()

Jenkins.instance.getInjector().getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)
Jenkins.instance.setNumExecutors(num_executors)
