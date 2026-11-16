package no.nav.su.se.bakover.common.infrastructure.brukerrolle

data class AzureGroups(
    val attestant: String,
    val saksbehandler: String,
    val veileder: String,
    val drift: String,
    val strengtFortroligAdresse: String,
) {
    // Adressebeskyttelsesgruppen gir ikke i seg selv tilgang til applikasjonen.
    fun asList() = listOf(attestant, saksbehandler, veileder, drift)
}
