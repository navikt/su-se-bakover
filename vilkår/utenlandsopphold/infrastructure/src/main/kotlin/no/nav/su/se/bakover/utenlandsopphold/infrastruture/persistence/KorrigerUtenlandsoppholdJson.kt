package no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson.Companion.toIdentJson
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import no.nav.su.se.bakover.utenlandsopphold.domain.korriger.KorrigerUtenlandsoppholdHendelse
import no.nav.su.se.bakover.utenlandsopphold.infrastruture.persistence.UtenlandsoppholdDokumentasjonDatabaseJson.Companion.toJson

internal data class KorrigerUtenlandsoppholdJson(
    val periode: PeriodeJson,
    val dokumentasjon: UtenlandsoppholdDokumentasjonDatabaseJson,
    val journalposter: List<String>,
    val begrunnelse: String?,
    val ident: IdentJson,
) {
    companion object {

        fun KorrigerUtenlandsoppholdHendelse.toJson(): String {
            return KorrigerUtenlandsoppholdJson(
                periode = periode.toJson(),
                dokumentasjon = this.dokumentasjon.toJson(),
                ident = this.utførtAv.toIdentJson(),
                journalposter = journalposter.map { it.toString() },
                begrunnelse = this.begrunnelse,
            ).let {
                serialize(it)
            }
        }

        fun PersistertHendelse.toKorrigerUtenlandsoppholdHendelse(): KorrigerUtenlandsoppholdHendelse {
            return deserialize<KorrigerUtenlandsoppholdJson>(this.data).let { json ->
                KorrigerUtenlandsoppholdHendelse.fraPersistert(
                    hendelseId = this.hendelseId,
                    tidligereHendelseId = this.tidligereHendelseId!!,
                    sakId = this.sakId!!,
                    periode = json.periode.toDatoIntervall(),
                    dokumentasjon = json.dokumentasjon.toDomain(),
                    journalposter = json.journalposter.map { JournalpostId(it) },
                    begrunnelse = json.begrunnelse,
                    utførtAv = json.ident.toDomain() as NavIdentBruker.Saksbehandler,
                    hendelsestidspunkt = this.hendelsestidspunkt,
                    versjon = this.versjon,
                    entitetId = this.entitetId,
                )
            }
        }
    }
}
