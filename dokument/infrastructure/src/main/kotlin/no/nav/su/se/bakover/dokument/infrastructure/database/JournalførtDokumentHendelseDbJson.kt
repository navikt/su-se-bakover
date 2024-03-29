package no.nav.su.se.bakover.dokument.infrastructure.database

import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.JournalførtDokument
import dokument.domain.hendelser.JournalførtDokumentHendelse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

internal data class JournalførtDokumentHendelseDbJson(
    val relaterteHendelse: String,
    val journalpostId: String,
    val skalSendeBrev: Boolean,
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
            val deserialized = deserialize<JournalførtDokumentHendelseDbJson>(data)

            return when (type) {
                JournalførtDokument -> toJournalførtDokumentHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    relaterteHendelse = HendelseId.fromString(deserialized.relaterteHendelse),
                    journalpostId = JournalpostId(deserialized.journalpostId),
                    skalSendeBrev = deserialized.skalSendeBrev,
                )

                else -> throw IllegalStateException("Ugyldig type for journalført dokument hendelse. type var $type")
            }
        }

        private fun toJournalførtDokumentHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            relaterteHendelse: HendelseId,
            journalpostId: JournalpostId,
            skalSendeBrev: Boolean,
        ): JournalførtDokumentHendelse = JournalførtDokumentHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relatertHendelse = relaterteHendelse,
            journalpostId = journalpostId,
            skalSendeBrev = skalSendeBrev,
        )

        internal fun JournalførtDokumentHendelse.dataDbJson(relaterteHendelse: HendelseId): String =
            JournalførtDokumentHendelseDbJson(
                relaterteHendelse = relaterteHendelse.toString(),
                journalpostId = journalpostId.toString(),
                skalSendeBrev = skalSendeBrev,
            ).let { serialize(it) }
    }
}
