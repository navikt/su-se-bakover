package no.nav.su.se.bakover.domain

class Sak(
        private val id: Long,
        private val fnr: String
) {
    fun toJson() = """
        {
            "id":"$id",
            "fnr":"$fnr"
        }
    """.trimIndent()
}