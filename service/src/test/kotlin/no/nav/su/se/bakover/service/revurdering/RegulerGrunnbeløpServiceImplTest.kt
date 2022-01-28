package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsakRegulerGrunnbeløp
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.UførevilkårStatus
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.innvilgetUførevilkår
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class RegulerGrunnbeløpServiceImplTest {

    @Test
    fun `oppdaterer uførevilkåret når nytt uføregrunnlag legges til`() {
        val informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Uførhet))
        val opprettetRevurdering =
            opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(informasjonSomRevurderes = informasjonSomRevurderes).second

        val nyttUføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = opprettetRevurdering.periode,
            uføregrad = Uføregrad.parse(45),
            forventetInntekt = 20,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturnConsecutively listOf(
                opprettetRevurdering,
                opprettetRevurdering.copy(grunnlagsdata = Grunnlagsdata.IkkeVurdert),
            )
        }
        val vilkårsvurderingServiceMock = mock<VilkårsvurderingService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vilkårsvurderingService = vilkårsvurderingServiceMock,
        ).leggTilUføregrunnlag(
            LeggTilUførevurderingerRequest(
                behandlingId = revurderingId,
                vurderinger = nonEmptyListOf(
                    LeggTilUførevilkårRequest(
                        behandlingId = nyttUføregrunnlag.id,
                        periode = nyttUføregrunnlag.periode,
                        uføregrad = nyttUføregrunnlag.uføregrad,
                        forventetInntekt = nyttUføregrunnlag.forventetInntekt,
                        oppfylt = UførevilkårStatus.VilkårOppfylt,
                        begrunnelse = "grunnbeløpet er høyere",
                    ),
                ),
            ),
        )

        verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
        verify(revurderingRepoMock).defaultTransactionContext()
        verify(revurderingRepoMock).lagre(
            argThat {
                it shouldBe opprettetRevurdering.copy(
                    vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger.copy(
                        uføre = innvilgetUførevilkår(
                            vurderingsperiodeId = (it.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).vurderingsperioder.first().id,
                            grunnlagsId = (it.vilkårsvurderinger.uføre as Vilkår.Uførhet.Vurdert).grunnlag.first().id,
                            opprettet = fixedTidspunkt,
                            periode = nyttUføregrunnlag.periode,
                            begrunnelse = "grunnbeløpet er høyere",
                            forventetInntekt = nyttUføregrunnlag.forventetInntekt,
                            uføregrad = nyttUføregrunnlag.uføregrad,
                        ),
                    ),
                    informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Uførhet),
                )
            },
            anyOrNull()
        )
        verify(vilkårsvurderingServiceMock).lagre(
            argThat { it shouldBe opprettetRevurdering.id },
            any(),
        )
        verifyNoMoreInteractions(revurderingRepoMock, vilkårsvurderingServiceMock)
    }

    @Test
    fun `G-regulering med uendret fradrag og forventetInntekt fører til IngenEndring`() {
        val (sak, revurdering) = opprettetRevurdering(
            revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            revurderingsårsak = Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP.toString(),
                begrunnelse = "tjohei",
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering
            },
            utbetalingService = mock {
                on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
            },
            vedtakService = mock {
                on {
                    kopierGjeldendeVedtaksdata(
                        any(),
                        any(),
                    )
                } doReturn sak.kopierGjeldendeVedtaksdata(revurdering.periode.fraOgMed, fixedClock).getOrFail().right()
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
                revurderingId = revurdering.id,
                saksbehandler = saksbehandler,
            ).getOrFail().revurdering

            inOrder(
                *serviceAndMocks.all(),
            ) {
                verify(serviceAndMocks.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(serviceAndMocks.utbetalingService).hentUtbetalinger(argThat { it shouldBe sakId })
                verify(serviceAndMocks.vedtakService).kopierGjeldendeVedtaksdata(
                    argThat { it shouldBe sakId },
                    argThat { it shouldBe revurdering.periode.fraOgMed },
                )
                verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
                verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
                verify(serviceAndMocks.grunnlagService).lagreFradragsgrunnlag(
                    argThat { it shouldBe revurdering.id },
                    argThat { it shouldBe revurdering.grunnlagsdata.fradragsgrunnlag },
                )
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Ikke lov å sende en Simulert Opphørt til attestering`() {
        val simulertRevurdering = SimulertRevurdering.Opphørt(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            beregning = TestBeregning,
            simulering = mock(),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = mock {
                on { resultat } doReturn Vilkårsvurderingsresultat.Innvilget(emptySet())
            },
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ) shouldBe KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør.left()

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `Ikke lov å sende en Underkjent Opphørt til attestering`() {
        val simulertRevurdering = UnderkjentRevurdering.Opphørt(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            beregning = TestBeregning,
            simulering = mock(),
            attesteringer = mock(),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = mock {
                on { resultat } doReturn Vilkårsvurderingsresultat.Innvilget(emptySet())
            },
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ) shouldBe KunneIkkeSendeRevurderingTilAttestering.KanIkkeRegulereGrunnbeløpTilOpphør.left()

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `En attestert beregnet revurdering skal ikke sende brev`() {
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val beregnetRevurdering = BeregnetRevurdering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            beregning = TestBeregning,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = mock {
                on { resultat } doReturn Vilkårsvurderingsresultat.Innvilget(emptySet())
            },
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn beregnetRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).orNull()!! as RevurderingTilAttestering.IngenEndring

        actual shouldBe RevurderingTilAttestering.IngenEndring(
            tilRevurdering = tilRevurdering,
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "Fritekst",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            beregning = actual.beregning,
            skalFøreTilUtsendingAvVedtaksbrev = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = actual.vilkårsvurderinger,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())

            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual }, anyOrNull())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `En attestert underkjent revurdering skal ikke sende brev`() {
        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val underkjentRevurdering = UnderkjentRevurdering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            beregning = TestBeregning,
            attesteringer = Attesteringshistorikk.empty(),
            skalFøreTilUtsendingAvVedtaksbrev = true,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = mock {
                on { resultat } doReturn Vilkårsvurderingsresultat.Innvilget(emptySet())
            },
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn underkjentRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).orNull()!! as RevurderingTilAttestering.IngenEndring

        actual shouldBe RevurderingTilAttestering.IngenEndring(
            tilRevurdering = tilRevurdering,
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "Fritekst",
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            beregning = actual.beregning,
            skalFøreTilUtsendingAvVedtaksbrev = false,
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = actual.vilkårsvurderinger,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })

            verify(oppgaveServiceMock).opprettOppgave(any())
            verify(oppgaveServiceMock).lukkOppgave(any())

            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual }, anyOrNull())
        }
        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `iverksetter endring av ytelse`() {
        val attestant = NavIdentBruker.Attestant("attestant")

        val tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            skalFøreTilUtsendingAvVedtaksbrev = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            attesteringer = Attesteringshistorikk.empty(),
            avkorting = AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående,
        )

        val iverksattRevurdering = IverksattRevurdering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregning,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(attestant, fixedTidspunkt)),
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            skalFøreTilUtsendingAvVedtaksbrev = false,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Revurdering.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }

        val vedtakRepoMock = mock<VedtakRepo>()
        val eventObserver: EventObserver = mock()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            vedtakRepo = vedtakRepoMock,
            clock = fixedClock,
        ).apply { addObserver(eventObserver) }
            .iverksett(
                revurderingId = revurderingTilAttestering.id,
                attestant = attestant,
            ) shouldBe iverksattRevurdering.right()

        inOrder(
            revurderingRepoMock,
            vedtakRepoMock,
            eventObserver,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(vedtakRepoMock).lagre(
                argThat {
                    it should beOfType<VedtakSomKanRevurderes.IngenEndringIYtelse>()
                },
            )
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe iverksattRevurdering }, anyOrNull())
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.RevurderingStatistikk.RevurderingIverksatt(iverksattRevurdering)
                },
            )
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            vedtakRepoMock,
        )
    }
}
