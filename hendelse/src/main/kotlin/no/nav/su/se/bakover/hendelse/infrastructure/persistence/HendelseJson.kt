package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.hendelse.application.Hendelse
import no.nav.su.se.bakover.hendelse.application.HendelseMetadata
import no.nav.su.se.bakover.hendelse.application.SakOpprettetHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.IdentRolle.Companion.toIdentRolle
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.SakOpprettetHendelseJson.Companion.toSakOpprettetHendelseData
import java.util.UUID

internal data class SakOpprettetHendelseJson(
    val id: UUID,
    val sakId: UUID,
    val fnr: String,
    val hendelsestidspunkt: Tidspunkt,
    val ident: String,
    val identRolle: IdentRolle,
) {
    companion object {
        fun SakOpprettetHendelse.toSakOpprettetHendelseData(): SakOpprettetHendelseJson {
            return SakOpprettetHendelseJson(
                id = this.id,
                sakId = this.sakId,
                hendelsestidspunkt = this.hendelsestidspunkt,
                fnr = this.fnr.toString(),
                ident = this.opprettetAv.toString(),
                identRolle = this.opprettetAv.toIdentRolle(),
            )
        }

        internal fun toDomain(
            metadata: HendelseMetadata,
            json: String,
            entitetId: UUID,
            versjon: Long,
        ): SakOpprettetHendelse {
            return deserialize<SakOpprettetHendelseJson>(json).let {
                SakOpprettetHendelse(
                    id = it.id,
                    sakId = it.sakId,
                    meta = metadata,
                    fnr = Fnr(it.fnr),
                    opprettetAv = it.identRolle.toDomain(it.ident),
                    hendelsestidspunkt = it.hendelsestidspunkt,
                ).also {
                    require(entitetId == it.entitetId)
                    require(Hendelse.Versjon(versjon) == it.versjon)
                }
            }
        }
    }
}

internal fun Hendelse.toData(): String {
    return when (this) {
        is SakOpprettetHendelse -> this.toSakOpprettetHendelseData()
    }.let {
        serialize(it)
    }
}

internal fun toHendelse(
    type: String,
    dataJson: String,
    metadataJson: String,
    entitetId: UUID,
    versjon: Long,
): Hendelse {
    val metadata = deserialize<MetadataJson>(metadataJson).toDomain()
    return when (HendelseType.valueOf(type)) {
        HendelseType.SAK_OPPRETTET -> SakOpprettetHendelseJson.toDomain(
            metadata = metadata,
            json = dataJson,
            entitetId = entitetId,
            versjon = versjon,
        )
    }
}
