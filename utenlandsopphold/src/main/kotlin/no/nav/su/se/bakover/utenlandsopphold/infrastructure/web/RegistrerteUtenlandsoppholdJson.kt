package no.nav.su.se.bakover.utenlandsopphold.infrastructure.web

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson

data class RegistrerteUtenlandsoppholdJson(
    val utenlandsopphold: List<EttUtenlandsoppholdJson>,
    val antallDager: Long,
) {
    data class EttUtenlandsoppholdJson(
        val id: String,
        val periode: PeriodeJson,
        val journalpostIder: List<String>,
        val dokumentasjon: UtenlandsoppholdDokumentasjonJson,
        val saksbehandler: String,
        val registrertTidspunkt: String,
        val endretTidspunkt: String,
        val versjon: Long,
        val antallDager: Long,
    )
}
