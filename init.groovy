#!groovy

import hudson.model.*
import hudson.tools.*
import hudson.plugins.*
import hudson.security.*
import hudson.security.csrf.*
import hudson.security.SecurityRealm.*
import jenkins.model.*
import jenkins.security.s2m.AdminWhitelistRule
import org.jenkinsci.plugins.*
import org.jenkinsci.plugins.oic.*
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

def configureOICAuthorizationStrategy = { clientId,
                                          clientSecret,
                                          tokenServerUrl,
                                          authorizationServerUrl,
                                          userInfoServerUrl,
                                          userNameField,
                                          tokenFieldToCheckKey,
                                          tokenFieldToCheckValue,
                                          fullNameFieldName,
                                          emailFieldName,
                                          scopes,
                                          groupsFieldName,
                                          disableSslVerification,
                                          logoutFromOpenidProvider,
                                          endSessionUrl,
                                          postLogoutRedirectUrl,
                                          escapeHatchEnabled,
                                          escapeHatchUsername,
                                          escapeHatchSecret,
                                          escapeHatchGroup ->
    println "configureOICAuthorizationStrategy()"

    if (!isValidString(clientId)) {
        throw new Throwable("'JENKINS_OIC_CLIENT_ID' is required")
    }
    if (!isValidString(clientSecret)) {
        throw new Throwable("'JENKINS_OIC_CLIENT_SECRET' is required")
    }

    if (!isValidString(tokenServerUrl)) {
        throw new Throwable("'JENKINS_OIC_TOKEN_SERVER_URL' is required")
    }

    if (!isValidString(authorizationServerUrl)) {
        throw new Throwable("'JENKINS_OIC_AUTHORIZATION_SERVER_URL' is required")
    }

    /* 'JENKINS_OIC_USER_INFO_SERVER_URL' is optional */

    if (!isValidString(userNameField)) {
        throw new Throwable("'JENKINS_OIC_USER_NAME_FIELD' is required")
    }

    /*  JENKINS_OIC_TOKEN_FIELD_TO_CHECK_KEY' is optional */

    /*  JENKINS_OIC_TOKEN_FIELD_TO_CHECK_VALUE' is optoinal */

    if (!isValidString(fullNameFieldName)) {
        throw new Throwable("'JENKINS_OIC_FULL_NAME_FIELD_NAME' is required")
    }

    if (!isValidString(emailFieldName)) {
        throw new Throwable("'JENKINS_OIC_EMAIL_FIELD_NAME' is required")
    }

    if (!isValidString(scopes)) {
        throw new Throwable("'JENKINS_OIC_SCOPES)' is required")
    }

    /* 'JENKINS_OIC_GROUPS_FIELD_NAME' is optional */

    if (!isValidString(disableSslVerification)) {
        throw new Throwable("'JENKINS_OIC_DISABLE_SSL_VERIFICATION' is required")
    }

    if (!isValidString(logoutFromOpenidProvider)) {
        logoutFromOpenidProvider = "false"
    }

    if (logoutFromOpenidProvider.toBoolean() == true) {
        if (!isValidString(endSessionUrl)) {
            throw new Throwable("'JENKINS_OIC_END_SESSION_URL' is required")
        }
        if (!isValidString(postLogoutRedirectUrl)) {
            throw new Throwable("'JENKINS_OIC_POST_LOGOUT_REDIRECT_URL' is required")
        }
    }

    if (!isValidString(escapeHatchEnabled)) {
        throw new Throwable("'JENKINS_OIC_ESCAPE_HATCH_ENABLED' is required")
    }

    if (escapeHatchEnabled.toBoolean() == true) {
        if (!isValidString(escapeHatchUsername)) {
            throw new Throwable("'JENKINS_OIC_ESCAPE_HATCH_USERNAME' is required")
        }

        if (!isValidString(escapeHatchSecret)) {
            throw new Throwable("'JENKINS_OIC_ESCAPE_HATCH_SECRET' is required")
        }

        if (!isValidString(escapeHatchGroup)) {
            throw new Throwable("'JENKINS_OIC_ESCAPE_HATCH_GROUP' is required")
        }
    }

    // https://github.com/jenkinsci/oic-auth-plugin/blob/master/src/main/java/org/jenkinsci/plugins/oic/OicSecurityRealm.java
    /**
     * @param clientId Client ID used by jenkins to authenticate itself to the openid connect provider
     * @param clientSecret Client secret used by jenkins to authenticate itself to the openid connect provider
     * @param tokenServerUrl Token server URL used by jenkins to obtain tokens
     * @param authorizationServerUrl Authorization server URL used by jenkins to authenticate with the openid connect provider
     * @param userInfoServerUrl Userinfo URL of the openid connect provider
     * @param userNameField Field within token used to obtain jenkins user's username
     * @param tokenFieldToCheckKey If specified, users are required to have this field match the value to successfully login
     * @param tokenFieldToCheckValue If specified, users are required to have this field match the value to successfully login
     * @param fullNameFieldName Field within token used to obtain jenkins user's full user name
     * @param emailFieldName  Field within token used to obtain jenkins user's email address
     * @param scopes Scopes to request to be included in response from openid connect provider
     * @param groupsFieldName Name of field that lists the user's groups
     * @param disableSslVerification Disable verification of SSL cert
     * @param logoutFromOpenidProvider When user logs out of jenkins they also log out of openid connect provider
     * @param endSessionUrl URL to openid connect provider logout endpotin
     * @param postLogoutRedirectUrl URL to which user is sent after logout completes.
     * @param escapeHatchEnabled Enable escape hatch to provide alternative login mechanism (should OIC be unavailable)
     * @param escapeHatchUsername Escape hatch username
     * @param escapeHatchSecret Escape hatch password
     * @param escapeHatchGroup Escape hatch user's group
     */
    def securityRealm

    securityRealm = new OicSecurityRealm(clientId, clientSecret,
                        tokenServerUrl, authorizationServerUrl, userInfoServerUrl,
                        userNameField, tokenFieldToCheckKey, tokenFieldToCheckValue, fullNameFieldName, emailFieldName,
                        scopes,
                        groupsFieldName,
                        disableSslVerification.toBoolean(),
                        logoutFromOpenidProvider.toBoolean(),
                        endSessionUrl, postLogoutRedirectUrl,
                        escapeHatchEnabled.toBoolean(), escapeHatchUsername, escapeHatchSecret, escapeHatchGroup
    )

    jenkins.setSecurityRealm(securityRealm)

    def strategy = new FullControlOnceLoggedInAuthorizationStrategy()
    strategy.setAllowAnonymousRead(false)
    jenkins.setAuthorizationStrategy(strategy)

    jenkins.save()
}

// Configure Authorization Strategy ('SAML', 'GitHub' or 'Matrix')
def jenkinsAuthorizationStrategy = env.JENKINS_AUTHORIZATION_STRATEGY ?: 'Matrix'

println "Jenkins Authorization Strategy ${jenkinsAuthorizationStrategy}"

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
    case "OIC":
        configureOICAuthorizationStrategy(
                env.JENKINS_OIC_CLIENT_ID,
                env.JENKINS_OIC_CLIENT_SECRET,
                env.JENKINS_OIC_TOKEN_SERVER_URL,
                env.JENKINS_OIC_AUTHORIZATION_SERVER_URL,
                env.JENKINS_OIC_USER_INFO_SERVER_URL,
                env.JENKINS_OIC_USER_NAME_FIELD,
                env.JENKINS_OIC_TOKEN_FIELD_TO_CHECK_KEY,
                env.JENKINS_OIC_TOKEN_FIELD_TO_CHECK_VALUE,
                env.JENKINS_OIC_FULL_NAME_FIELD_NAME,
                env.JENKINS_OIC_EMAIL_FIELD_NAME,
                env.JENKINS_OIC_SCOPES,
                env.JENKINS_OIC_GROUPS_FIELD_NAME,
                env.JENKINS_OIC_DISABLE_SSL_VERIFICATION,
                env.JENKINS_OIC_LOGOUT_FROM_OPENID_PROVIDER,
                env.JENKINS_OIC_END_SESSION_URL,
                env.JENKINS_OIC_POST_LOGOUT_REDIRECT_URL,
                env.JENKINS_OIC_ESCAPE_HATCH_ENABLED,
                env.JENKINS_OIC_ESCAPE_HATCH_USERNAME,
                env.JENKINS_OIC_ESCAPE_HATCH_SECRET,
                env.JENKINS_OIC_ESCAPE_HATCH_GROUP
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
