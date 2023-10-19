package no.nav.su.se.bakover.dokument.infrastructure

import arrow.core.NonEmptyList
import dokument.domain.Dokument
import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.LagretDokumentForJournalføringHendelse
import dokument.domain.hendelser.LagretDokumentForUtsendelseHendelse
import dokument.domain.hendelser.LagretDokumentHendelse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.DokumentMetaDataDbJson.Companion.toHendelseDbJson
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

internal data class DokumentHendelseDbJson(
    val id: UUID,
    val opprettet: Tidspunkt,
    val distribusjonstype: DistribusjonstypeDbJson,
    val distribusjonstidspunkt: DistribusjonstidspunktDbJson,
    val tittel: String,
    val generertDokumentJson: String,
    val relaterteHendelser: List<String>,
    val dokumentMeta: DokumentMetaDataDbJson,
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
            val deserialized = deserialize<DokumentHendelseDbJson>(data)
            val dokumentUtenFil = DokumentMedMetadataUtenFil(
                id = deserialized.id,
                opprettet = deserialized.opprettet,
                tittel = deserialized.tittel,
                metadata = Dokument.Metadata(
                    sakId = deserialized.dokumentMeta.sakId,
                    tilbakekrevingsbehandlingId = deserialized.dokumentMeta.tilbakekrevingsbehandlingId,
                    journalpostId = deserialized.dokumentMeta.journalpostId,
                    brevbestillingId = deserialized.dokumentMeta.brevbestillingsId,
                ),
                distribusjonstype = deserialized.distribusjonstype.toDomain(),
                distribusjonstidspunkt = deserialized.distribusjonstidspunkt.toDomain(),
                generertDokumentJson = deserialized.generertDokumentJson,
            )

            return when (type) {
                LagretDokument -> toLagretDokumentHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    meta = meta,
                    relaterteHendelser = deserialized.relaterteHendelser.map { HendelseId.fromString(it) }
                        .toNonEmptyList(),
                    dokumentMedMetadataUtenFil = dokumentUtenFil,
                )

                LagretDokumentForJournalføring -> toLagretDokumentForJournalføringHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    meta = meta,
                    relaterteHendelser = deserialized.relaterteHendelser.map { HendelseId.fromString(it) }
                        .toNonEmptyList(),
                    dokumentMedMetadataUtenFil = dokumentUtenFil,
                )

                LagretDokumentForUtsendelse -> toLagretDokumentForUtsendelseHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    meta = meta,
                    relaterteHendelser = deserialized.relaterteHendelser.map { HendelseId.fromString(it) }
                        .toNonEmptyList(),
                    dokumentMedMetadataUtenFil = dokumentUtenFil,
                )

                else -> throw IllegalStateException("Ugyldig type for lagret dokument hendelse. type var $type")
            }
        }

        private fun toLagretDokumentForJournalføringHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
            relaterteHendelser: NonEmptyList<HendelseId>,
            dokumentMedMetadataUtenFil: DokumentMedMetadataUtenFil,
        ): LagretDokumentForJournalføringHendelse = LagretDokumentForJournalføringHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            hendelseMetadata = meta,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relaterteHendelser = relaterteHendelser,
            dokument = dokumentMedMetadataUtenFil,
        )

        private fun toLagretDokumentForUtsendelseHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
            relaterteHendelser: NonEmptyList<HendelseId>,
            dokumentMedMetadataUtenFil: DokumentMedMetadataUtenFil,
        ): LagretDokumentForUtsendelseHendelse = LagretDokumentForUtsendelseHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            hendelseMetadata = meta,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relaterteHendelser = relaterteHendelser,
            dokument = dokumentMedMetadataUtenFil,
        )

        private fun toLagretDokumentHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
            relaterteHendelser: NonEmptyList<HendelseId>,
            dokumentMedMetadataUtenFil: DokumentMedMetadataUtenFil,
        ): LagretDokumentHendelse = LagretDokumentHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            hendelseMetadata = meta,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relaterteHendelser = relaterteHendelser,
            dokument = dokumentMedMetadataUtenFil,
        )

        internal fun DokumentMedMetadataUtenFil.toDbJson(relaterteHendelser: NonEmptyList<HendelseId>): String =
            DokumentHendelseDbJson(
                id = this.id,
                opprettet = this.opprettet,
                distribusjonstype = this.distribusjonstype.toHendelseDbJson(),
                distribusjonstidspunkt = this.distribusjonstidspunkt.toHendelseDbJson(),
                tittel = this.tittel,
                generertDokumentJson = this.generertDokumentJson,
                relaterteHendelser = relaterteHendelser.map { it.toString() },
                dokumentMeta = this.metadata.toHendelseDbJson(),
            ).let { serialize(it) }
    }
}
