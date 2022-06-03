package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.domain.dokument.Dokument
import java.util.UUID

fun dokumentUtenMetadataVedtak(id: UUID = UUID.randomUUID()) = Dokument.UtenMetadata.Vedtak(
    id = id,
    opprettet = fixedTidspunkt,
    tittel = "tittel1",
    generertDokument = "".toByteArray(),
    generertDokumentJson = "",
)

fun dokumetMedMetadataVedtak(
    sakId: UUID = UUID.randomUUID(),
    bestillBrev: Boolean = true,
) = dokumentUtenMetadataVedtak().leggTilMetadata(
    metadata = Dokument.Metadata(
        sakId = sakId,
        bestillBrev = bestillBrev,
        søknadId = null,
        vedtakId = null,
        revurderingId = null,
        klageId = null,
        journalpostId = null,
        brevbestillingId = null,
    ),
)

fun dokumentUtenMetadataInformasjon(id: UUID = UUID.randomUUID()) = Dokument.UtenMetadata.Informasjon.Annet(
    id = id,
    opprettet = fixedTidspunkt,
    tittel = "tittel1",
    generertDokument = "".toByteArray(),
    generertDokumentJson = "",
)

fun dokumetMedMetadataInformasjon(
    sakId: UUID = UUID.randomUUID(),
    bestillBrev: Boolean = true,
): Dokument.MedMetadata.Informasjon = dokumentUtenMetadataInformasjon().leggTilMetadata(
    metadata = Dokument.Metadata(
        sakId = sakId,
        bestillBrev = bestillBrev,
        søknadId = null,
        vedtakId = null,
        revurderingId = null,
        klageId = null,
        journalpostId = null,
        brevbestillingId = null,
    ),
)
