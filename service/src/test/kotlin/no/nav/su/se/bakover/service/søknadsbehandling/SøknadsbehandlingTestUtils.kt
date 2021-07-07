package no.nav.su.se.bakover.service.søknadsbehandling

import com.nhaarman.mockitokotlin2.mock
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.VilkårsvurderingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import java.time.Clock
import java.util.UUID

internal val testBeregning = TestBeregning

internal val simulering = Simulering(
    gjelderId = fnr,
    gjelderNavn = "NAVN",
    datoBeregnet = idag(fixedClock),
    nettoBeløp = 191500,
    periodeList = listOf(),
)

internal fun createSøknadsbehandlingService(
    søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
    utbetalingService: UtbetalingService = mock(),
    oppgaveService: OppgaveService = mock(),
    søknadService: SøknadService = mock(),
    søknadRepo: SøknadRepo = mock(),
    personService: PersonService = mock(),
    behandlingMetrics: BehandlingMetrics = mock(),
    observer: EventObserver = mock(),
    beregningService: BeregningService = mock(),
    microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
    brevService: BrevService = mock(),
    opprettVedtakssnapshotService: OpprettVedtakssnapshotService = mock(),
    clock: Clock = Clock.systemUTC(),
    vedtakRepo: VedtakRepo = mock(),
    ferdigstillVedtakService: FerdigstillVedtakService = mock(),
    vilkårsvurderingService: VilkårsvurderingService = mock(),
    grunnlagService: GrunnlagService = mock(),
) = SøknadsbehandlingServiceImpl(
    søknadService,
    søknadRepo,
    søknadsbehandlingRepo,
    utbetalingService,
    personService,
    oppgaveService,
    behandlingMetrics,
    beregningService,
    microsoftGraphApiOppslag,
    brevService,
    opprettVedtakssnapshotService,
    clock,
    vedtakRepo,
    ferdigstillVedtakService,
    vilkårsvurderingService,
    grunnlagService,
).apply { addObserver(observer) }

internal fun lagUavklartSøknadsbehandling(sakId: UUID = UUID.randomUUID(), saksnummer: Saksnummer = Saksnummer(2021)): Søknadsbehandling.Vilkårsvurdert.Uavklart {
    return Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
        sakId = sakId, saksnummer = saksnummer,
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
            sakId = sakId, søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("123"),
            oppgaveId = OppgaveId("123"),
        ),
        oppgaveId = OppgaveId("123"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(), fritekstTilBrev = "",
        stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
        grunnlagsdata = Grunnlagsdata(
            fradragsgrunnlag = listOf(), bosituasjon = listOf(),
        ),
        vilkårsvurderinger = Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.IkkeVurdert, formue = Vilkår.Formue.IkkeVurdert,
        ),
    )
}

internal fun lagUnderkjentInnvilgetSøknadsbehandling(sakId: UUID = UUID.randomUUID(), saksnummer: Saksnummer = Saksnummer(2021)): Søknadsbehandling.Underkjent.Innvilget {
    return Søknadsbehandling.Underkjent.Innvilget(
        id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
        sakId = sakId, saksnummer = saksnummer,
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
            sakId = sakId, søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("123"), oppgaveId = OppgaveId("123"),
        ),
        oppgaveId = OppgaveId("123"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(), fritekstTilBrev = "",
        stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
        grunnlagsdata = Grunnlagsdata.EMPTY, vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        beregning = TestBeregning, simulering = simulering,
        saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler"),
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant attestanten")),
    )
}

internal fun lagTilAttesteringInnvilgetSøknadsbehandling(sakId: UUID = UUID.randomUUID(), saksnummer: Saksnummer = Saksnummer(2021)): Søknadsbehandling.TilAttestering.Innvilget {
    return Søknadsbehandling.TilAttestering.Innvilget(
        id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
        sakId = sakId, saksnummer = saksnummer,
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
            sakId = sakId, søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("123"), oppgaveId = OppgaveId("123"),
        ),
        oppgaveId = OppgaveId("123"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(), fritekstTilBrev = "",
        stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
        grunnlagsdata = Grunnlagsdata.EMPTY, vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        beregning = TestBeregning, simulering = simulering,
        saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler"),
    )
}

internal fun lagIverksattInnvilgetSøknadsbehandling(sakId: UUID = UUID.randomUUID(), saksnummer: Saksnummer = Saksnummer(2021)): Søknadsbehandling.Iverksatt.Innvilget {
    return Søknadsbehandling.Iverksatt.Innvilget(
        id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
        sakId = sakId, saksnummer = saksnummer,
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(), opprettet = Tidspunkt.EPOCH,
            sakId = sakId, søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId("123"), oppgaveId = OppgaveId("123"),
        ),
        oppgaveId = OppgaveId("123"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(), fritekstTilBrev = "",
        stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021))),
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        beregning = TestBeregning, simulering = simulering,
        saksbehandler = NavIdentBruker.Saksbehandler("Sak S. behandler"),
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant attestanten")),
    )
}
