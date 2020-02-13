package no.nav.su.se.bakover.domain

class Sak(
        private val id: Long,
        private val fnr: String
) : PrimaryKey {
    override fun id() = id
    fun toJson() = """
        {
            "id":"$id",
            "fnr":"$fnr"
        }
    """.trimIndent()
}
