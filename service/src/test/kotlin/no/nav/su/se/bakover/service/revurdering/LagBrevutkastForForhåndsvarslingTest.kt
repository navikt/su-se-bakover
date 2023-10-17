package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.jsonRequest.FeilVedHentingAvInformasjon
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

class LagBrevutkastForForhåndsvarslingTest {

    @Test
    fun `lager brevutkast for forhåndsvarsel - saksbehandler som lager utkastet, er ikke den som har gjort behandlingen`() {
        val simulertRevurdering = simulertRevurdering().second
        val saksbehandlerSomLagerBrev = NavIdentBruker.Saksbehandler("saksbehandlerSomLagerBrevet")
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn dokumentUtenMetadataVedtak(pdf = PdfA("brevbytes".toByteArray())).right()
            },
        ).let {
            simulertRevurdering.saksbehandler shouldNotBe saksbehandlerSomLagerBrev
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                simulertRevurdering.id,
                saksbehandlerSomLagerBrev,
                "saksbehandler og saksbehandler som lager brev er ikke de samme",
            ).shouldBeRight()
            verify(it.revurderingRepo).hent(simulertRevurdering.id)
            verify(it.brevService).lagDokument(any(), anyOrNull())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom revurderingen ikke finnes`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                UUID.randomUUID(),
                saksbehandler,
                "fritekst til forhåndsvarsling",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkeRevurdering.left()
        }
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi ikke finner personen knyttet til revurderingen`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering().second
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                UUID.randomUUID(),
                saksbehandler,
                "fritekst til forhåndsvarsling",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
            ).left()
        }
    }

    @Test
    fun `lag brevutkast for forhåndsvarsling feiler dersom vi ikke klarer lage brevet`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering().second
            },

            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
            },
            identClient = mock {
                on { hentNavnForNavIdent(any()) } doReturn saksbehandler.navIdent.right()
            },
        ).let {
            it.revurderingService.lagBrevutkastForForhåndsvarsling(
                UUID.randomUUID(),
                saksbehandler,
                "fritekst til forhåndsvarsling",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedGenereringAvPdf,
            ).left()
        }
    }
}
