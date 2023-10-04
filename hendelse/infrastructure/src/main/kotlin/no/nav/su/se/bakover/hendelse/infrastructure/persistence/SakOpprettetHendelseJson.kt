package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson
import no.nav.su.se.bakover.common.infrastructure.ident.IdentJson.Companion.toIdentJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.SakOpprettetHendelse
import java.util.UUID

/**
 * TODO jah: Flytt nær/inn til SakRepo
 * @param fnr ønsker å persistere det fødselsnummeret som ble brukt ved sakens opprettelse.
 * @param ident Duplikat med HendelseMetadata.ident for ident-trigget hendelser.
 */
internal data class SakOpprettetHendelseJson(
    val fnr: String,
    val ident: IdentJson,
) {
    companion object {
        fun SakOpprettetHendelse.toSakOpprettetHendelseData(): SakOpprettetHendelseJson {
            return SakOpprettetHendelseJson(
                fnr = this.fnr.toString(),
                ident = this.opprettetAv.toIdentJson(),
            )
        }

        internal fun toDomain(
            hendelseId: HendelseId,
            sakId: UUID,
            metadata: DefaultHendelseMetadata,
            json: String,
            entitetId: UUID,
            versjon: Long,
            hendelsestidspunkt: Tidspunkt,
        ): SakOpprettetHendelse {
            return deserialize<SakOpprettetHendelseJson>(json).let {
                SakOpprettetHendelse.fraPersistert(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    meta = metadata,
                    fnr = Fnr(it.fnr),
                    opprettetAv = it.ident.toDomain(),
                    hendelsestidspunkt = hendelsestidspunkt,
                    entitetId = entitetId,
                    versjon = versjon,
                )
            }
        }
    }
}
