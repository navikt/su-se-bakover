package no.nav.su.se.bakover.dokument.infrastructure

import arrow.core.NonEmptyList
import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.LagretDokumentHendelse
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.dokument.infrastructure.DokumentMetaDataDbJson.Companion.toHendelseDbJson
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.Base64
import java.util.UUID

internal data class LagretDokumentHendelseDbJson(
    val id: UUID,
    val opprettet: Tidspunkt,
    val distribusjonstype: DistribusjonstypeDbJson,
    val distribusjonstidspunkt: DistribusjonstidspunktDbJson,
    val tittel: String,
    val generertDokumentJson: String,
    val generertDokument: String,
    val relaterteHendelser: List<String>,
    val dokumentMeta: DokumentMetaDataDbJson,
) {
    companion object {

        fun toDomain(
            data: String,
            hendelseId: HendelseId,
            sakId: UUID,
            hendelsestidspunkt: Tidspunkt,
            versjon: Hendelsesversjon,
            meta: DefaultHendelseMetadata,
        ): LagretDokumentHendelse {
            val deserialized = deserialize<LagretDokumentHendelseDbJson>(data)

            val dokumentMetadata = Dokument.Metadata(
                sakId = deserialized.dokumentMeta.sakId,
                søknadId = null,
                vedtakId = null,
                revurderingId = null,
                klageId = null,
                tilbakekrevingsbehandlingId = deserialized.dokumentMeta.tilbakekrevingsbehandlingId,
                journalpostId = deserialized.dokumentMeta.journalpostId,
                brevbestillingId = deserialized.dokumentMeta.brevbestillingsId,
            )

            val dokument = when (deserialized.distribusjonstype) {
                DistribusjonstypeDbJson.VEDTAK -> Dokument.MedMetadata.Vedtak(
                    utenMetadata = Dokument.UtenMetadata.Vedtak(
                        id = deserialized.id,
                        opprettet = deserialized.opprettet,
                        tittel = deserialized.tittel,
                        generertDokument = PdfA(Base64.getDecoder().decode(deserialized.generertDokument)),
                        generertDokumentJson = deserialized.generertDokumentJson,
                    ),
                    metadata = dokumentMetadata,
                )

                DistribusjonstypeDbJson.VIKTIG -> Dokument.MedMetadata.Informasjon.Viktig(
                    utenMetadata = Dokument.UtenMetadata.Informasjon.Viktig(
                        id = deserialized.id,
                        opprettet = deserialized.opprettet,
                        tittel = deserialized.tittel,
                        generertDokument = PdfA(Base64.getDecoder().decode(deserialized.generertDokument)),
                        generertDokumentJson = deserialized.generertDokumentJson,
                    ),
                    metadata = dokumentMetadata,
                )

                DistribusjonstypeDbJson.ANNET -> Dokument.MedMetadata.Informasjon.Annet(
                    utenMetadata = Dokument.UtenMetadata.Informasjon.Annet(
                        id = deserialized.id,
                        opprettet = deserialized.opprettet,
                        tittel = deserialized.tittel,
                        generertDokument = PdfA(Base64.getDecoder().decode(deserialized.generertDokument)),
                        generertDokumentJson = deserialized.generertDokumentJson,
                    ),
                    metadata = dokumentMetadata,
                )
            }

            return LagretDokumentHendelse.fraPersistert(
                hendelseId = hendelseId,
                hendelsestidspunkt = hendelsestidspunkt,
                hendelseMetadata = meta,
                entitetId = sakId,
                versjon = versjon,
                sakId = sakId,
                relaterteHendelser = deserialized.relaterteHendelser.map { HendelseId.fromString(it) },
                dokument = dokument,
            )
        }

        internal fun Dokument.MedMetadata.toDbJson(relaterteHendelser: NonEmptyList<HendelseId>): String {
            return LagretDokumentHendelseDbJson(
                id = this.id,
                opprettet = this.opprettet,
                distribusjonstype = this.distribusjonstype.toHendelseDbJson(),
                distribusjonstidspunkt = this.distribusjonstidspunkt.toHendelseDbJson(),
                tittel = this.tittel,
                generertDokumentJson = this.generertDokumentJson,
                generertDokument = Base64.getEncoder().encodeToString(this.generertDokument.getContent()),
                relaterteHendelser = relaterteHendelser.map { it.toString() },
                dokumentMeta = this.metadata.toHendelseDbJson(),
            ).let { serialize(it) }
        }
    }
}

internal fun Distribusjonstype.toHendelseDbJson(): DistribusjonstypeDbJson {
    return when (this) {
        Distribusjonstype.VEDTAK -> DistribusjonstypeDbJson.VEDTAK
        Distribusjonstype.VIKTIG -> DistribusjonstypeDbJson.VIKTIG
        Distribusjonstype.ANNET -> DistribusjonstypeDbJson.ANNET
    }
}

internal fun Distribusjonstidspunkt.toHendelseDbJson(): DistribusjonstidspunktDbJson {
    return when (this) {
        Distribusjonstidspunkt.UMIDDELBART -> DistribusjonstidspunktDbJson.UMIDDELBART
        Distribusjonstidspunkt.KJERNETID -> DistribusjonstidspunktDbJson.KJERNETID
    }
}

internal enum class DistribusjonstypeDbJson {
    VEDTAK,
    VIKTIG,
    ANNET,
}

internal enum class DistribusjonstidspunktDbJson {
    UMIDDELBART,
    KJERNETID,
}

/**
 * Per nå støtter kun hendelser for tilbakekreving
 */
data class DokumentMetaDataDbJson(
    val sakId: UUID,
    val tilbakekrevingsbehandlingId: UUID,
    val journalpostId: String?,
    val brevbestillingsId: String?,
) {
    companion object {
        fun Dokument.Metadata.toHendelseDbJson(): DokumentMetaDataDbJson = DokumentMetaDataDbJson(
            sakId = this.sakId,
            tilbakekrevingsbehandlingId = this.tilbakekrevingsbehandlingId!!,
            journalpostId = this.journalpostId,
            brevbestillingsId = this.brevbestillingId,
        )
    }
}
