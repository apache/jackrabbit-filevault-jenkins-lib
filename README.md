# Jenkins Shared Library

This library is used for all FileVault Jenkins builds and enabled on <https://ci-builds.apache.org/job/Jackrabbit/job/filevault/>

It follows the structure outlined at <https://www.jenkins.io/doc/book/pipeline/shared-libraries/>

It is supposed to be called in a `Jenkinsfile` like this

```
vaultPipeline('ubuntu', 11, '3', {
   vaultStageBuild(['ubuntu', 'Windows'], [8, 11, 17], ['3', '3.6.3'], 'apache_jackrabbit-filevault-package-maven-plugin') 
   vaultStageDeploy()
  }
)
```

The `vaultPipeline` step encapsulates the main build environment parameters:
The first argument is the main *node label* to build with, the second one the main *JDK version*, third argument the main *Maven version*
The fourth argument is a closure containing the actual stages where each may be one of

1. `vaultStageBuild`: the actual Maven build and SonarQube execution (the latter only for the main environment)
1. `vaultStageIT`: an isolated execution of just the integration tests
1. `vaultStageDeploy`: the stage to deploy the previously built Maven artifacts to the ASF Snapshot Repository (depends on 1.)

For the parametrisation of those individual stages refer the source code.