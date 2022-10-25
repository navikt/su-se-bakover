package no.nav.su.se.bakover.database.sak

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson.Companion.toIdentJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.sak.SakRegistrertHendelse
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.domain.sak.Sakstype
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

/**
 *
 * @param fnr ønsker å persistere det fødselsnummeret som ble brukt ved sakens opprettelse.
 * @param ident Duplikat med HendelseMetadata.ident for ident-trigget hendelser.
 */
internal data class RegistrertSakHendelseDatabaseJson(
    val fnr: String,
    val ident: IdentJson,
    val type: String,
    val saksnummer: Long,
) {
    companion object {
        fun SakRegistrertHendelse.toSakOpprettetHendelseDatabaseJson(): String {
            return RegistrertSakHendelseDatabaseJson(
                fnr = this.fnr.toString(),
                ident = this.registrertAv.toIdentJson(),
                type = this.sakstype.value,
                saksnummer = saksnummer.nummer,
            ).let { serialize(it) }
        }

        internal fun toDomain(
            hendelseId: HendelseId,
            sakId: UUID,
            metadata: HendelseMetadata,
            json: String,
            entitetId: UUID,
            versjon: Long,
            hendelsestidspunkt: Tidspunkt,
        ): SakRegistrertHendelse {
            return deserialize<RegistrertSakHendelseDatabaseJson>(json).let {
                SakRegistrertHendelse.fraPersistert(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    meta = metadata,
                    fnr = Fnr(it.fnr),
                    opprettetAv = it.ident.toDomain(),
                    hendelsestidspunkt = hendelsestidspunkt,
                    entitetId = entitetId,
                    versjon = Hendelsesversjon(versjon),
                    type = Sakstype.valueOf(it.type),
                    saksnummer = Saksnummer(it.saksnummer),
                )
            }
        }
    }
}
