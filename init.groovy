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

    // https://github.com/mocleiri/github-oauth-plugin/blob/master/src/main/java/org/jenkinsci/plugins/GithubSecurityRealm.java
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
            false,     /*allowAnonymousReadPermission*/
            false      /*allowAnonymousJobStatusPermission*/
    )

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

// Configure SAML Security
def configureSAMLAuthorizationStrategy = { idpMetadata,
                                           displayNameAttributeName,
                                           groupsAttributeName,
                                           maximumAuthenticationLifetime,
                                           usernameAttributeName,
                                           emailAttributeName,
                                           logoutUrl,
                                           usernameCaseConversion,
                                           binding ->
    if (!isValidString(idpMetadata)) {
        throw new Throwable("'JENKINS_SAML_IDP_METADATA' is required")
    }
    if (!isValidString(displayNameAttributeName)) {
        throw new Throwable("'JENKINS_SAML_DISPLAY_NAME_ATTRIBUTE_NAME' is required")
    }
    if (!isValidString(groupsAttributeName)) {
        throw new Throwable("'JENKINS_SAML_GROUPS_ATTRIBUTE_NAME' is required")
    }
    if (!isValidString(maximumAuthenticationLifetime)) {
        throw new Throwable("'JENKINS_SAML_MAXIMUM_AUTHENTICATION_LIFETIME' is required")
    }
    if (!isValidString(usernameAttributeName)) {
        throw new Throwable("'JENKINS_SAML_USERNAME_ATTRIBUTE_NAME' is required")
    }
    if (!isValidString(emailAttributeName)) {
        throw new Throwable("'JENKINS_SAML_EMAIL_ATTRIBUTE_NAME' is required")
    }

    // https://github.com/jenkinsci/saml-plugin/blob/master/src/main/java/org/jenkinsci/plugins/saml/SamlSecurityRealm.java
    /**
     * @param idpMetadata Identity provider Metadata.
     * @param displayNameAttributeName attribute that has the displayname.
     * @param groupsAttributeName attribute that has the groups.
     * @param maximumAuthenticationLifetime maximum time that an identification it is valid.
     * @param usernameAttributeName attribute that has the username.
     * @param emailAttributeName attribute that has the email.
     * @param logoutUrl optional URL to redirect on logout.
     * @param advancedConfiguration advanced configuration settings.
     * @param encryptionData encryption configuration settings.
     * @param usernameCaseConversion username case sensitive settings.
     * @param binding SAML binding method.
     */
    def securityRealm = new SamlSecurityRealm(
            new String(idpMetadata.decodeBase64()),
            displayNameAttributeName,
            groupsAttributeName,
            maximumAuthenticationLifetime.toInteger(),
            usernameAttributeName,
            emailAttributeName,
            logoutUrl ?: null,
            null,
            null,
            usernameCaseConversion ?: null,
            binding ?: null
    )

    jenkins.setSecurityRealm(securityRealm)

    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    jenkins.setAuthorizationStrategy(strategy)

    jenkins.save()
}

// Configure Authorization Strategy ('SAML', 'GitHub' or 'Matrix')
def jenkinsAuthorizationStrategy = env.JENKINS_AUTHORIZATION_STRATEGY ?: 'Matrix'
switch (jenkinsAuthorizationStrategy) {
    case "None":
        // Do nothing. We just don't want to override the security settings in Jenkins that were set up manually
        break
    case "GitHub":
        configureGitHubAuthorizationStrategy(
                env.JENKINS_GITHUB_CLIENT_ID,
                env.JENKINS_GITHUB_CLIENT_SECRET,
                env.JENKINS_GITHUB_ADMINS,
                env.JENKINS_GITHUB_ORG_NAMES,
                env.JENKINS_GITHUB_OAUTH_SCOPES
        )
        break
    case "Matrix":
        configureMatrixAuthorizationStrategy(
                env.JENKINS_USER,
                env.JENKINS_PASS
        )
        break
    case "SAML":
        configureSAMLAuthorizationStrategy(
                env.JENKINS_SAML_IDP_METADATA,
                env.JENKINS_SAML_DISPLAY_NAME_ATTRIBUTE_NAME,
                env.JENKINS_SAML_GROUPS_ATTRIBUTE_NAME,
                env.JENKINS_SAML_MAXIMUM_AUTHENTICATION_LIFETIME,
                env.JENKINS_SAML_USERNAME_ATTRIBUTE_NAME,
                env.JENKINS_SAML_EMAIL_ATTRIBUTE_NAME,
                env.JENKINS_SAML_LOGOUT_URL,
                env.JENKINS_SAML_USERNAME_CASE_CONVERSION,
                env.JENKINS_SAML_BINDING
        )
        break
    default:
        throw new Throwable("Invalid 'JENKINS_AUTHORIZATION_STRATEGY'")
}

// Set number of job executors
int num_executors = 4
if (isValidString(env.JENKINS_NUM_EXECUTORS)) {
    num_executors = env.JENKINS_NUM_EXECUTORS.toInteger()
}
jenkins.setNumExecutors(num_executors)

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
