package no.nav.su.se.bakover.service.notat

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.antivirus.VirusScanService
import no.nav.su.se.bakover.domain.antivirus.VirusScanServiceMock
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatFeil
import no.nav.su.se.bakover.domain.notat.NotatHandling
import no.nav.su.se.bakover.domain.notat.NotatRepo
import no.nav.su.se.bakover.domain.notat.NotatSaksbehandler
import no.nav.su.se.bakover.domain.notat.VedleggRepo
import no.nav.su.se.bakover.domain.sak.SakService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal class NotatServiceTest {

    private val clock = Clock.fixed(Instant.parse("2026-06-24T10:15:30Z"), ZoneOffset.UTC)
    private val sakId = UUID.randomUUID()
    private val saksbehandler = NavIdentBruker.Saksbehandler("Z123456")

    @Test
    fun `leggTilVedlegg avviser ugyldig mime type`() {
        val notatRepo = mock<NotatRepo>()
        val vedleggRepo = mock<VedleggRepo>()
        val service = NotatServiceImpl(
            notatRepo = notatRepo,
            vedleggRepo = vedleggRepo,
            sakService = sakServiceSomFinnerSak(),
            virusScanService = VirusScanServiceMock(),
        )

        service.leggTilVedlegg(
            sakId = sakId,
            notatId = UUID.randomUUID(),
            filnavn = "notat.gif",
            mimeType = "image/gif",
            innhold = "innhold".toByteArray(),
            saksbehandler = saksbehandler,
            clock = clock,
        ).shouldBeLeft(NotatFeil.UgyldigMimeType)

        verifyNoInteractions(notatRepo, vedleggRepo)
    }

    @Test
    fun `matcherFilnavnMimeType returnerer true for gyldige kombinasjoner`() {
        matcherFilnavnMimeType("fil.pdf", "application/pdf") shouldBe true
        matcherFilnavnMimeType("fil.png", "image/png") shouldBe true
        matcherFilnavnMimeType("fil.jpg", "image/jpeg") shouldBe true
        matcherFilnavnMimeType("fil.jpeg", "image/jpeg") shouldBe true
    }

    @Test
    fun `matcherFilnavnMimeType returnerer false for ugyldige kombinasjoner`() {
        matcherFilnavnMimeType("fil.pdf", "image/png") shouldBe false
        matcherFilnavnMimeType("fil", "application/pdf") shouldBe false
        matcherFilnavnMimeType("fil.gif", "image/gif") shouldBe false
    }

    @Test
    fun `leggTilVedlegg avviser mime type som ikke matcher filnavn`() {
        val notatRepo = mock<NotatRepo>()
        val vedleggRepo = mock<VedleggRepo>()
        val service = NotatServiceImpl(
            notatRepo = notatRepo,
            vedleggRepo = vedleggRepo,
            sakService = sakServiceSomFinnerSak(),
            virusScanService = VirusScanServiceMock(),
        )

        service.leggTilVedlegg(
            sakId = sakId,
            notatId = UUID.randomUUID(),
            filnavn = "notat.pdf",
            mimeType = "image/png",
            innhold = "innhold".toByteArray(),
            saksbehandler = saksbehandler,
            clock = clock,
        ).shouldBeLeft(NotatFeil.MimeTypeMatcherIkkeFilnavn)

        verifyNoInteractions(notatRepo, vedleggRepo)
    }

    @Test
    fun `leggTilVedlegg avviser filer over 20 mb`() {
        val notat = lagNotat()
        val notatRepo = mock<NotatRepo> {
            on { hent(notat.id) } doReturn notat
        }
        val vedleggRepo = mock<VedleggRepo>()
        val service = NotatServiceImpl(
            notatRepo = notatRepo,
            vedleggRepo = vedleggRepo,
            sakService = sakServiceSomFinnerSak(),
            virusScanService = VirusScanServiceMock(),
        )

        service.leggTilVedlegg(
            sakId = sakId,
            notatId = notat.id,
            filnavn = "stor.pdf",
            mimeType = "application/pdf",
            innhold = ByteArray(NotatServiceImpl.MAKS_VEDLEGG_STORRELSE_BYTES + 1),
            saksbehandler = saksbehandler,
            clock = clock,
        ).shouldBeLeft(NotatFeil.FilForStor)

        verifyNoInteractions(vedleggRepo)
    }

    @Test
    fun `legg til vedlegg fungerer under 20mb`() {
        val notat = lagNotat()
        val notatRepo = mock<NotatRepo> {
            on { hent(notat.id) } doReturn notat
        }
        val vedleggRepo = mock<VedleggRepo>()
        val service = NotatServiceImpl(
            notatRepo = notatRepo,
            vedleggRepo = vedleggRepo,
            sakService = sakServiceSomFinnerSak(),
            virusScanService = VirusScanServiceMock(),
        )

        val enmegabyte = 1 * 1024 * 1024
        service.leggTilVedlegg(
            sakId = sakId,
            notatId = notat.id,
            filnavn = "stor.pdf",
            mimeType = "application/pdf",
            innhold = ByteArray(enmegabyte),
            saksbehandler = saksbehandler,
            clock = clock,
        ).shouldBeRight()

        verify(vedleggRepo, times(1)).leggTil(any())
    }

    @Test
    fun `lagrer ingenting om virusscan finner virus`() {
        val notat = lagNotat()
        val notatRepo = mock<NotatRepo> {
            on { hent(notat.id) } doReturn notat
        }
        val vedleggRepo = mock<VedleggRepo>()
        val virusService = mock<VirusScanService> {
            on { scan(any()) } doThrow IllegalArgumentException("Virus funnet")
        }
        val service = NotatServiceImpl(
            notatRepo = notatRepo,
            vedleggRepo = vedleggRepo,
            sakService = sakServiceSomFinnerSak(),
            virusScanService = virusService,
        )

        val enmegabyte = 1 * 1024 * 1024

        assertThrows<IllegalArgumentException> {
            service.leggTilVedlegg(
                sakId = sakId,
                notatId = notat.id,
                filnavn = "stor.pdf",
                mimeType = "application/pdf",
                innhold = ByteArray(enmegabyte),
                saksbehandler = saksbehandler,
                clock = clock,
            )
        }

        verifyNoInteractions(vedleggRepo)
    }

    @Test
    fun `oppdaterNotat appender saksbehandlerhistorikk`() {
        val eksisterende = lagNotat()
        val notatRepo = mock<NotatRepo> {
            on { hent(eksisterende.id) } doReturn eksisterende
        }
        val vedleggRepo = mock<VedleggRepo>()
        val service = NotatServiceImpl(
            notatRepo = notatRepo,
            vedleggRepo = vedleggRepo,
            sakService = sakServiceSomFinnerSak(),
            virusScanService = VirusScanServiceMock(),
        )

        val resultat = service.oppdaterNotat(
            sakId = sakId,
            notatId = eksisterende.id,
            notat = "Oppdatert notat",
            saksbehandler = saksbehandler,
            clock = clock,
        ).shouldBeRight()

        resultat.saksbehandler.size shouldBe 2
        resultat.saksbehandler.last().handling shouldBe NotatHandling.OPPDATERT
        resultat.saksbehandler.last().navIdent shouldBe saksbehandler
        verify(notatRepo).oppdater(
            argThat {
                saksbehandler.size == 2 &&
                    saksbehandler.last().handling == NotatHandling.OPPDATERT &&
                    saksbehandler.last().navIdent == NavIdentBruker.Saksbehandler("Z123456")
            },
        )
    }

    private fun sakServiceSomFinnerSak(): SakService =
        mock {
            on { hentSakInfo(any()) } doReturn mock<SakInfo>().right()
        }

    private fun lagNotat() = Notat(
        id = UUID.randomUUID(),
        sakId = sakId,
        referanseId = UUID.randomUUID(),
        notat = "Originalt notat",
        opprettet = Tidspunkt.now(clock),
        endret = Tidspunkt.now(clock),
        saksbehandler = listOf(
            NotatSaksbehandler(
                navIdent = NavIdentBruker.Saksbehandler("Z654321"),
                tidspunkt = Tidspunkt.now(clock),
                handling = NotatHandling.OPPRETTET,
            ),
        ),
    )
}
