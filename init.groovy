#!groovy

import hudson.security.*
import hudson.security.csrf.*
import jenkins.model.*
import jenkins.security.s2m.AdminWhitelistRule
import org.jenkinsci.plugins.*
import org.jenkinsci.plugins.saml.*


def isValidString = { value ->
    if (value != null && value instanceof String && value.trim() != "") {
        return true
    }
    return false
}

def env = System.getenv()
def jenkins = Jenkins.getInstance()
jenkins.getInjector().getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)

def configureGitHubMatrixAuthorizationStrategy = { clientId, clientSecret, admins, orgNames, oauthScopes ->
    if (!isValidString(clientId)) {
        throw new Throwable("'JENKINS_GITHUB_CLIENT_ID' is required for GitHub Matrix Authorization Strategy")
    }
    if (!isValidString(clientSecret)) {
        throw new Throwable("'JENKINS_GITHUB_CLIENT_SECRET' is required for GitHub Matrix Authorization Strategy")
    }
    if (!isValidString(admins)) {
        throw new Throwable("'JENKINS_GITHUB_ADMINS' is required for GitHub Matrix Authorization Strategy")
    }
    if (!isValidString(orgNames)) {
        throw new Throwable("'JENKINS_GITHUB_ORG_NAMES' is required for GitHub Matrix Authorization Strategy")
    }
    if (!isValidString(oauthScopes)) {
        throw new Throwable("'JENKINS_GITHUB_OAUTH_SCOPES' is required for GitHub Matrix Authorization Strategy")
    }

    // https://github.com/mocleiri/github-oauth-plugin/blob/master/src/main/java/org/jenkinsci/plugins/GithubSecurityRealm.java
    def githubSecurityRealm = new GithubSecurityRealm(
            "https://github.com",
            "https://api.github.com",
            clientId,
            clientSecret,
            oauthScopes
    )

    jenkins.setSecurityRealm(githubSecurityRealm)
    jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy())
    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, admins)
    jenkins.save()
}



configureGitHubMatrixAuthorizationStrategy(
            env.JENKINS_GITHUB_CLIENT_ID,
            env.JENKINS_GITHUB_CLIENT_SECRET,
            env.JENKINS_GITHUB_ADMINS,
            env.JENKINS_GITHUB_ORG_NAMES,
            env.JENKINS_GITHUB_OAUTH_SCOPES
        )

jenkins.setNumExecutors(0)

// Enable CSRF protection
jenkins.setCrumbIssuer(new DefaultCrumbIssuer(true))

// Disable CLI over the remoting protocol for security
jenkins.getDescriptor("jenkins.CLI").get().enabled = false

// Disable old/unsafe agent protocols for security
jenkins.agentProtocols = ["JNLP4-connect", "Ping"] as Set

// disabled CLI access over TCP listener (separate port)
def p = jenkins.AgentProtocol.all()
p.each { x ->
    if (x.name?.contains("CLI")) {
        println "Removing protocol ${x.name}"
        p.remove(x)
    }
}

// disable CLI access over /cli URL
def removal = { lst ->
    lst.each { x ->
        if (x.getClass().name.contains("CLIAction")) {
            println "Removing extension ${x.getClass().name}"
            lst.remove(x)
        }
    }
}

removal(jenkins.getExtensionList(hudson.model.RootAction.class))
removal(jenkins.actions)

jenkins.save()
