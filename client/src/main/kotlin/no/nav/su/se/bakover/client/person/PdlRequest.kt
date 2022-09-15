package no.nav.su.se.bakover.client.person

data class PdlRequest(
    val query: String,
    val variables: Variables,
)

data class Variables(
    val ident: String,
    val historikk: Boolean = false,
    val identGrupper: List<String> = listOf(FOLKEREGISTERIDENT, AKTORID),
) {
    companion object {
        const val FOLKEREGISTERIDENT = "FOLKEREGISTERIDENT"
        const val AKTORID = "AKTORID"
    }
}
