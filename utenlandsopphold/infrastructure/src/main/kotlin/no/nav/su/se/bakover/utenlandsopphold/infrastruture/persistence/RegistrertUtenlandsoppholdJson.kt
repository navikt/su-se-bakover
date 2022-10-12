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
import no.nav.su.se.bakover.utenlandsopphold.domain.RegistrertUtenlandsopphold
import no.nav.su.se.bakover.utenlandsopphold.domain.UtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.registrer.RegistrerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.UtenlandsoppholdDokumentasjonDatabaseJson.Companion.toJson
import java.util.UUID

internal data class RegistrertUtenlandsoppholdJson(
    val utenlandsoppholdId: UUID,
    val periode: PeriodeJson,
    val dokumentasjon: UtenlandsoppholdDokumentasjonDatabaseJson,
    val journalposter: List<String>,
    val ident: IdentJson,
    val erAnnulert: Boolean,
) {
    companion object {
        fun UtenlandsoppholdHendelse.toJson(): String {
            return RegistrertUtenlandsoppholdJson(
                periode = periode.toJson(),
                dokumentasjon = this.dokumentasjon.toJson(),
                ident = this.utførtAv.toIdentJson(),
                journalposter = journalposter.map { it.toString() },
                erAnnulert = erAnnulert,
                utenlandsoppholdId = this.utenlandsoppholdId,
            ).let {
                serialize(it)
            }
        }

        internal fun PersistertHendelse.toRegistrerUtenlandsoppholdHendelse(): RegistrerUtenlandsoppholdHendelse {
            return deserialize<RegistrertUtenlandsoppholdJson>(this.data).let { json ->
                RegistrerUtenlandsoppholdHendelse.fraPersistert(
                    utenlandsoppholdId = json.utenlandsoppholdId,
                    hendelseId = this.hendelseId,
                    sakId = this.sakId!!,
                    periode = json.periode.toDatoIntervall(),
                    dokumentasjon = json.dokumentasjon.toDomain(),
                    journalposter = json.journalposter.map { JournalpostId(it) },
                    opprettetAv = json.ident.toDomain() as NavIdentBruker.Saksbehandler,
                    hendelsestidspunkt = this.hendelsestidspunkt,
                    hendelseMetadata = this.hendelseMetadata,
                    forrigeVersjon = this.versjon,
                )
            }
        }

        internal fun PersistertHendelse.toOppdaterUtenlandsoppholdHendelse(): RegistrerUtenlandsoppholdHendelse {
            return deserialize<RegistrertUtenlandsoppholdJson>(this.data).let { json ->
                RegistrerUtenlandsoppholdHendelse.fraPersistert(
                    utenlandsoppholdId = json.utenlandsoppholdId,
                    hendelseId = this.hendelseId,
                    sakId = this.sakId!!,
                    periode = json.periode.toDatoIntervall(),
                    dokumentasjon = json.dokumentasjon.toDomain(),
                    journalposter = json.journalposter.map { JournalpostId(it) },
                    opprettetAv = json.ident.toDomain() as NavIdentBruker.Saksbehandler,
                    hendelsestidspunkt = this.hendelsestidspunkt,
                    hendelseMetadata = this.hendelseMetadata,
                    forrigeVersjon = this.versjon,
                )
            }
        }

        internal fun List<PersistertHendelse>.toRegistrertUtenlandsOpphold(): List<RegistrertUtenlandsopphold> {
            return this.map {
                Pair(it, deserialize<RegistrertUtenlandsoppholdJson>(it.data))
            }.groupBy {
                it.second.utenlandsoppholdId
            }.map {
                toRegistrertUtenlandsOpphold(it.value)
            }
        }

        // TODO jah: Flytt til domain
        private fun toRegistrertUtenlandsOpphold(
            hendelser: List<Pair<PersistertHendelse, RegistrertUtenlandsoppholdJson>>,
        ): RegistrertUtenlandsopphold {
            val (opprettetAv, opprettetTidspunkt) = hendelser.minBy {
                it.first.versjon
            }.let {
                Pair(it.second.ident.toDomain() as NavIdentBruker.Saksbehandler, it.first.hendelsestidspunkt)
            }
            return hendelser.maxByOrNull { it.first.versjon }!!.let { (hendelse, json) ->
                RegistrertUtenlandsopphold.fraHendelse(
                    utenlandsoppholdId = json.utenlandsoppholdId,
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
