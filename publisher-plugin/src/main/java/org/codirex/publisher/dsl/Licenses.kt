package org.codirex.publisher.dsl

data class License(val name: String, val url: String, val spdxId: String)

/** Common SPDX license presets so modules don't have to spell out name/url by hand. */
object Licenses {
    val APACHE_2_0 = License(
        "Apache License, Version 2.0",
        "https://www.apache.org/licenses/LICENSE-2.0.txt",
        "Apache-2.0"
    )
    val MIT = License("MIT License", "https://opensource.org/licenses/MIT", "MIT")
    val GPL_3_0 = License(
        "GNU General Public License v3.0",
        "https://www.gnu.org/licenses/gpl-3.0.txt",
        "GPL-3.0"
    )
    val LGPL_2_1 = License(
        "GNU Lesser General Public License v2.1",
        "https://www.gnu.org/licenses/lgpl-2.1.txt",
        "LGPL-2.1"
    )
    val BSD_3_CLAUSE = License(
        "BSD 3-Clause License",
        "https://opensource.org/licenses/BSD-3-Clause",
        "BSD-3-Clause"
    )
}
