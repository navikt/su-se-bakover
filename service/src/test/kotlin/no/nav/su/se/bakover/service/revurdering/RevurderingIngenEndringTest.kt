package no.nav.su.se.bakover.service.revurdering

import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.Vurderingstatus
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.søknadOppgaveId
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.beregning.TestBeregningSomGirOpphør
import no.nav.su.se.bakover.service.formueVilkår
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.attesteringUnderkjent
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.createRevurderingService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.periodeNesteMånedOgTreMånederFram
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.søknadsbehandlingsvedtakIverksattInnvilget
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.create
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.api.Test
import java.util.UUID

class RevurderingIngenEndringTest {

    @Test
    fun `Revurderingen går ikke gjennom hvis endring av utbetaling er under ti prosent`() {
        val uføregrunnlag = Grunnlag.Uføregrunnlag(
            periode = periodeNesteMånedOgTreMånederFram,
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 12000,
            opprettet = fixedTidspunkt,
        )
        val opprettetRevurdering = OpprettetRevurdering(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata(
                bosituasjon = listOf(
                    Grunnlag.Bosituasjon.Fullstendig.Enslig(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        periode = periodeNesteMånedOgTreMånederFram,
                        begrunnelse = null,
                    ),
                ),
            ),
            vilkårsvurderinger = Vilkårsvurderinger(
                uføre = Vilkår.Uførhet.Vurdert.create(
                    vurderingsperioder = nonEmptyListOf(
                        Vurderingsperiode.Uføre.create(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            resultat = Resultat.Innvilget,
                            grunnlag = uføregrunnlag,
                            periode = periodeNesteMånedOgTreMånederFram,
                            begrunnelse = "ok2k",
                        ),
                    ),
                ),
                formue = formueVilkår(periodeNesteMånedOgTreMånederFram),
            ),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(
                mapOf(
                    Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                ),
            ),
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val utbetalingMock = mock<Utbetaling> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                Utbetalingslinje.Ny(
                    fraOgMed = periodeNesteMånedOgTreMånederFram.fraOgMed,
                    tilOgMed = periodeNesteMånedOgTreMånederFram.tilOgMed,
                    forrigeUtbetalingslinjeId = null,
                    beløp = 20000,
                ),
            )
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { hentUtbetalinger(any()) } doReturn listOf(utbetalingMock)
        }

        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = saksbehandler,
        ).orNull()!!.revurdering as BeregnetRevurdering.IngenEndring
        actual.shouldBeEqualToIgnoringFields(
            BeregnetRevurdering.IngenEndring(
                id = revurderingId,
                periode = periodeNesteMånedOgTreMånederFram,
                opprettet = fixedTidspunkt,
                tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
                oppgaveId = søknadOppgaveId,
                beregning = TestBeregning,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
                revurderingsårsak = revurderingsårsak,
                behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
                forhåndsvarsel = null,
                grunnlagsdata = opprettetRevurdering.grunnlagsdata,
                vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger,
                informasjonSomRevurderes = InformasjonSomRevurderes.create(
                    mapOf(
                        Revurderingsteg.Inntekt to Vurderingstatus.IkkeVurdert,
                    ),
                ),
            ),
            // beregningstypen er internal i domene modulen
            BeregnetRevurdering.IngenEndring::beregning,
            BeregnetRevurdering::grunnlagsdata,
        )

        inOrder(
            revurderingRepoMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual })
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
        )
    }

    @Test
    fun `attesterer revurdering som ikke fører til endring i ytelse`() {
        val vilkårsvurderingerMock = mock<Vilkårsvurderinger> {
            on { resultat } doReturn Resultat.Innvilget
        }
        val beregnetRevurdering = BeregnetRevurdering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = vilkårsvurderingerMock,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val endretSaksbehandler = NavIdentBruker.Saksbehandler("endretSaksbehandler")
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = endretSaksbehandler,
            fritekstTilBrev = "endret fritekst",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = true,
            behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = vilkårsvurderingerMock,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn beregnetRevurdering
            on { hentEventuellTidligereAttestering(any()) } doReturn null
        }
        val utbetalingServiceMock = mock<UtbetalingService>()
        val vedtakRepoMock = mock<VedtakRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn søknadOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId,
                endretSaksbehandler,
                "endret fritekst",
                true,
            ),
        ).getOrHandle { throw RuntimeException(it.toString()) } as RevurderingTilAttestering.IngenEndring

        actual shouldBe revurderingTilAttestering

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            personServiceMock,
            oppgaveServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(revurderingRepoMock).hentEventuellTidligereAttestering(argThat { it shouldBe revurderingId })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.AttesterRevurdering(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                    )
                },
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknadOppgaveId })
            verify(revurderingRepoMock).lagre(argThat { it shouldBe revurderingTilAttestering })
        }
        verifyNoMoreInteractions(
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
        )
    }

    @Test
    fun `underkjenn revurdering`() {
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = false,
            behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val underkjentRevurdering = UnderkjentRevurdering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = Tidspunkt.EPOCH,
            tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
            oppgaveId = søknadOppgaveId,
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            attestering = attesteringUnderkjent,
            skalFøreTilBrevutsending = false,
            behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }
        val utbetalingServiceMock = mock<UtbetalingService>()
        val vedtakRepoMock = mock<VedtakRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn søknadOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }
        val actual = createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).underkjenn(
            revurderingId,
            attesteringUnderkjent,
        ).orNull()!! as UnderkjentRevurdering.IngenEndring

        actual shouldBe underkjentRevurdering

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            personServiceMock,
            oppgaveServiceMock,
        ) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.Revurderingsbehandling(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = saksbehandler,
                    )
                },
            )
            verify(revurderingRepoMock).lagre(argThat { it shouldBe underkjentRevurdering })
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe søknadOppgaveId })
        }
        verifyNoMoreInteractions(
            personServiceMock,
            oppgaveServiceMock,
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
        )
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse og sender brev`() {
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = true,
            behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val attestant = NavIdentBruker.Attestant("ATTT")
        val iverksattRevurdering = revurderingTilAttestering.tilIverksatt(attestant).orNull()!!
        val vedtak = Vedtak.from(iverksattRevurdering, fixedClock)
        val journalførtVedtak = vedtak.journalfør { JournalpostId("journalført").right() }.orNull()!!
        val vedtakMedDistribuertBrev = journalførtVedtak.distribuerBrev { BrevbestillingId("bestiltBrev").right() }.orNull()!!

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }
        val utbetalingServiceMock = mock<UtbetalingService>()
        val vedtakRepoMock = mock<VedtakRepo>()
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService> {
            on { journalførOgLagre(any()) } doReturn journalførtVedtak.right()
            on { distribuerOgLagre(any()) } doReturn vedtakMedDistribuertBrev.right()
        }

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            ferdigstillVedtakService = ferdigstillVedtakServiceMock,
        ).iverksett(
            revurderingId,
            attestant,
        ) shouldBe iverksattRevurdering.right()

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
            ferdigstillVedtakServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(ferdigstillVedtakServiceMock).journalførOgLagre(argThat { it shouldBe vedtak.copy(id = it.id) })
            verify(ferdigstillVedtakServiceMock).distribuerOgLagre(argThat { it shouldBe journalførtVedtak.copy(id = it.id) })
            verify(revurderingRepoMock).lagre(any())
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            ferdigstillVedtakServiceMock,
        )
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse og sender ikke brev`() {
        val revurderingTilAttestering = RevurderingTilAttestering.IngenEndring(
            id = revurderingId,
            periode = periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = søknadsbehandlingsvedtakIverksattInnvilget,
            oppgaveId = OppgaveId(value = "OppgaveId"),
            beregning = TestBeregningSomGirOpphør,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = null,
            skalFøreTilBrevutsending = false,
            behandlingsinformasjon = søknadsbehandlingsvedtakIverksattInnvilget.behandlingsinformasjon,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
        )
        val attestant = NavIdentBruker.Attestant("ATTT")
        val iverksattRevurdering = revurderingTilAttestering.tilIverksatt(attestant).orNull()!!
        val vedtak = Vedtak.from(iverksattRevurdering, fixedClock)

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }
        val utbetalingServiceMock = mock<UtbetalingService>()
        val vedtakRepoMock = mock<VedtakRepo>()
        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            ferdigstillVedtakService = ferdigstillVedtakServiceMock,
        ).iverksett(
            revurderingId,
            attestant,
        ) shouldBe iverksattRevurdering.right()

        inOrder(
            revurderingRepoMock,
            vedtakRepoMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(vedtakRepoMock).lagre(argThat { it shouldBe vedtak.copy(id = it.id) })
            verify(revurderingRepoMock).lagre(any())
        }
        verifyNoMoreInteractions(
            revurderingRepoMock,
            utbetalingServiceMock,
            vedtakRepoMock,
            ferdigstillVedtakServiceMock,
        )
    }
}
