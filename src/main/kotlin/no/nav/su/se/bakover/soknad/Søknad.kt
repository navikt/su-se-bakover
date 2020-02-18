package no.nav.su.se.bakover.soknad

class Søknad(
        internal val id: Long,
        private val søknadJson: String,
        private val sakId: Long
) {
    fun toJson() = """
        {
            "id":"$id",
            "json":$søknadJson,
            "sakId":"$sakId"
        }
    """.trimIndent()
}