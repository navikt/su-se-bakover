package no.nav.su.se.bakover.domain

class Sak(
        internal val id: Long,
        private val fnr: String
) {
    fun toJson() = """
        {
            "id":"$id",
            "fnr":"$fnr"
        }
    """.trimIndent()
}
