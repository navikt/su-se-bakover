package no.nav.su.se.bakover.service.revurdering

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiClient
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

object RevurderingTestUtils {
    internal val sakId: UUID = UUID.randomUUID()
    internal val dagensDato = LocalDate.now().let {
        LocalDate.of(
            it.year,
            it.month,
            1,
        )
    }
    internal val nesteMåned =
        LocalDate.of(
            dagensDato.year,
            dagensDato.month.plus(1),
            1,
        )
    internal val periode = Periode.create(
        fraOgMed = nesteMåned,
        tilOgMed = nesteMåned.let {
            val treMånederFramITid = it.plusMonths(3)
            LocalDate.of(
                treMånederFramITid.year,
                treMånederFramITid.month,
                treMånederFramITid.lengthOfMonth(),
            )
        },
    )
    internal val stønadsperiode = ValgtStønadsperiode(
        periode = periode,
        begrunnelse = "begrunnelsen for perioden"
    )
    internal val attesteringUnderkjent = Attestering.Underkjent(NavIdentBruker.Attestant("Attes T. Ant"), Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL, "kommentar")
    internal val saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler")
    internal val saksnummer = Saksnummer(nummer = 12345676)
    internal val fnr = FnrGenerator.random()
    internal val revurderingId = UUID.randomUUID()
    internal val aktørId = AktørId("aktørId")

    internal val beregningMock = mock<Beregning> {
        on { getPeriode() } doReturn periode
        on { getMånedsberegninger() } doReturn periode.tilMånedsperioder()
            .map { MånedsberegningFactory.ny(it, Sats.HØY, listOf()) }
        on { getFradrag() } doReturn listOf()
        on { getSumYtelse() } doReturn periode.tilMånedsperioder()
            .sumBy { MånedsberegningFactory.ny(it, Sats.HØY, listOf()).getSumYtelse() }
    }

    internal val revurderingsårsak = Revurderingsårsak(
        Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
        Revurderingsårsak.Begrunnelse.create("Ny informasjon"),
    )

    internal val revurderingsårsakRegulerGrunnbeløp = Revurderingsårsak(
        Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
        Revurderingsårsak.Begrunnelse.create("Nytt Grunnbeløp"),
    )

    internal val søknadsbehandlingVedtak = Vedtak.fromSøknadsbehandling(
        Søknadsbehandling.Iverksatt.Innvilget(
            id = mock(),
            opprettet = mock(),
            sakId = sakId,
            saksnummer = saksnummer,
            søknad = mock(),
            oppgaveId = mock(),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().copy(
                bosituasjon = Behandlingsinformasjon.Bosituasjon(
                    ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                    delerBolig = false,
                    ektemakeEllerSamboerUførFlyktning = null,
                    begrunnelse = null,
                ),
                ektefelle = Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
            ),
            fnr = fnr,
            beregning = beregningMock,
            simulering = mock(),
            saksbehandler = saksbehandler,
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("Attes T. Ant")),
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
        ),
        UUID30.randomUUID(),
    )
    internal val simulertRevurderingInnvilget = SimulertRevurdering.Innvilget(
        id = revurderingId,
        periode = periode,
        opprettet = fixedTidspunkt,
        tilRevurdering = søknadsbehandlingVedtak,
        saksbehandler = saksbehandler,
        oppgaveId = OppgaveId("Oppgaveid"),
        fritekstTilBrev = "",
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = søknadsbehandlingVedtak.behandlingsinformasjon,
        simulering = mock(),
        beregning = beregningMock,
        grunnlagsdata = Grunnlagsdata.EMPTY,
    )

    internal val sak = Sak(
        id = sakId,
        saksnummer = saksnummer,
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        søknader = listOf(),
        utbetalinger = listOf(
            mock {
                on { senesteDato() } doReturn periode.tilOgMed
                on { tidligsteDato() } doReturn periode.fraOgMed
            },
        ),
        vedtakListe = listOf(søknadsbehandlingVedtak),
    )
    internal fun createRevurderingService(
        sakService: SakService = mock(),
        utbetalingService: UtbetalingService = mock(),
        revurderingRepo: RevurderingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        microsoftGraphApiClient: MicrosoftGraphApiClient = mock(),
        brevService: BrevService = mock(),
        clock: Clock = fixedClock,
        vedtakRepo: VedtakRepo = mock(),
        ferdigstillVedtakService: FerdigstillVedtakService = mock(),
        grunnlagService: GrunnlagService = mock(),
    ) =
        RevurderingServiceImpl(
            sakService = sakService,
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            oppgaveService = oppgaveService,
            personService = personService,
            microsoftGraphApiClient = microsoftGraphApiClient,
            brevService = brevService,
            clock = clock,
            vedtakRepo = vedtakRepo,
            ferdigstillVedtakService = ferdigstillVedtakService,
            grunnlagService = grunnlagService,
        )
}
