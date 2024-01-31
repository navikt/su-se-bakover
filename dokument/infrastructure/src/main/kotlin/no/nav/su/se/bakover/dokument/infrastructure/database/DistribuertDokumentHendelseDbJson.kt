package no.nav.su.se.bakover.dokument.infrastructure.database

import dokument.domain.brev.BrevbestillingId
import dokument.domain.hendelser.DistribuertDokument
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

internal data class DistribuertDokumentHendelseDbJson(
    val relaterteHendelse: String,
    val brevbestillingId: String,
) {
    companion object {
        fun toDomain(
            type: Hendelsestype,
            data: String,
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
        ): DokumentHendelse {
            val deserialized = deserialize<DistribuertDokumentHendelseDbJson>(data)

            return when (type) {
                DistribuertDokument -> toDistribuertDokumentHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    relaterteHendelse = HendelseId.fromString(deserialized.relaterteHendelse),
                    brevbestillingId = deserialized.brevbestillingId,
                )

                else -> throw IllegalStateException("Ugyldig type for journalført dokument hendelse. type var $type")
            }
        }

        private fun toDistribuertDokumentHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            relaterteHendelse: HendelseId,
            brevbestillingId: String,
        ): DistribuertDokumentHendelse = DistribuertDokumentHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relatertHendelse = relaterteHendelse,
            brevbestillingId = BrevbestillingId(brevbestillingId),
        )

        internal fun DistribuertDokumentHendelse.dataDbJson(relaterteHendelse: HendelseId): String =
            DistribuertDokumentHendelseDbJson(
                relaterteHendelse = relaterteHendelse.toString(),
                brevbestillingId = this.brevbestillingId.value,
            ).let { serialize(it) }
    }
}
