package no.nav.su.se.bakover.service.klage

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class KlagevedtakServiceImplTest {

    @Test
    fun `når deserializering feiler så markerer vi vedtaket med FEIL`() {
        val id = UUID.randomUUID()
        val feilFormatertVedtak = UprosessertFattetKlagevedtak(
            id = id,
            opprettet = fixedTidspunkt,
            metadata = UprosessertFattetKlagevedtak.Metadata(
                hendelseId = "55",
                offset = 0,
                partisjon = 0,
                key = "",
                value = ""
            )
        )

        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(feilFormatertVedtak)
        }

        buildKlagevedtakService(klagevedtakRepoMock).håndterUtfallFraKlageinstans()
        verify(klagevedtakRepoMock).markerSomFeil(argThat { it shouldBe id })
    }

    @Test
    fun `Stadfestelse setter vedtaket som prosessert`() {
        val id = UUID.randomUUID()
        val stadfestetVedtak = UprosessertFattetKlagevedtak(
            id = id,
            opprettet = fixedTidspunkt,
            metadata = UprosessertFattetKlagevedtak.Metadata(
                hendelseId = "55",
                offset = 0,
                partisjon = 0,
                key = "key",
                value = gyldigMelding(utfall = "STADFESTELSE")
            )
        )

        val klagevedtakRepoMock: KlagevedtakRepo = mock {
            on { hentUbehandlaKlagevedtak() } doReturn listOf(stadfestetVedtak)
        }

        buildKlagevedtakService(klagevedtakRepoMock).håndterUtfallFraKlageinstans()
        verify(klagevedtakRepoMock).hentUbehandlaKlagevedtak()
        verify(klagevedtakRepoMock).markerSomProssesert(argThat { it shouldBe id })
    }

    private fun gyldigMelding(utfall: String, eventId: UUID = UUID.randomUUID()) = """
        {
          "eventId": "$eventId",
          "kildeReferanse": 2021,
          "kilde": "SUPSTONAD",
          "utfall": "$utfall",
          "vedtaksbrevReferanse": "${UUID.randomUUID()}",
          "kabalReferanse": "${UUID.randomUUID()}"
        }
    """.trimIndent()

    private fun buildKlagevedtakService(
        klagevedtakRepo: KlagevedtakRepo = mock(),
        oppgaveService: OppgaveService = mock(),
    ): KlagevedtakService {
        return KlagevedtakServiceImpl(
            klagevedtakRepo = klagevedtakRepo,
            oppgaveService = oppgaveService,
        )
    }
}
