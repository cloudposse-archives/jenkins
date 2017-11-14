#!groovy

import hudson.security.*
import jenkins.model.*
import jenkins.security.s2m.AdminWhitelistRule

def isValidString = { value ->
    if (value != null && value instanceof String && value.trim() != "") {
        return true
    }
    return false
}

def env = System.getenv()
def jenkins = Jenkins.getInstance()
jenkins.getInjector().getInstance(AdminWhitelistRule.class).setMasterKillSwitch(false)

// Set number of job executors
int num_executors = 4
if (env.JENKINS_NUM_EXECUTORS) {
    num_executors = env.JENKINS_NUM_EXECUTORS.toInteger()
}
jenkins.setNumExecutors(num_executors)


def setupSecurity = { clientId, clientSecret, admins, orgNames, oauthScopes, jenkinsUser, jenkinsPassword ->
    // Try to configure Github Authentication Security Realm and GitHub Committer Authorization Strategy
    if (isValidString(clientId) && isValidString(clientSecret) && isValidString(admins) && isValidString(orgNames) && isValidString(oauthScopes)) {
        def githubSecurityRealm = new org.jenkinsci.plugins.GithubSecurityRealm(
                "https://github.com",
                "https://api.github.com",
                clientId,
                clientSecret,
                oauthScopes
        )

        /**
         * GithubAuthorizationStrategy constructor params
         * source: https://github.com/jenkinsci/github-oauth-plugin/blob/master/src/main/java/org/jenkinsci/plugins/GithubAuthorizationStrategy.java
         * ==============================================
         * String adminUserNames,
         * boolean authenticatedUserReadPermission,
         * boolean useRepositoryPermissions,
         * boolean authenticatedUserCreateJobPermission,
         * String organizationNames,
         * boolean allowGithubWebHookPermission,
         * boolean allowCcTrayPermission,
         * boolean allowAnonymousReadPermission
         * ==============================================
         * Please see source for latest code if this throws errors
         **/
        def authorizationStrategy = new org.jenkinsci.plugins.GithubAuthorizationStrategy(
                admins,    /*adminUserNames*/
                true,      /*authenticatedUserReadPermission*/
                true,      /*useRepositoryPermissions*/
                true,      /*authenticatedUserCreateJobPermission*/
                orgNames,  /*organizationNames*/
                true,      /*allowGithubWebHookPermission*/
                false,     /*allowCcTrayPermission*/
                false)     /*allowAnonymousReadPermission*/

        jenkins.setSecurityRealm(githubSecurityRealm)
        jenkins.setAuthorizationStrategy(authorizationStrategy)
        jenkins.save()
    } else {
        // If any of Github Authentication settings are not provided, try to configure Matrix-based Security Realm
        if (!isValidString(jenkinsUser)) {
            throw new Throwable("`JENKINS_USER' ENV variable is required to create the initial admin user")
        }
        if (!isValidString(jenkinsPassword)) {
            throw new Throwable("`JENKINS_PASS' ENV variable required to create the initial admin user")
        }
        jenkins.setSecurityRealm(new HudsonPrivateSecurityRealm(false))
        jenkins.setAuthorizationStrategy(new GlobalMatrixAuthorizationStrategy())
        def user = jenkins.getSecurityRealm().createAccount(jenkinsUser, jenkinsPassword)
        user.save()
        jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, jenkinsUser)
        jenkins.save()
    }
}

setupSecurity(
        env.JENKINS_GITHUB_CLIENT_ID,
        env.JENKINS_GITHUB_CLIENT_SECRET,
        env.JENKINS_GITHUB_ADMINS,
        env.JENKINS_GITHUB_ORG_NAMES,
        env.JENKINS_GITHUB_OAUTH_SCOPES,
        env.JENKINS_USER,
        env.JENKINS_PASS
)
