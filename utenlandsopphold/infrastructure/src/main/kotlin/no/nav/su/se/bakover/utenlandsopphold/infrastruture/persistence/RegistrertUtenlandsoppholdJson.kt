package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson.Companion.toIdentJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.periode.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.UtenlandsoppholdDokumentasjonDatabaseJson.Companion.toJson
import java.util.UUID

internal data class RegistrertUtenlandsoppholdJson(
    val registrertUtenlandsoppholdId: UUID,
    val periode: PeriodeJson,
    val dokumentasjon: UtenlandsoppholdDokumentasjonDatabaseJson,
    val journalposter: List<String>,
    val ident: IdentJson,
    val erAnnulert: Boolean,
) {
    companion object {
        fun RegistrerUtenlandsoppholdHendelse.toJson(): String {
            return RegistrertUtenlandsoppholdJson(
                periode = periode.toJson(),
                dokumentasjon = this.dokumentasjon.toJson(),
                ident = this.registrertAv.toIdentJson(),
                journalposter = journalposter.map { it.toString() },
                erAnnulert = erAnnulert,
                registrertUtenlandsoppholdId = this.entitetId,
            ).let {
                serialize(it)
            }
        }

        internal fun List<PersistertHendelse>.toDomain(): List<RegistrertUtenlandsopphold> {
            return this.map {
                Pair(it, deserialize<RegistrertUtenlandsoppholdJson>(it.data))
            }.groupBy {
                it.second.registrertUtenlandsoppholdId
            }.map {
                toDomain(it.value)
            }
        }

        private fun toDomain(
            hendelser: List<Pair<PersistertHendelse, RegistrertUtenlandsoppholdJson>>,
        ): RegistrertUtenlandsopphold {
            val (opprettetAv, opprettetTidspunkt) = hendelser.minBy {
                it.first.versjon
            }.let {
                Pair(it.second.ident.toDomain() as NavIdentBruker.Saksbehandler, it.first.hendelsestidspunkt)
            }
            return hendelser.maxByOrNull { it.first.versjon }!!.let { (hendelse, json) ->
                RegistrertUtenlandsopphold.fraHendelse(
                    utenlandsoppholdId = json.registrertUtenlandsoppholdId,
                    periode = json.periode.toDatoIntervall(),
                    dokumentasjon = json.dokumentasjon.toDomain(),
                    journalposter = json.journalposter.map { JournalpostId(it) },
                    opprettetAv = opprettetAv,
                    opprettetTidspunkt = opprettetTidspunkt,
                    endretAv = json.ident.toDomain() as NavIdentBruker.Saksbehandler,
                    endretTidspunkt = hendelse.hendelsestidspunkt,
                    versjon = hendelse.versjon,
                    erAnnulert = json.erAnnulert,
                )
            }
        }
    }
}
