package org.codirex.publisher.dsl

/** Maven coordinates for a module: `identity("org.codirex", "axiom", "1.0.0")`. */
data class IdentityDsl(
    val groupId: String,
    val artifactId: String,
    val version: String
) {
    val coordinates: String get() = "$groupId:$artifactId:$version"
}
