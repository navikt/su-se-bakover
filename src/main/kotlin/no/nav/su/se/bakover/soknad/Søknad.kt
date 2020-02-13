package no.nav.su.se.bakover.soknad

import no.nav.su.se.bakover.domain.PrimaryKey

class Søknad(
        private val id: Long,
        private val søknadJson: String,
        private val sakId: Long
) : PrimaryKey {
    override fun id() = id
    fun toJson() = """
        {
            "id":"$id",
            "json":$søknadJson,
            "sakId":"$sakId"
        }
    """.trimIndent()
}