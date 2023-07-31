package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.dokument.Dokument
import java.util.UUID

fun dokumentUtenMetadataVedtak(
    id: UUID = UUID.randomUUID(),
    pdf: PdfA = pdfATom(),
    tittel: String = "test-dokument-vedtak",
    generertDokumentJson: String = "{}",
    opprettet: Tidspunkt = fixedTidspunkt,
): Dokument.UtenMetadata.Vedtak {
    return Dokument.UtenMetadata.Vedtak(
        id = id,
        opprettet = opprettet,
        tittel = tittel,
        generertDokument = pdf,
        generertDokumentJson = generertDokumentJson,
    )
}

fun dokumentMedMetadataVedtak(
    sakId: UUID = UUID.randomUUID(),
    vedtakId: UUID,
): Dokument.MedMetadata.Vedtak {
    return dokumentUtenMetadataVedtak().leggTilMetadata(
        metadata = Dokument.Metadata(
            sakId = sakId,
            søknadId = null,
            vedtakId = vedtakId,
            revurderingId = null,
            klageId = null,
            journalpostId = null,
            brevbestillingId = null,
        ),
    )
}

fun dokumentUtenMetadataInformasjonAnnet(
    id: UUID = UUID.randomUUID(),
    pdf: PdfA = pdfATom(),
    tittel: String = "test-dokument-informasjon-annet",
    opprettet: Tidspunkt = fixedTidspunkt,
    generertDokumentJson: String = "{}",
): Dokument.UtenMetadata.Informasjon.Annet {
    return Dokument.UtenMetadata.Informasjon.Annet(
        id = id,
        opprettet = opprettet,
        tittel = tittel,
        generertDokument = pdf,
        generertDokumentJson = generertDokumentJson,
    )
}

fun dokumentMedMetadataInformasjonAnnet(
    sakId: UUID = UUID.randomUUID(),
): Dokument.MedMetadata.Informasjon {
    return dokumentUtenMetadataInformasjonAnnet(tittel = "test-dokument-informasjon-annet").leggTilMetadata(
        metadata = Dokument.Metadata(
            sakId = sakId,
            søknadId = null,
            vedtakId = null,
            revurderingId = null,
            klageId = null,
            journalpostId = null,
            brevbestillingId = null,
        ),
    )
}

fun dokumentUtenMetadataInformasjonViktig(
    id: UUID = UUID.randomUUID(),
    pdf: PdfA = pdfATom(),
    tittel: String = "test-dokument-informasjon-viktig",
    generertDokumentJson: String = "{}",
    opprettet: Tidspunkt = fixedTidspunkt,
): Dokument.UtenMetadata.Informasjon.Viktig {
    return Dokument.UtenMetadata.Informasjon.Viktig(
        id = id,
        opprettet = opprettet,
        tittel = tittel,
        generertDokument = pdf,
        generertDokumentJson = generertDokumentJson,
    )
}
