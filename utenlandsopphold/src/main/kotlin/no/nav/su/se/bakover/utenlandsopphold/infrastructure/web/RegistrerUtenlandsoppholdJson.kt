package no.nav.su.se.bakover.utenlandsopphold.infrastructure.web

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson

/**
 * @param journalpostIder kan være tom dersom det ikke er knyttet noen journalposter til utenlandsoppholdet.
 */
data class RegistrerUtenlandsoppholdJson(
    val periode: PeriodeJson,
    val journalpostIder: List<String>,
    val dokumentasjon: Dokumentasjon,
) {
    enum class Dokumentasjon {
        Udokumentert,
        Dokumentert,
        Sannsynliggjort,
    }
}
