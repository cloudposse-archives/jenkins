#!groovy

import hudson.security.*
import jenkins.model.*
import jenkins.security.s2m.AdminWhitelistRule
import org.jenkinsci.plugins.*

def isValidString = { value ->
    if (value != null && value instanceof String && value.trim() != "") {
        return true
    }
    return false
}

def env = System.getenv()
def jenkins = Jenkins.getInstance()
jenkins.getInjector().getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)

// Configure Github Authentication Security Realm and GitHub Committer Authorization Strategy
def configureGitHubAuthorizationStrategy = { clientId, clientSecret, admins, orgNames, oauthScopes ->
    if (!isValidString(clientId)) {
        throw new Throwable("'JENKINS_GITHUB_CLIENT_ID' is required for GitHub Authorization Strategy")
    }
    if (!isValidString(clientSecret)) {
        throw new Throwable("'JENKINS_GITHUB_CLIENT_SECRET' is required for GitHub Authorization Strategy")
    }
    if (!isValidString(admins)) {
        throw new Throwable("'JENKINS_GITHUB_ADMINS' is required for GitHub Authorization Strategy")
    }
    if (!isValidString(orgNames)) {
        throw new Throwable("'JENKINS_GITHUB_ORG_NAMES' is required for GitHub Authorization Strategy")
    }
    if (!isValidString(oauthScopes)) {
        throw new Throwable("'JENKINS_GITHUB_OAUTH_SCOPES' is required for GitHub Authorization Strategy")
    }

    def githubSecurityRealm = new GithubSecurityRealm(
            "https://github.com",
            "https://api.github.com",
            clientId,
            clientSecret,
            oauthScopes
    )

    // https://github.com/jenkinsci/github-oauth-plugin/blob/master/src/main/java/org/jenkinsci/plugins/GithubAuthorizationStrategy.java
    def githubAuthorizationStrategy = new GithubAuthorizationStrategy(
            admins,    /*adminUserNames*/
            false,     /*authenticatedUserReadPermission*/
            true,      /*useRepositoryPermissions*/
            false,     /*authenticatedUserCreateJobPermission*/
            orgNames,  /*organizationNames*/
            true,      /*allowGithubWebHookPermission*/
            false,     /*allowCcTrayPermission*/
            false)     /*allowAnonymousReadPermission*/

    jenkins.setSecurityRealm(githubSecurityRealm)
    jenkins.setAuthorizationStrategy(githubAuthorizationStrategy)
    jenkins.save()
}

// Configure Matrix-based Security
def configureMatrixAuthorizationStrategy = { jenkinsUser, jenkinsPassword ->
    if (!isValidString(jenkinsUser)) {
        throw new Throwable("'JENKINS_USER' is required to create the initial admin user")
    }
    if (!isValidString(jenkinsPassword)) {
        throw new Throwable("'JENKINS_PASS' is required to create the initial admin user")
    }
    jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false))
    jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy())
    def user = jenkins.getSecurityRealm().createAccount(jenkinsUser, jenkinsPassword)
    user.save()
    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, jenkinsUser)
    jenkins.save()
}

// Configure Authorization Strategy ('GitHub' or 'Matrix')
def jenkinsAuthorizationStrategy = env.JENKINS_AUTHORIZATION_STRATEGY ?: 'Matrix'
if (jenkinsAuthorizationStrategy == "GitHub") {
    configureGitHubAuthorizationStrategy(
            env.JENKINS_GITHUB_CLIENT_ID,
            env.JENKINS_GITHUB_CLIENT_SECRET,
            env.JENKINS_GITHUB_ADMINS,
            env.JENKINS_GITHUB_ORG_NAMES,
            env.JENKINS_GITHUB_OAUTH_SCOPES
    )
} else if (jenkinsAuthorizationStrategy == "Matrix") {
    configureMatrixAuthorizationStrategy(
            env.JENKINS_USER,
            env.JENKINS_PASS
    )
} else {
    throw new Throwable("Invalid 'JENKINS_AUTHORIZATION_STRATEGY'")
}

// Set number of job executors
int num_executors = 4
if (isValidString(env.JENKINS_NUM_EXECUTORS)) {
    num_executors = env.JENKINS_NUM_EXECUTORS.toInteger()
}
jenkins.setNumExecutors(num_executors)
