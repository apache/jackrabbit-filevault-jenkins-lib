/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault

/**
 * Singleton class encapsulating information about main build environment and some helper methods.
 */
class PipelineSupport implements Serializable {
    
    static PipelineSupport INSTANCE
    
    static PipelineSupport createInstance(String mainNodeLabel, int mainJdkVersion, String mainMavenVersion, String mainBranch) {
        INSTANCE = new PipelineSupport(mainNodeLabel, mainJdkVersion, mainMavenVersion, mainBranch)
        return INSTANCE
    }

    static PipelineSupport getInstance() {
        if (INSTANCE == null) {
            throw new IllegalStateException("createInstance has not been called before, not wrapped in vaultPipeline step?")
        }
        return INSTANCE
    }

    private final String mainNodeLabel
    private final Integer mainJdkVersion
    private final String mainMavenVersion
    private final String mainBranch

    PipelineSupport(String mainNodeLabel, int mainJdkVersion, String mainMavenVersion, String mainBranch) {
        this.mainNodeLabel = mainNodeLabel
        this.mainJdkVersion = mainJdkVersion
        this.mainMavenVersion = mainMavenVersion
        this.mainBranch = mainBranch
    }

    def executeMaven(pipeline, String mavenArguments, boolean enablePublishers) {
        executeMaven(pipeline, mainJdkVersion, mavenArguments, enablePublishers)
    }

    def executeMaven(pipeline, Integer jdkVersion, String mavenArguments, boolean enablePublishers) {
        executeMaven(pipeline, jdkVersion, mainMavenVersion, mavenArguments, '', enablePublishers)
    }

    static def withSimpleCredentials(pipeline, Map<String, String> credentialIdToEnvironmentVariable, Closure closure) {
        if (credentialIdToEnvironmentVariable == null) {
            closure.call()
        } else {
            def bindings = credentialIdToEnvironmentVariable.collect{ e -> pipeline.string(credentialsId: e.key, variable: e.value) }
            pipeline.withCredentials(bindings, closure)
        }
    }

    static def executeMaven(pipeline, Integer jdkVersion, String mavenVersion, String mavenArguments, String mavenOpts = '', boolean enablePublishers) {
        String maven = AsfCloudbeesJenkinsEnvironment.getMavenLabel(!pipeline.isUnix(), mavenVersion)
        String jdk = AsfCloudbeesJenkinsEnvironment.getJdkLabel(jdkVersion)
        pipeline.echo("Using Maven '${maven}' with JDK '{jdk}'")
        pipeline.withMaven(
            maven: maven,
            jdk: jdk,
            mavenLocalRepo: '.repository',
            mavenOpts: mavenOpts,
            publisherStrategy: enablePublishers?'IMPLICIT':'EXPLICIT') {
            if (pipeline.isUnix()) {
                pipeline.sh "mvn -B -e ${mavenArguments}"
            } else {
                pipeline.bat "mvn -B -e ${mavenArguments}"
            }
        }
    }

    def stepsForMainAndAdditional(String stepsLabel, Set<String> nodeLabels, Set<Integer> jdkVersions, Set<String> mavenVersions, Closure closure) {
        stepsFor(stepsLabel, nodeLabels.plus(mainNodeLabel), jdkVersions.plus(mainJdkVersion), mavenVersions.plus(mainMavenVersion), closure, false)
    }

    def stepsFor(String stepsLabel, Set<String> nodeLabels, Set<Integer> jdkVersions, Set<String> mavenVersions, Closure closure, boolean excludeMain = false) {
        def stepsMap = [failFast: true]
        for (nodeLabel in nodeLabels) {
            for (jdkVersion in jdkVersions) {
                for (mavenVersion in mavenVersions) {
                    if (mavenVersion.startsWith('4') && jdkVersion < 17) {
                        continue; // skip incompatible combinations
                    }
                    boolean isMainBuild = (nodeLabel.equals(mainNodeLabel) && jdkVersion.equals(mainJdkVersion) && mavenVersion.equals(mainMavenVersion))
                    if (excludeMain && isMainBuild) {
                        continue // skip main environment
                    }
                    stepsMap["${stepsLabel} (JDK ${jdkVersion}, ${nodeLabel}, Maven ${mavenVersion}${isMainBuild ? ' (Main)' : ''})"] = closure(nodeLabel, jdkVersion, mavenVersion, isMainBuild)
                }
            }
        }
        return stepsMap
    }

    String getMainNodeLabel() {
        return mainNodeLabel
    }

    boolean isOnMainBranch(String currentBranch) {
        return mainBranch == currentBranch
    }

    String getMainBranch() {
        return mainBranch
    }
}