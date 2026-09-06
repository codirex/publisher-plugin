package org.codirex.publisher.task

import org.gradle.api.DefaultTask

/** Umbrella lifecycle task: `./gradlew publishAll` runs every enabled target. */
abstract class PublishAllTask : DefaultTask() {
    init {
        group = "publishing"
        description = "Publishes this module to every enabled target (GitHub Packages, Maven Central, Maven Local)."
    }
}
