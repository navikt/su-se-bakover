package no.nav.su.se.bakover.utenlandsopphold.presentation.web

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.antallDager
import no.nav.su.se.bakover.utenlandsopphold.presentation.web.UtenlandsoppholdDokumentasjonJson.Companion.toJson

data class RegistrerteUtenlandsoppholdJson(
    val utenlandsopphold: List<EttUtenlandsoppholdJson>,
    val antallDager: Long,
) {
    data class EttUtenlandsoppholdJson(
        val id: String,
        val periode: PeriodeJson,
        val journalposter: List<String>,
        val dokumentasjon: UtenlandsoppholdDokumentasjonJson,
        val opprettetAv: String,
        val opprettetTidspunkt: String,
        val endretAv: String,
        val endretTidspunkt: String,
        val versjon: Long,
        val antallDager: Long,
        val erGyldig: Boolean,
    )

    companion object {
        fun List<RegistrertUtenlandsopphold>.toJson() = RegistrerteUtenlandsoppholdJson(
            utenlandsopphold = this.map {
                EttUtenlandsoppholdJson(
                    id = it.id.toString(),
                    periode = it.periode.toJson(),
                    journalposter = it.journalposter.map { it.toString() },
                    dokumentasjon = it.dokumentasjon.toJson(),
                    opprettetAv = it.opprettetAv.toString(),
                    opprettetTidspunkt = it.opprettetTidspunkt.toString(),
                    endretAv = it.endretAv.toString(),
                    endretTidspunkt = it.endretTidspunkt.toString(),
                    versjon = it.versjon,
                    antallDager = it.antallDager,
                    erGyldig = it.erGyldig,
                )
            },
            antallDager = this.antallDager,
        )
    }
}
