package no.nav.su.se.bakover.test

import dokument.domain.Distribusjonstidspunkt
import dokument.domain.Distribusjonstype
import dokument.domain.Dokument
import dokument.domain.DokumentMedMetadataUtenFil
import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.util.UUID

fun dokumentUtenMetadataVedtak(
    id: UUID = UUID.randomUUID(),
    pdf: PdfA = minimumPdfAzeroPadded(),
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
    distribueringsadresse: Distribueringsadresse? = null,
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
        distribueringsadresse = distribueringsadresse,
    )
}

fun dokumentUtenMetadataInformasjonAnnet(
    id: UUID = UUID.randomUUID(),
    pdf: PdfA = minimumPdfAzeroPadded(),
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
    id: UUID = UUID.randomUUID(),
    pdf: PdfA = PdfA("PdfA-dokumentMedMetadataInformasjonAnnet".toByteArray()),
    sakId: UUID = UUID.randomUUID(),
    distribueringsadresse: Distribueringsadresse? = null,
): Dokument.MedMetadata.Informasjon {
    return dokumentUtenMetadataInformasjonAnnet(
        id = id,
        pdf = pdf,
        tittel = "test-dokument-informasjon-annet",
    ).leggTilMetadata(
        metadata = Dokument.Metadata(
            sakId = sakId,
            søknadId = null,
            vedtakId = null,
            revurderingId = null,
            klageId = null,
            journalpostId = null,
            brevbestillingId = null,
        ),
        distribueringsadresse = distribueringsadresse,
    )
}

fun dokumentUtenMetadataInformasjonViktig(
    id: UUID = UUID.randomUUID(),
    pdf: PdfA = minimumPdfAzeroPadded(),
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

fun dokumentUtenFil(
    id: UUID = UUID.randomUUID(),
    opprettet: Tidspunkt = fixedTidspunkt,
    tittel: String = "Dokument-tittel",
    metadata: Dokument.Metadata = Dokument.Metadata(
        sakId = UUID.randomUUID(),
    ),
    distribusjonstype: Distribusjonstype = Distribusjonstype.VEDTAK,
    distribusjonstidspunkt: Distribusjonstidspunkt = Distribusjonstidspunkt.KJERNETID,
    distribueringsadresse: Distribueringsadresse? = null,
    generertDokumentJson: String = "{}",
): DokumentMedMetadataUtenFil = DokumentMedMetadataUtenFil(
    id = id,
    opprettet = opprettet,
    tittel = tittel,
    metadata = metadata,
    distribusjonstype = distribusjonstype,
    distribusjonstidspunkt = distribusjonstidspunkt,
    distribueringsadresse = distribueringsadresse,
    generertDokumentJson = generertDokumentJson,
)

fun nyDistribueringsAdresse(
    adresselinje1: String? = "Goldshire Inn",
    adresselinje2: String? = "Elwynn Forest",
    adresselinje3: String? = null,
    postnummer: String = "123",
    poststed: String = "Elwynn",
): Distribueringsadresse = Distribueringsadresse(
    adresselinje1 = adresselinje1,
    adresselinje2 = adresselinje2,
    adresselinje3 = adresselinje3,
    postnummer = postnummer,
    poststed = poststed,
)
