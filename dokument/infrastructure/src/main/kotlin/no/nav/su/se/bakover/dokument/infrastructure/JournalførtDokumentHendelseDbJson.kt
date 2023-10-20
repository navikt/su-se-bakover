package no.nav.su.se.bakover.dokument.infrastructure

import arrow.core.NonEmptyList
import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentForArkiveringHendelse
import dokument.domain.hendelser.JournalførtDokumentForUtsendelseHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

internal data class JournalførtDokumentHendelseDbJson(
    val relaterteHendelser: List<String>,
    val journalpostId: String,
) {
    companion object {
        fun toDomain(
            type: Hendelsestype,
            data: String,
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
        ): DokumentHendelse {
            val deserialized = deserialize<JournalførtDokumentHendelseDbJson>(data)

            return when (type) {
                JournalførtDokumentForArkivering -> toJournalførtDokumentForArkiveringHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    meta = meta,
                    relaterteHendelser = deserialized.relaterteHendelser.map { HendelseId.fromString(it) }
                        .toNonEmptyList(),
                    journalpostId = JournalpostId(deserialized.journalpostId),
                )

                JournalførtDokumentForUtsendelse -> toJournalførtDokumentForUtsendelseHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    meta = meta,
                    relaterteHendelser = deserialized.relaterteHendelser.map { HendelseId.fromString(it) }
                        .toNonEmptyList(),
                    journalpostId = JournalpostId(deserialized.journalpostId),
                )

                else -> throw IllegalStateException("Ugyldig type for journalført dokument hendelse. type var $type")
            }
        }

        private fun toJournalførtDokumentForArkiveringHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
            relaterteHendelser: NonEmptyList<HendelseId>,
            journalpostId: JournalpostId,
        ): JournalførtDokumentForArkiveringHendelse = JournalførtDokumentForArkiveringHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            hendelseMetadata = meta,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relaterteHendelser = relaterteHendelser,
            journalpostId = journalpostId,
        )

        private fun toJournalførtDokumentForUtsendelseHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
            relaterteHendelser: NonEmptyList<HendelseId>,
            journalpostId: JournalpostId,
        ): JournalførtDokumentForUtsendelseHendelse = JournalførtDokumentForUtsendelseHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            hendelseMetadata = meta,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relaterteHendelser = relaterteHendelser,
            journalpostId = journalpostId,
        )

        internal fun JournalførtDokumentHendelse.dataDbJson(relaterteHendelser: NonEmptyList<HendelseId>): String =
            JournalførtDokumentHendelseDbJson(
                relaterteHendelser = relaterteHendelser.map { it.toString() },
                journalpostId = journalpostId.toString(),
            ).let { serialize(it) }
    }
}
