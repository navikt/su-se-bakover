package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.UtenlandsoppholdDokumentasjonJson.Companion.toJson

data class RegistrerteUtenlandsoppholdJson(
    val utenlandsopphold: List<EttUtenlandsoppholdJson>,
    val antallDager: Long,
) {
    data class EttUtenlandsoppholdJson(
        val periode: PeriodeJson,
        val journalposter: List<String>,
        val dokumentasjon: UtenlandsoppholdDokumentasjonJson,
        val begrunnelse: String?,
        val opprettetAv: String,
        val opprettetTidspunkt: String,
        val endretAv: String,
        val endretTidspunkt: String,
        val versjon: Long,
        val antallDager: Long,
        val erAnnullert: Boolean,
    )

    companion object {
        fun RegistrerteUtenlandsopphold.toJson() = RegistrerteUtenlandsoppholdJson(
            utenlandsopphold = this.map {
                EttUtenlandsoppholdJson(
                    versjon = it.versjon.value,
                    periode = it.periode.toJson(),
                    journalposter = it.journalposter.map { it.toString() },
                    dokumentasjon = it.dokumentasjon.toJson(),
                    begrunnelse = it.begrunnelse,
                    opprettetAv = it.opprettetAv.toString(),
                    opprettetTidspunkt = it.opprettetTidspunkt.toString(),
                    endretAv = it.endretAv.toString(),
                    endretTidspunkt = it.endretTidspunkt.toString(),
                    antallDager = it.antallDager,
                    erAnnullert = it.erAnnullert,
                )
            },
            antallDager = this.antallDager,
        )
    }
}
