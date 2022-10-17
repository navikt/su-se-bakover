package no.nav.su.se.bakover.utenlandsopphold.infrastruture.web

import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.antallDager
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.web.UtenlandsoppholdDokumentasjonJson.Companion.toJson

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
        val erAnnulert: Boolean,
    )

    companion object {
        fun List<RegistrertUtenlandsopphold>.toJson() = RegistrerteUtenlandsoppholdJson(
            utenlandsopphold = this.map {
                EttUtenlandsoppholdJson(
                    // Frontend forholder seg til kombinasjonen av entitetsid + versjon (unik).
                    id = it.utenlandsoppholdId.toString(),
                    versjon = it.versjon.value,
                    periode = it.periode.toJson(),
                    journalposter = it.journalposter.map { it.toString() },
                    dokumentasjon = it.dokumentasjon.toJson(),
                    opprettetAv = it.opprettetAv.toString(),
                    opprettetTidspunkt = it.opprettetTidspunkt.toString(),
                    endretAv = it.endretAv.toString(),
                    endretTidspunkt = it.endretTidspunkt.toString(),
                    antallDager = it.antallDager,
                    erAnnulert = it.erAnnulert,
                )
            },
            antallDager = this.antallDager,
        )
    }
}
