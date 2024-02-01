package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import dokument.domain.KunneIkkeLageDokument
import dokument.domain.brev.BrevService
import dokument.domain.brev.Brevvalg
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.brev.command.AvsluttRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeLageAvsluttetGjenopptaAvYtelse
import no.nav.su.se.bakover.domain.revurdering.opphør.AnnullerKontrollsamtaleVedOpphørService
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.service.tilbakekreving.TilbakekrevingUnderRevurderingService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak
import no.nav.su.se.bakover.test.avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulertGjenopptakAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.PersonService
import satser.domain.SatsFactory
import java.time.Clock
import java.util.UUID

internal class AvsluttGrunnlagsdataOgVilkårsvurderingerRevurderingTest {

    @Test
    fun `avslutter en revurdering som ikke skal bli forhåndsvarslet`() {
        val opprettetRevurdering = opprettetRevurdering().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            oppgaveService = oppgaveServiceMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = opprettetRevurdering.id,
            begrunnelse = "opprettet revurderingen med en feil",
            brevvalg = null,
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
        )

        actual shouldBe AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = opprettetRevurdering,
            begrunnelse = "opprettet revurderingen med en feil",
            brevvalg = null,
            tidspunktAvsluttet = fixedTidspunkt,
            avsluttetAv = saksbehandler,
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe opprettetRevurdering.id })
        verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe opprettetRevurdering.oppgaveId })
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() }, anyOrNull())
        verifyNoMoreInteractions(revurderingRepoMock, oppgaveServiceMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en revurdering man ikke finner`() {
        val id = UUID.randomUUID()
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        revurderingService.avsluttRevurdering(
            revurderingId = id,
            begrunnelse = "hehe",
            brevvalg = null,
            saksbehandler = saksbehandler,

        ) shouldBe KunneIkkeAvslutteRevurdering.FantIkkeRevurdering.left()

        verify(revurderingRepoMock).hent(argThat { it shouldBe id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom generering av brev feiler når man avslutter revurdering`() {
        val simulert = simulertRevurdering().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulert
        }
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
        )

        revurderingService.avsluttRevurdering(
            revurderingId = simulert.id,
            begrunnelse = "begrunnelse",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("medFritekst"),
            saksbehandler = saksbehandler,
        ) shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageDokument.left()

        verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe simulert.oppgaveId })
        verify(revurderingRepoMock).hent(argThat { it shouldBe simulert.id })
        verify(brevServiceMock).lagDokument(
            argThat {
                it shouldBe AvsluttRevurderingDokumentCommand(fødselsnummer = simulert.fnr, saksnummer = Saksnummer(12345676), saksbehandler = saksbehandler, fritekst = "medFritekst")
            },
            anyOrNull(),
        )
        verifyNoMoreInteractions(revurderingRepoMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `avslutter en stansAvYtelse-revurdering`() {
        val stansAvYtelse = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn stansAvYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = stansAvYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe StansAvYtelseRevurdering.AvsluttetStansAvYtelse.tryCreate(
            stansAvYtelseRevurdering = stansAvYtelse,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            tidspunktAvsluttet = fixedTidspunkt,
            avsluttetAv = saksbehandler,
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe stansAvYtelse.id })
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() }, anyOrNull())
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en stansAvYtelse-revurdering som allerede er avsluttet`() {
        val avsluttetStansAvYtelse = avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak().second
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn avsluttetStansAvYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = avsluttetStansAvYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse(StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingErAlleredeAvsluttet)
            .left()

        verify(revurderingRepoMock).hent(argThat { it shouldBe avsluttetStansAvYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en stansAvYtelse-revurdering som er iverksatt`() {
        val iverksattStansAvYtelse = iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn iverksattStansAvYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = iverksattStansAvYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetStansAvYtelse(StansAvYtelseRevurdering.KunneIkkeLageAvsluttetStansAvYtelse.RevurderingenErIverksatt)
            .left()

        verify(revurderingRepoMock).hent(argThat { it shouldBe iverksattStansAvYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `avslutter en gjenoppta-revurdering`() {
        val gjenopptaYtelse = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn gjenopptaYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe GjenopptaYtelseRevurdering.AvsluttetGjenoppta.tryCreate(
            gjenopptakAvYtelseRevurdering = gjenopptaYtelse,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            tidspunktAvsluttet = fixedTidspunkt,
            avsluttetAv = saksbehandler,
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe gjenopptaYtelse.id })
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() }, anyOrNull())
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en gjenoppta-revurdering som er avlusttet`() {
        val gjenopptaYtelse = avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn gjenopptaYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(
            KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingErAlleredeAvsluttet,
        )
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe gjenopptaYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en gjenoppta-revurdering som er iverksatt`() {
        val gjenopptaYtelse = iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn gjenopptaYtelse
        }

        val revurderingService = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(
            KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingenErIverksatt,
        )
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe gjenopptaYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    private fun createRevurderingService(
        utbetalingService: UtbetalingService = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        brevService: BrevService = mock(),
        clock: Clock = fixedClock,
        vedtakService: VedtakService = mock(),
        sakService: SakService = mock(),
        annullerKontrollsamtaleService: AnnullerKontrollsamtaleVedOpphørService = mock(),
        sessionFactory: SessionFactory = TestSessionFactory(),
        tilbakekrevingService: TilbakekrevingUnderRevurderingService = mock(),
        satsFactory: SatsFactory = satsFactoryTestPåDato(),
    ) =
        RevurderingServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            brevService = brevService,
            clock = clock,
            vedtakService = vedtakService,
            annullerKontrollsamtaleService = annullerKontrollsamtaleService,
            sessionFactory = sessionFactory,
            formuegrenserFactory = formuegrenserFactoryTestPåDato(),
            sakService = sakService,
            tilbakekrevingService = tilbakekrevingService,
            satsFactory = satsFactory,
        )
}
