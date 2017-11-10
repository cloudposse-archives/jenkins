## Configure Github 

This is how to setup GitHub OAuth2 on Jenkins to authenticate users. 

This was originally documented by [David Hollenberger](http://davidhollenberger.com/2015/09/25/jenkins-github-oath/).

### Register Github Application

- Register a new [GitHub OAuth Applications](https://github.com/settings/applications/new).
- Fill out registration form. Make sure to enter `http://jenkins.dns.address/securityRealm/finishLogin` for the Authorization Calback URL.

### Jenkins configuration

**Install `github-oauth` plugin**

See the [github-oauth documentation page](https://wiki.jenkins-ci.org/display/JENKINS/Github+OAuth+Plugin#GithubOAuthPlugin-Setup).

**Configure Global Security**

- Select _Github Authentication Plugin_
    - Enter Client ID and Client Secret from the Github Application registration page.
    - Keep the other fields default.

**Authorization**

- select _Github Commiter Authorization Strategy_.

**Github Authorization Settings**

- _Admin User Names_ enter Github username for any administrators
- _Participant in Organization_ 

Enter the GitHub Organization name. Any user who is registered with the organization will have job view and build
- Check _Use Github repository permissions_
- Check _Grant READ permissions for `/github-webhook`_
