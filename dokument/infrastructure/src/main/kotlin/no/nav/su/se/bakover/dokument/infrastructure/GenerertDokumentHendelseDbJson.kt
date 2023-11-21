package no.nav.su.se.bakover.dokument.infrastructure

import dokument.domain.Dokument
import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.GenerertDokument
import dokument.domain.hendelser.GenerertDokumentHendelse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.DokumentMetaDataDbJson.Companion.toHendelseDbJson
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

internal data class GenerertDokumentHendelseDbJson(
    val id: UUID,
    val opprettet: Tidspunkt,
    val distribusjonstype: DistribusjonstypeDbJson,
    val distribusjonstidspunkt: DistribusjonstidspunktDbJson,
    val tittel: String,
    val generertDokumentJson: String,
    val relaterteHendelse: String,
    val dokumentMeta: DokumentMetaDataDbJson,
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
            meta: DefaultHendelseMetadata,
        ): DokumentHendelse {
            val deserialized = deserialize<GenerertDokumentHendelseDbJson>(data)
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
                GenerertDokument -> toGenerertDokumentHendelse(
                    hendelseId = hendelseId,
                    sakId = sakId,
                    hendelsestidspunkt = hendelsestidspunkt,
                    versjon = versjon,
                    meta = meta,
                    relaterteHendelse = HendelseId.fromString(deserialized.relaterteHendelse),
                    dokumentMedMetadataUtenFil = dokumentUtenFil,
                    skalSendeBrev = deserialized.skalSendeBrev,
                )
                else -> throw IllegalStateException("Ugyldig type for lagret dokument hendelse. type var $type")
            }
        }

        private fun toGenerertDokumentHendelse(
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
            relaterteHendelse: HendelseId,
            dokumentMedMetadataUtenFil: DokumentMedMetadataUtenFil,
            skalSendeBrev: Boolean,
        ): GenerertDokumentHendelse = GenerertDokumentHendelse.fraPersistert(
            hendelseId = hendelseId,
            hendelsestidspunkt = hendelsestidspunkt,
            hendelseMetadata = meta,
            entitetId = sakId,
            versjon = versjon,
            sakId = sakId,
            relatertHendelse = relaterteHendelse,
            dokument = dokumentMedMetadataUtenFil,
            skalSendeBrev = skalSendeBrev,
        )

        internal fun DokumentMedMetadataUtenFil.toDbJson(
            relaterteHendelse: HendelseId,
            skalSendeBrev: Boolean,
        ): String =
            GenerertDokumentHendelseDbJson(
                id = this.id,
                opprettet = this.opprettet,
                distribusjonstype = this.distribusjonstype.toHendelseDbJson(),
                distribusjonstidspunkt = this.distribusjonstidspunkt.toHendelseDbJson(),
                tittel = this.tittel,
                generertDokumentJson = this.generertDokumentJson,
                relaterteHendelse = relaterteHendelse.toString(),
                dokumentMeta = this.metadata.toHendelseDbJson(),
                skalSendeBrev = skalSendeBrev,
            ).let { serialize(it) }
    }
}
