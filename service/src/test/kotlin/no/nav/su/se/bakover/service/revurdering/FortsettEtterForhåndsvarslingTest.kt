package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.AvsluttetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class FortsettEtterForhåndsvarslingTest {

    @Test
    fun `fortsett med andre opplysninger etter forhåndsvarsling`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
        ).second

        val mocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
        )
        mocks.revurderingService.fortsettEtterForhåndsvarsling(
            FortsettEtterForhåndsvarslingRequest.FortsettMedAndreOpplysninger(
                revurderingId = simulertRevurdering.id,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomBesluttetForhåndsvarsling"),
                begrunnelse = "begrunnelse",
            ),
        ) shouldBe simulertRevurdering.copy(
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("begrunnelse"),
        ).right()
        verify(mocks.revurderingRepo).hent(argThat { it shouldBe simulertRevurdering.id })
        verify(mocks.revurderingRepo).defaultTransactionContext()
        verify(mocks.revurderingRepo).lagre(
            argThat {
                it shouldBe simulertRevurdering.copy(
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("begrunnelse"),
                )
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `fortsett med samme opplysninger etter forhåndsvarsling`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
            revurderingsperiode = Periode.create(1.juli(2021), 31.desember(2021))
        ).second

        val mocks = RevurderingServiceMocks(
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
        )
        mocks.revurderingService.fortsettEtterForhåndsvarsling(
            FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                revurderingId = simulertRevurdering.id,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomBesluttetForhåndsvarsling"),
                begrunnelse = "begrunnelse",
                fritekstTilBrev = "",
            ),
        ) shouldBe simulertRevurdering.copy(
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("begrunnelse"),
        ).tilAttestering(
            attesteringsoppgaveId = OppgaveId("oppgaveId"),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomBesluttetForhåndsvarsling"),
            fritekstTilBrev = "",
        )
        verify(mocks.revurderingRepo).hent(argThat { it shouldBe simulertRevurdering.id })
        verify(mocks.oppgaveService).lukkOppgave(
            argThat { it shouldBe simulertRevurdering.oppgaveId },
        )
        verify(mocks.oppgaveService).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.AttesterRevurdering(
                    saksnummer = simulertRevurdering.saksnummer,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )
        verify(mocks.personService).hentAktørId(argThat { it shouldBe simulertRevurdering.fnr })
        verify(mocks.revurderingRepo).defaultTransactionContext()
        verify(mocks.revurderingRepo).lagre(
            argThat {
                it shouldBe simulertRevurdering.copy(
                    forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag("begrunnelse"),
                ).tilAttestering(
                    attesteringsoppgaveId = OppgaveId("oppgaveId"),
                    saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "saksbehandlerSomBesluttetForhåndsvarsling"),
                    fritekstTilBrev = "",
                ).orNull()!!
            },
            anyOrNull(),
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `avslutter en revurdering etter forhåndsvarsling`() {
        val simulertMedForhåndsvarsel = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            forhåndsvarsel = Forhåndsvarsel.UnderBehandling.Sendt,
        ).second

        val mocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertMedForhåndsvarsel
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Informasjon(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = "brev".toByteArray(),
                    generertDokumentJson = "brev",
                ).right()
            },
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
        )

        val actual = mocks.revurderingService.fortsettEtterForhåndsvarsling(
            request = FortsettEtterForhåndsvarslingRequest.AvsluttUtenEndringer(
                revurderingId = simulertMedForhåndsvarsel.id,
                saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "navn"),
                begrunnelse = "b e g r u n n e l s e",
                fritekstTilBrev = null,
            ),
        )

        val expected = AvsluttetRevurdering.tryCreate(
            underliggendeRevurdering = simulertMedForhåndsvarsel.copy(
                forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.Avsluttet("b e g r u n n e l s e"),
            ),
            begrunnelse = "b e g r u n n e l s e",
            fritekst = null,
            tidspunktAvsluttet = fixedTidspunkt,
        ).getOrFail()

        actual shouldBe expected.right()

        verify(mocks.revurderingRepo).hent(argThat { it shouldBe simulertMedForhåndsvarsel.id })
        verify(mocks.revurderingRepo).defaultTransactionContext()
        verify(mocks.revurderingRepo).lagre(
            argThat {
                it shouldBe expected
            },
            anyOrNull(),
        )
        verify(mocks.oppgaveService).lukkOppgave(
            argThat { it shouldBe simulertMedForhåndsvarsel.oppgaveId },
        )
        verify(mocks.brevService).lagDokument(
            argThat {
                it shouldBe expected
            },
        )
        verify(mocks.brevService).lagreDokument(
            argThat {
                it should beOfType<Dokument.MedMetadata.Informasjon>()
                it.generertDokument shouldBe "brev".toByteArray()
                it.metadata shouldBe Dokument.Metadata(
                    sakId = simulertMedForhåndsvarsel.sakId,
                    revurderingId = simulertMedForhåndsvarsel.id,
                    bestillBrev = true,
                )
            },
        )
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `kan ikke beslutte en allerede besluttet forhåndsvarsling`() {
        val simulertRevurdering = SimulertRevurdering.Innvilget(
            id = revurderingId,
            periode = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            beregning = TestBeregning,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "navn",
                datoBeregnet = fixedLocalDate,
                nettoBeløp = 0,
                periodeList = listOf(),
            ),
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget("begrunnelse"),
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).fortsettEtterForhåndsvarsling(
            FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                begrunnelse = "",
                fritekstTilBrev = "",
            ),
        ) shouldBe FortsettEtterForhåndsvarselFeil.UgyldigTilstandsovergang(
            fra = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.EndreGrunnlaget::class,
            til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,

        ).left()
    }

    @Test
    fun `kan ikke beslutte hvis det ikke har vært sendt forhåndsvarsel`() {
        val simulertRevurdering = RevurderingTestUtils.simulertRevurderingInnvilget
            .copy(forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles)

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertRevurdering
        }

        RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).fortsettEtterForhåndsvarsling(
            FortsettEtterForhåndsvarslingRequest.FortsettMedSammeOpplysninger(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                begrunnelse = "",
                fritekstTilBrev = "",
            ),
        ) shouldBe FortsettEtterForhåndsvarselFeil.UgyldigTilstandsovergang(
            fra = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles::class,
            til = Forhåndsvarsel.Ferdigbehandlet.Forhåndsvarslet.FortsettMedSammeGrunnlag::class,
        ).left()
    }
}
