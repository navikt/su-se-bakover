package no.nav.su.se.bakover.service.klage

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.journalføring.JournalpostMedDokumenter
import dokument.domain.journalføring.QueryJournalpostClient
import dokument.domain.journalføring.Utsendingsinfo
import dokument.domain.journalføring.Utsendingskanal
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageRepo
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonAnnet
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class JournalpostAdresseServiceImplTest {

    @Test
    fun `returnerer FantIkkeDokument naar dokument ikke finnes`() {
        runBlocking {
            val dokumentId = UUID.randomUUID()
            val dokumentRepo = mock<DokumentRepo> {
                on { hentDokument(dokumentId) } doReturn null
            }
            val service = buildService(dokumentRepo = dokumentRepo)

            service.hentAdresseForDokumentIdForInterneDokumenter(
                dokumentId = dokumentId,
                journalpostId = JournalpostId("jp1"),
            ) shouldBe AdresseServiceFeil.FantIkkeDokument.left()
        }
    }

    @Test
    fun `returnerer ErIkkeJournalfoert naar dokumentet ikke er journalfoert`() {
        runBlocking {
            val dokumentId = UUID.randomUUID()
            val dokument = dokumentMedMetadata(
                dokumentId = dokumentId,
                journalpostId = null,
                brevbestillingId = null,
            )
            val dokumentRepo = mock<DokumentRepo> {
                on { hentDokument(dokumentId) } doReturn dokument
            }
            val service = buildService(dokumentRepo = dokumentRepo)

            service.hentAdresseForDokumentIdForInterneDokumenter(
                dokumentId = dokumentId,
                journalpostId = JournalpostId("jp1"),
            ) shouldBe AdresseServiceFeil.ErIkkeJournalført.left()
        }
    }

    @Test
    fun `returnerer JournalpostIkkeKnyttetTilDokument naar journalpostId ikke matcher`() {
        runBlocking {
            val dokumentId = UUID.randomUUID()
            val dokument = dokumentMedMetadata(
                dokumentId = dokumentId,
                journalpostId = "jp1",
                brevbestillingId = "brev1",
            )
            val dokumentRepo = mock<DokumentRepo> {
                on { hentDokument(dokumentId) } doReturn dokument
            }
            val service = buildService(dokumentRepo = dokumentRepo)

            service.hentAdresseForDokumentIdForInterneDokumenter(
                dokumentId = dokumentId,
                journalpostId = JournalpostId("jp2"),
            ) shouldBe AdresseServiceFeil.JournalpostIkkeKnyttetTilDokument.left()
        }
    }

    @Test
    fun `returnerer JournalpostManglerBrevbestilling naar brevbestillingId mangler`() {
        runBlocking {
            val dokumentId = UUID.randomUUID()
            val dokument = dokumentMedMetadata(
                dokumentId = dokumentId,
                journalpostId = "jp1",
                brevbestillingId = null,
            )
            val dokumentRepo = mock<DokumentRepo> {
                on { hentDokument(dokumentId) } doReturn dokument
            }
            val service = buildService(dokumentRepo = dokumentRepo)

            service.hentAdresseForDokumentIdForInterneDokumenter(
                dokumentId = dokumentId,
                journalpostId = JournalpostId("jp1"),
            ) shouldBe AdresseServiceFeil.JournalpostManglerBrevbestilling.left()
        }
    }

    @Test
    fun `returnerer utsendingsinfo i happy case`() {
        runBlocking {
            val dokumentId = UUID.randomUUID()
            val journalpostId = JournalpostId("jp1")
            val dokument = dokumentMedMetadata(
                dokumentId = dokumentId,
                journalpostId = journalpostId.toString(),
                brevbestillingId = "brev1",
            )
            val dokumentRepo = mock<DokumentRepo> {
                on { hentDokument(dokumentId) } doReturn dokument
            }
            val utsendingsinfo = Utsendingsinfo(
                fysiskpostSendt = "2024-01-01",
                digitalpostSendt = false,
                varselSendt = emptyList(),
                prioritertKanal = Utsendingskanal.FYSISKPOST,
            )
            val journalpost = JournalpostMedDokumenter(
                journalpostId = journalpostId,
                tittel = null,
                datoOpprettet = null,
                utsendingsinfo = utsendingsinfo,
                dokumenter = emptyList(),
            )
            val journalpostClient = mock<QueryJournalpostClient> {
                on { runBlocking { hentJournalpostMedDokumenter(journalpostId) } } doReturn journalpost.right()
            }
            val service = buildService(
                dokumentRepo = dokumentRepo,
                journalpostClient = journalpostClient,
            )

            service.hentAdresseForDokumentIdForInterneDokumenter(
                dokumentId = dokumentId,
                journalpostId = journalpostId,
            ) shouldBe DokumentUtsendingsinfo(utsendingsinfo).right()
        }
    }

    private fun dokumentMedMetadata(
        dokumentId: UUID,
        journalpostId: String?,
        brevbestillingId: String?,
    ): Dokument.MedMetadata {
        return dokumentUtenMetadataInformasjonAnnet(id = dokumentId).leggTilMetadata(
            metadata = Dokument.Metadata(
                sakId = UUID.randomUUID(),
                journalpostId = journalpostId,
                brevbestillingId = brevbestillingId,
            ),
            distribueringsadresse = null,
        )
    }

    private fun buildService(
        klageRepo: KlageRepo = mock(),
        dokumentRepo: DokumentRepo = mock(),
        journalpostClient: QueryJournalpostClient = mock {
            on { runBlocking { hentJournalpostMedDokumenter(any()) } } doReturn JournalpostMedDokumenter(
                journalpostId = JournalpostId("jp-default"),
                tittel = null,
                datoOpprettet = null,
                utsendingsinfo = null,
                dokumenter = emptyList(),
            ).right()
        },
    ): JournalpostAdresseServiceImpl {
        return JournalpostAdresseServiceImpl(
            klageRepo = klageRepo,
            journalpostClient = journalpostClient,
            dokumentRepo = dokumentRepo,
        )
    }
}
