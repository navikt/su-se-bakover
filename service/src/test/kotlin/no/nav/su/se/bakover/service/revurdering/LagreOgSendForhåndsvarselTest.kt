package no.nav.su.se.bakover.service.revurdering

import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.mottaker.MottakerDokumentkontekst
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.test.dokumentUtenMetadataInformasjonViktig
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.nyDistribueringsAdresse
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurdering
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

internal class LagreOgSendForhåndsvarselTest {

    @Test
    fun `lagreOgSendForhandsvarsel henter mottaker med FORHANDSVARSEL og lagrer original og kopi`() {
        val (sak, revurdering) = simulertRevurdering()
        val dokumentUtenMetadata = dokumentUtenMetadataInformasjonViktig()

        val ekstraMottaker = MottakerFnrDomain(
            navn = "Ekstra Mottaker",
            foedselsnummer = Fnr.generer(),
            adresse = nyDistribueringsAdresse(),
            sakId = sak.id,
            referanseId = revurdering.id.value,
            referanseType = ReferanseTypeMottaker.REVURDERING,
            brevtype = MottakerDokumentkontekst.FORHANDSVARSEL,
        )

        val brevService = mock<BrevService> {
            on { lagDokumentPdf(any(), anyOrNull()) } doReturn dokumentUtenMetadata.right()
        }
        val revurderingRepo = mock<RevurderingRepo> {
            on { hent(revurdering.id) } doReturn revurdering
        }
        val sakService = mock<SakService> {
            on { hentSakForRevurdering(revurdering.id) } doReturn sak
        }
        val oppgaveService = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
        }
        val mottakerService = mock<MottakerService> {
            on { hentMottaker(any(), any(), anyOrNull()) } doReturn ekstraMottaker.right()
        }

        val mocks = RevurderingServiceMocks(
            brevService = brevService,
            revurderingRepo = revurderingRepo,
            sakService = sakService,
            oppgaveService = oppgaveService,
            mottakerService = mottakerService,
        )

        mocks.revurderingService.lagreOgSendForhåndsvarsel(
            revurderingId = revurdering.id,
            utførtAv = saksbehandler,
            fritekst = "fritekst",
        ).shouldBeRight(revurdering)

        verify(mottakerService).hentMottaker(
            argThat {
                referanseType == ReferanseTypeMottaker.REVURDERING &&
                    referanseId == revurdering.id.value &&
                    brevtype == MottakerDokumentkontekst.FORHANDSVARSEL
            },
            eq(sak.id),
            anyOrNull(),
        )

        val dokumentCaptor = argumentCaptor<Dokument.MedMetadata>()
        verify(brevService, times(2)).lagreDokument(dokumentCaptor.capture(), anyOrNull())

        val lagredeForhåndsvarsler = dokumentCaptor.allValues.filterIsInstance<Dokument.MedMetadata.Informasjon.Viktig>()
        lagredeForhåndsvarsler.shouldHaveSize(2)

        val kopi = lagredeForhåndsvarsler.single { it.erKopi }
        val original = lagredeForhåndsvarsler.single { !it.erKopi }

        original.ekstraMottaker shouldBe null
        kopi.ekstraMottaker shouldBe ekstraMottaker.foedselsnummer.toString()
        kopi.navnEkstraMottaker shouldBe ekstraMottaker.navn
        kopi.distribueringsadresse shouldBe ekstraMottaker.adresse
    }
}
