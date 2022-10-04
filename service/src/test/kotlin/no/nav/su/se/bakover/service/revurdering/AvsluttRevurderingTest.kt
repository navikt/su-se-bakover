package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.avsluttetGjenopptakelseAvYtelseeFraIverksattSøknadsbehandlignsvedtak
import no.nav.su.se.bakover.test.avsluttetStansAvYtelseFraIverksattSøknadsbehandlignsvedtak
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattGjenopptakelseAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.iverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class AvsluttRevurderingTest {

    @Test
    fun `avslutter en revurdering som ikke skal bli forhåndsvarslet`() {
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn opprettetRevurdering
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe opprettetRevurdering.id })
        verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe opprettetRevurdering.oppgaveId })
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() }, anyOrNull())
        verifyNoMoreInteractions(revurderingRepoMock, oppgaveServiceMock)
    }

    @Test
    fun `avslutter en revurdering som er blitt forhåndsvarslet (med fritekst)`() {
        val simulert = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second.copy(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulert
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn Dokument.UtenMetadata.Informasjon.Annet(
                opprettet = fixedTidspunkt,
                tittel = "tittel1",
                generertDokument = "brev".toByteArray(),
                generertDokumentJson = "brev",
            ).right()
        }
        val sessionFactoryMock = TestSessionFactory()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            sessionFactory = sessionFactoryMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = simulert.id,
            begrunnelse = "opprettet revurderingen med en feil",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("en fri tekst"),
            saksbehandler = saksbehandler,
        )

        val expectedAvsluttetRevurdering = AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulert,
            begrunnelse = "opprettet revurderingen med en feil",
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("en fri tekst"),
            tidspunktAvsluttet = fixedTidspunkt,
        )
        actual shouldBe expectedAvsluttetRevurdering

        verify(revurderingRepoMock).hent(argThat { it shouldBe simulert.id })
        verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe simulert.oppgaveId })
        verify(brevServiceMock).lagDokument(
            argThat<Visitable<LagBrevRequestVisitor>> {
                it shouldBe expectedAvsluttetRevurdering.getOrFail()
            },
        )
        verify(brevServiceMock).lagreDokument(
            argThat {
                it should beOfType<Dokument.MedMetadata.Informasjon.Annet>()
                it.generertDokument shouldBe "brev".toByteArray()
                it.metadata shouldBe Dokument.Metadata(
                    sakId = simulert.sakId,
                    revurderingId = simulert.id,
                    bestillBrev = true,
                )
            },
        )
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(argThat { it shouldBe actual.getOrFail() }, anyOrNull())
        verifyNoMoreInteractions(revurderingRepoMock, oppgaveServiceMock, brevServiceMock)
    }

    @Test
    fun `får feil dersom man prøver å avslutte en revurdering man ikke finner`() {
        val id = UUID.randomUUID()
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn null
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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
        val simulert = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second.copy(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulert
        }
        val brevServiceMock = mock<BrevService> {
            on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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
            argThat<Visitable<LagBrevRequestVisitor>> {
                it shouldBe AvsluttetRevurdering.tryCreate(
                    underliggendeRevurdering = simulert,
                    begrunnelse = "begrunnelse",
                    brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("medFritekst"),
                    tidspunktAvsluttet = fixedTidspunkt,
                ).getOrFail()
            },
        )
        verifyNoMoreInteractions(revurderingRepoMock, brevServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `avslutter en stansAvYtelse-revurdering`() {
        val stansAvYtelse = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn stansAvYtelse
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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
        val gjenopptaYtelse = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn gjenopptaYtelse
        }

        val revurderingService = RevurderingTestUtils.createRevurderingService(
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

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingErAlleredeAvsluttet)
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

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        )

        val actual = revurderingService.avsluttRevurdering(
            revurderingId = gjenopptaYtelse.id,
            begrunnelse = "skulle stanse ytelse, men så tenkte jeg 'neh'",
            brevvalg = null,
            saksbehandler = saksbehandler,
        )

        actual shouldBe KunneIkkeAvslutteRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse(GjenopptaYtelseRevurdering.KunneIkkeLageAvsluttetGjenopptaAvYtelse.RevurderingenErIverksatt)
            .left()
        verify(revurderingRepoMock).hent(argThat { it shouldBe gjenopptaYtelse.id })
        verifyNoMoreInteractions(revurderingRepoMock)
    }
}
