package org.codirex.publisher.dsl

/** One `<developer>` entry in the generated POM. */
data class Developer(
    val id: String,
    val name: String,
    val email: String? = null,
    val url: String? = null,
    val roles: List<String> = emptyList()
)
