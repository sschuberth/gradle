/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.plugins.integrationtests

import org.gradle.api.DomainObjectCollection
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.build.ReleasedVersionsFromVersionControl
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.testfixtures.TestFixturesExtension
import org.gradle.plugins.testfixtures.TestFixturesPlugin
import org.gradle.testing.CrossVersionTest
import org.gradle.testing.IntegrationTest

class CrossVersionTestsPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        val sourceSet = addSourceSet(TestType.CROSSVERSION)
        addDependenciesAndConfigurations(TestType.CROSSVERSION)
        createTasks(sourceSet, TestType.CROSSVERSION)
        createAggregateTasks()
        configureIde(TestType.CROSSVERSION)
        configureTestFixturesForCrossVersionTests()
    }

    private fun Project.configureTestFixturesForCrossVersionTests() {
        plugins.withType<TestFixturesPlugin> {
            configure<TestFixturesExtension> {
                from(":toolingApi")
            }
        }
    }

    private
    fun Project.createAggregateTasks() {
        // Calculate the set of released versions - do this at configuration time because we need this to create various tasks
        val allVersionsCrossVersionTests by tasks.creating {
            group = "verification"
            description = "Runs the cross-version tests against all Gradle versions with 'forking' executer"
        }

        val quickFeedbackCrossVersionTests by tasks.creating {
            group = "verification"
            description = "Runs the cross-version tests against a subset of selected Gradle versions with 'forking' executer for quick feedback"
        }

        val releasedVersions = property("releasedVersions") as ReleasedVersionsFromVersionControl
        val quickTestVersions = releasedVersions.getTestedVersions(true)
        releasedVersions.getTestedVersions(false).forEach { targetVersion ->
            tasks.create<CrossVersionTest>("gradle${targetVersion}CrossVersionTest") {
                allVersionsCrossVersionTests.dependsOn(this)
                description = "Runs the cross-version tests against Gradle $targetVersion"
                systemProperties["org.gradle.integtest.versions"] = targetVersion
                systemProperties["org.gradle.integtest.executer"] = "forking"
                if (targetVersion in quickTestVersions) {
                    quickFeedbackCrossVersionTests.dependsOn(this)
                }
            }
        }
    }
}
