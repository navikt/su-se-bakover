package no.nav.su.se.bakover.domain

class Behandling constructor(
        private val id: Long
) {
    fun toJson() = """
        {
            "id": $id
        }
    """.trimIndent()
}