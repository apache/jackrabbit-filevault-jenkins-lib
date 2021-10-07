# Jenkins Shared Library

This library is used for all FileVault Jenkins builds and enabled on https://ci-builds.apache.org/job/Jackrabbit/job/filevault/ and ...

It follows the structure outlined at <https://www.jenkins.io/doc/book/pipeline/shared-libraries/>


It is supposed to be called in a Jenkinsfile like this

```
fileVaultMavenStdBuild([11, 8, 17], 11, [ "ubuntu", "Windows"], "ubuntu")
```

The first argument is an array of JDK versions to build with, the second one the main JDK version.
The third argument is an array of node labels to build on, the fourth one the main node label.

The main parameters specify on which environment the lengthy (environment independent) steps should happen like SonarQube analysis and optional deployment.