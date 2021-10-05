package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingslinjePåTidslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.sak.SakRestans
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.HentDokumenterForIdType
import no.nav.su.se.bakover.service.grunnlag.GrunnlagService
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarselFeil
import no.nav.su.se.bakover.service.revurdering.FortsettEtterForhåndsvarslingRequest
import no.nav.su.se.bakover.service.revurdering.GjenopptaYtelseRequest
import no.nav.su.se.bakover.service.revurdering.HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.service.revurdering.KunneIkkeGjenopptaYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteGjenopptakAvYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilBosituasjongrunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.service.revurdering.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.service.revurdering.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.service.revurdering.LeggTilBosituasjongrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.LeggTilFormuegrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.OppdaterRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.OpprettRevurderingRequest
import no.nav.su.se.bakover.service.revurdering.RevurderingOgFeilmeldingerResponse
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.Revurderingshandling
import no.nav.su.se.bakover.service.revurdering.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Statistikk
import no.nav.su.se.bakover.service.statistikk.StatistikkService
import no.nav.su.se.bakover.service.søknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.KunneIkkeLageSøknadPdf
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.OpprettManglendeJournalpostOgOppgaveResultat
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.utbetaling.FantIkkeGjeldendeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.SimulerGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import java.time.LocalDate
import java.util.UUID

open class AccessCheckProxy(
    private val personRepo: PersonRepo,
    private val services: Services,
) {
    fun proxy(): Services {
        return Services(
            avstemming = object : AvstemmingService {
                override fun grensesnittsavstemming(): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
                    return services.avstemming.grensesnittsavstemming()
                }

                override fun grensesnittsavstemming(
                    fraOgMed: Tidspunkt,
                    tilOgMed: Tidspunkt,
                ): Either<AvstemmingFeilet, Avstemming.Grensesnittavstemming> {
                    return services.avstemming.grensesnittsavstemming(fraOgMed, tilOgMed)
                }

                override fun konsistensavstemming(
                    løpendeFraOgMed: LocalDate,
                ): Either<AvstemmingFeilet, Avstemming.Konsistensavstemming.Ny> {
                    return services.avstemming.konsistensavstemming(løpendeFraOgMed)
                }
            },
            utbetaling = object : UtbetalingService {
                override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
                    assertHarTilgangTilUtbetaling(utbetalingId)

                    return services.utbetaling.hentUtbetaling(utbetalingId)
                }

                override fun hentUtbetalinger(sakId: UUID): List<Utbetaling> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun oppdaterMedKvittering(
                    avstemmingsnøkkel: Avstemmingsnøkkel,
                    kvittering: Kvittering,
                ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> =
                    kastKanKunKallesFraAnnenService()

                override fun simulerUtbetaling(
                    sakId: UUID,
                    saksbehandler: NavIdentBruker,
                    beregning: Beregning,
                    uføregrunnlag: List<Grunnlag.Uføregrunnlag>
                ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
                    assertHarTilgangTilSak(sakId)

                    return services.utbetaling.simulerUtbetaling(sakId, saksbehandler, beregning, uføregrunnlag)
                }

                override fun simulerOpphør(
                    sakId: UUID,
                    saksbehandler: NavIdentBruker,
                    opphørsdato: LocalDate,
                ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
                    assertHarTilgangTilSak(sakId)

                    return services.utbetaling.simulerOpphør(sakId, saksbehandler, opphørsdato)
                }

                override fun utbetal(
                    sakId: UUID,
                    attestant: NavIdentBruker,
                    beregning: Beregning,
                    simulering: Simulering,
                    uføregrunnlag: List<Grunnlag.Uføregrunnlag>
                ): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling.UtenKvittering> {
                    assertHarTilgangTilSak(sakId)

                    return services.utbetaling.utbetal(sakId, attestant, beregning, simulering, uføregrunnlag)
                }

                override fun simulerStans(
                    sakId: UUID,
                    saksbehandler: NavIdentBruker,
                    stansDato: LocalDate
                ): Either<SimulerStansFeilet, Utbetaling.SimulertUtbetaling> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun stansUtbetalinger(
                    sakId: UUID,
                    attestant: NavIdentBruker,
                    simulering: Simulering,
                    stansDato: LocalDate
                ): Either<UtbetalStansFeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun simulerGjenopptak(
                    sakId: UUID,
                    saksbehandler: NavIdentBruker,
                ): Either<SimulerGjenopptakFeil, Utbetaling.SimulertUtbetaling> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun gjenopptaUtbetalinger(
                    sakId: UUID,
                    attestant: NavIdentBruker,
                    simulering: Simulering,
                ): Either<UtbetalGjenopptakFeil, Utbetaling.OversendtUtbetaling.UtenKvittering> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun opphør(
                    sakId: UUID,
                    attestant: NavIdentBruker,
                    simulering: Simulering,
                    opphørsdato: LocalDate,
                ): Either<UtbetalingFeilet, Utbetaling.OversendtUtbetaling.UtenKvittering> {
                    assertHarTilgangTilSak(sakId)
                    return services.utbetaling.opphør(sakId, attestant, simulering, opphørsdato)
                }

                override fun hentGjeldendeUtbetaling(
                    sakId: UUID,
                    forDato: LocalDate,
                ): Either<FantIkkeGjeldendeUtbetaling, UtbetalingslinjePåTidslinje> {
                    kastKanKunKallesFraAnnenService()
                }
            },
            sak = object : SakService {
                override fun hentSak(sakId: UUID): Either<FantIkkeSak, Sak> {
                    assertHarTilgangTilSak(sakId)

                    return services.sak.hentSak(sakId)
                }

                override fun hentSak(fnr: Fnr): Either<FantIkkeSak, Sak> {
                    // Siden vi også vil kontrollere på EPS må vi hente ut saken først
                    // og sjekke på hele den (i stedet for å gjøre assertHarTilgangTilPerson(fnr))
                    return services.sak.hentSak(fnr)
                        .also {
                            it.map { sak -> assertHarTilgangTilSak(sak.id) }
                        }
                }

                override fun hentSak(saksnummer: Saksnummer): Either<FantIkkeSak, Sak> {
                    return services.sak.hentSak(saksnummer)
                        .also {
                            it.map { sak -> assertHarTilgangTilSak(sak.id) }
                        }
                }

                override fun opprettSak(sak: NySak) {
                    assertHarTilgangTilPerson(sak.fnr)

                    return services.sak.opprettSak(sak)
                }

                override fun hentRestanserForAlleSaker(): List<SakRestans> {
                    // vi gjør ikke noe assert fordi vi ikke sender noe sensitiv info.
                    // Samtidig som at dem går gjennom hentSak() når de skal saksbehandle
                    return services.sak.hentRestanserForAlleSaker()
                }
            },
            søknad = object : SøknadService {
                override fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>> {
                    assertHarTilgangTilPerson(søknadInnhold.personopplysninger.fnr)

                    return services.søknad.nySøknad(søknadInnhold)
                }

                override fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.søknad.hentSøknad(søknadId)
                }

                override fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, ByteArray> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.søknad.hentSøknadPdf(søknadId)
                }

                override fun opprettManglendeJournalpostOgOppgave(): OpprettManglendeJournalpostOgOppgaveResultat {
                    // Dette er et driftsendepunkt og vi vil ikke returnere kode 6/7/person-sensitive data.

                    return services.søknad.opprettManglendeJournalpostOgOppgave()
                }
            },
            brev = object : BrevService {
                override fun lagBrev(request: LagBrevRequest) = kastKanKunKallesFraAnnenService()

                override fun journalførOgDistribuerUtgåendeDokumenter() {
                    kastKanKunKallesFraAnnenService()
                }

                override fun lagreDokument(dokument: Dokument.MedMetadata) {
                    kastKanKunKallesFraAnnenService()
                }

                override fun lagreDokument(dokument: Dokument.MedMetadata, transactionContext: TransactionContext) {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentDokumenterFor(hentDokumenterForIdType: HentDokumenterForIdType): List<Dokument> {
                    when (hentDokumenterForIdType) {
                        is HentDokumenterForIdType.Revurdering -> assertHarTilgangTilRevurdering(hentDokumenterForIdType.id)
                        is HentDokumenterForIdType.Sak -> assertHarTilgangTilSak(hentDokumenterForIdType.id)
                        is HentDokumenterForIdType.Søknad -> assertHarTilgangTilSøknad(hentDokumenterForIdType.id)
                        is HentDokumenterForIdType.Vedtak -> assertHarTilgangTilVedtak(hentDokumenterForIdType.id)
                    }.let {
                        return services.brev.hentDokumenterFor(hentDokumenterForIdType)
                    }
                }
            },
            lukkSøknad = object : LukkSøknadService {
                override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, Sak> {
                    assertHarTilgangTilSøknad(request.søknadId)

                    return services.lukkSøknad.lukkSøknad(request)
                }

                override fun lagBrevutkast(
                    request: LukkSøknadRequest,
                ): Either<no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLageBrevutkast, ByteArray> {
                    assertHarTilgangTilSøknad(request.søknadId)

                    return services.lukkSøknad.lagBrevutkast(request)
                }
            },
            oppgave = object : OppgaveService {
                override fun opprettOppgave(config: OppgaveConfig) = kastKanKunKallesFraAnnenService()
                override fun opprettOppgaveMedSystembruker(config: OppgaveConfig) = kastKanKunKallesFraAnnenService()
                override fun lukkOppgave(oppgaveId: OppgaveId) = kastKanKunKallesFraAnnenService()
                override fun lukkOppgaveMedSystembruker(oppgaveId: OppgaveId) = kastKanKunKallesFraAnnenService()
                override fun oppdaterOppgave(
                    oppgaveId: OppgaveId,
                    beskrivelse: String,
                ): Either<OppgaveFeil.KunneIkkeOppdatereOppgave, Unit> =
                    kastKanKunKallesFraAnnenService()
            },
            person = object : PersonService {
                override fun hentPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
                    assertHarTilgangTilPerson(fnr)

                    return services.person.hentPerson(fnr)
                }

                override fun hentPersonMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentAktørId(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                    assertHarTilgangTilPerson(fnr)

                    return services.person.hentAktørId(fnr)
                }

                override fun hentAktørIdMedSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, AktørId> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun sjekkTilgangTilPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Unit> {
                    return services.person.sjekkTilgangTilPerson(fnr)
                }
            },
            statistikk = object : StatistikkService {
                override fun publiser(statistikk: Statistikk) {
                    kastKanKunKallesFraAnnenService()
                }
            },
            toggles = services.toggles,
            søknadsbehandling = object : SøknadsbehandlingService {
                override fun opprett(request: SøknadsbehandlingService.OpprettRequest): Either<SøknadsbehandlingService.KunneIkkeOpprette, Søknadsbehandling.Vilkårsvurdert.Uavklart> {
                    assertHarTilgangTilSøknad(request.søknadId)
                    return services.søknadsbehandling.opprett(request)
                }

                override fun vilkårsvurder(request: SøknadsbehandlingService.VilkårsvurderRequest): Either<SøknadsbehandlingService.KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.vilkårsvurder(request)
                }

                override fun beregn(request: SøknadsbehandlingService.BeregnRequest): Either<SøknadsbehandlingService.KunneIkkeBeregne, Søknadsbehandling.Beregnet> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.beregn(request)
                }

                override fun simuler(request: SøknadsbehandlingService.SimulerRequest): Either<SøknadsbehandlingService.KunneIkkeSimulereBehandling, Søknadsbehandling.Simulert> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.simuler(request)
                }

                override fun sendTilAttestering(request: SøknadsbehandlingService.SendTilAttesteringRequest): Either<SøknadsbehandlingService.KunneIkkeSendeTilAttestering, Søknadsbehandling.TilAttestering> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.sendTilAttestering(request)
                }

                override fun underkjenn(request: SøknadsbehandlingService.UnderkjennRequest): Either<SøknadsbehandlingService.KunneIkkeUnderkjenne, Søknadsbehandling.Underkjent> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.underkjenn(request)
                }

                override fun iverksett(request: SøknadsbehandlingService.IverksettRequest): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.iverksett(request)
                }

                override fun brev(request: SøknadsbehandlingService.BrevRequest): Either<SøknadsbehandlingService.KunneIkkeLageBrev, ByteArray> {
                    assertHarTilgangTilBehandling(request.behandling.id)
                    return services.søknadsbehandling.brev(request)
                }

                override fun hent(request: SøknadsbehandlingService.HentRequest): Either<SøknadsbehandlingService.FantIkkeBehandling, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.hent(request)
                }

                override fun oppdaterStønadsperiode(request: SøknadsbehandlingService.OppdaterStønadsperiodeRequest): Either<SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.oppdaterStønadsperiode(request)
                }

                override fun leggTilUføregrunnlag(request: LeggTilUførevurderingRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilGrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilUføregrunnlag(request)
                }

                override fun leggTilBosituasjonEpsgrunnlag(request: LeggTilBosituasjonEpsRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilBosituasjonEpsgrunnlag(request)
                }

                override fun fullførBosituasjongrunnlag(request: FullførBosituasjonRequest): Either<SøknadsbehandlingService.KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.fullførBosituasjongrunnlag(request)
                }

                override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<SøknadsbehandlingService.KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.leggTilFradragsgrunnlag(request)
                }
            },
            ferdigstillVedtak = object : FerdigstillVedtakService {
                override fun ferdigstillVedtakEtterUtbetaling(utbetaling: Utbetaling.OversendtUtbetaling.MedKvittering): Unit =
                    kastKanKunKallesFraAnnenService()

                override fun lukkOppgaveMedBruker(vedtak: Vedtak): Either<FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave, Vedtak> =
                    kastKanKunKallesFraAnnenService()
            },
            revurdering = object : RevurderingService {
                override fun hentRevurdering(revurderingId: UUID): AbstraktRevurdering? {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.hentRevurdering(revurderingId)
                }

                override fun stansAvYtelse(request: StansYtelseRequest): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.revurdering.stansAvYtelse(request)
                }

                override fun iverksettStansAvYtelse(
                    revurderingId: UUID,
                    attestant: NavIdentBruker.Attestant
                ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.iverksettStansAvYtelse(revurderingId, attestant)
                }

                override fun gjenopptaYtelse(request: GjenopptaYtelseRequest): Either<KunneIkkeGjenopptaYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse> {
                    assertHarTilgangTilSak(request.sakId)
                    return services.revurdering.gjenopptaYtelse(request)
                }

                override fun iverksettGjenopptakAvYtelse(
                    revurderingId: UUID,
                    attestant: NavIdentBruker.Attestant
                ): Either<KunneIkkeIverksetteGjenopptakAvYtelse, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.iverksettGjenopptakAvYtelse(revurderingId, attestant)
                }

                override fun opprettRevurdering(
                    opprettRevurderingRequest: OpprettRevurderingRequest,
                ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering> {
                    assertHarTilgangTilSak(opprettRevurderingRequest.sakId)
                    return services.revurdering.opprettRevurdering(opprettRevurderingRequest)
                }

                override fun oppdaterRevurdering(
                    oppdaterRevurderingRequest: OppdaterRevurderingRequest,
                ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering> {
                    assertHarTilgangTilRevurdering(oppdaterRevurderingRequest.revurderingId)
                    return services.revurdering.oppdaterRevurdering(oppdaterRevurderingRequest)
                }

                override fun beregnOgSimuler(
                    revurderingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.beregnOgSimuler(
                        revurderingId = revurderingId,
                        saksbehandler = saksbehandler,
                    )
                }

                override fun forhåndsvarsleEllerSendTilAttestering(
                    revurderingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    revurderingshandling: Revurderingshandling,
                    fritekst: String,
                ): Either<KunneIkkeForhåndsvarsle, Revurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.forhåndsvarsleEllerSendTilAttestering(
                        revurderingId,
                        saksbehandler,
                        revurderingshandling,
                        fritekst,
                    )
                }

                override fun lagBrevutkastForForhåndsvarsling(
                    revurderingId: UUID,
                    fritekst: String,
                ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkastForForhåndsvarsling(revurderingId, fritekst)
                }

                override fun sendTilAttestering(
                    request: SendTilAttesteringRequest,
                ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.sendTilAttestering(request)
                }

                override fun lagBrevutkast(
                    revurderingId: UUID,
                    fritekst: String,
                ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.lagBrevutkast(revurderingId, fritekst)
                }

                override fun hentBrevutkast(revurderingId: UUID): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.hentBrevutkast(revurderingId)
                }

                override fun iverksett(
                    revurderingId: UUID,
                    attestant: NavIdentBruker.Attestant,
                ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.iverksett(revurderingId, attestant)
                }

                override fun underkjenn(
                    revurderingId: UUID,
                    attestering: Attestering.Underkjent,
                ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.underkjenn(revurderingId, attestering)
                }

                override fun fortsettEtterForhåndsvarsling(request: FortsettEtterForhåndsvarslingRequest): Either<FortsettEtterForhåndsvarselFeil, Revurdering> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.fortsettEtterForhåndsvarsling(request)
                }

                override fun leggTilUføregrunnlag(
                    request: LeggTilUførevurderingerRequest,
                ): Either<KunneIkkeLeggeTilGrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilUføregrunnlag(request)
                }

                override fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.behandlingId)
                    return services.revurdering.leggTilFradragsgrunnlag(request)
                }

                override fun leggTilBosituasjongrunnlag(request: LeggTilBosituasjongrunnlagRequest): Either<KunneIkkeLeggeTilBosituasjongrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.leggTilBosituasjongrunnlag(request)
                }

                override fun leggTilFormuegrunnlag(request: LeggTilFormuegrunnlagRequest): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse> {
                    assertHarTilgangTilRevurdering(request.revurderingId)
                    return services.revurdering.leggTilFormuegrunnlag(request)
                }

                override fun hentGjeldendeGrunnlagsdataOgVilkårsvurderinger(revurderingId: UUID): Either<KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger, HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse> {
                    assertHarTilgangTilRevurdering(revurderingId)
                    return services.revurdering.hentGjeldendeGrunnlagsdataOgVilkårsvurderinger(revurderingId)
                }
            },
            vedtakService = object : VedtakService {
                override fun lagre(vedtak: Vedtak) {
                    kastKanKunKallesFraAnnenService()
                }

                override fun hentAktiveFnr(fomDato: LocalDate): List<Fnr> {
                    return services.vedtakService.hentAktiveFnr(fomDato)
                }

                override fun kopierGjeldendeVedtaksdata(
                    sakId: UUID,
                    fraOgMed: LocalDate,
                ): Either<KunneIkkeKopiereGjeldendeVedtaksdata, GjeldendeVedtaksdata> {
                    kastKanKunKallesFraAnnenService()
                }

                override fun historiskGrunnlagForVedtaksperiode(
                    sakId: UUID,
                    vedtakId: UUID,
                ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataForVedtak, GjeldendeVedtaksdata> {
                    return services.vedtakService.historiskGrunnlagForVedtaksperiode(sakId, vedtakId)
                }
            },
            grunnlagService = object : GrunnlagService {
                override fun lagreFradragsgrunnlag(
                    behandlingId: UUID,
                    fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
                ) {
                    kastKanKunKallesFraAnnenService()
                }

                override fun lagreBosituasjongrunnlag(
                    behandlingId: UUID,
                    bosituasjongrunnlag: List<Grunnlag.Bosituasjon>,
                ) {
                    kastKanKunKallesFraAnnenService()
                }
            },
        )
    }

    /**
     * Denne skal kun brukes fra en annen service.
     * Når en service bruker en annen service, vil den ha den ikke-proxyede versjonen.
     * Vi kaster derfor her for å unngå at noen bruker metoden fra feil plass (som ville ha omgått tilgangssjekk).
     */
    private fun kastKanKunKallesFraAnnenService(): Nothing =
        throw IllegalStateException("This should only be called from another service")

    private fun assertHarTilgangTilPerson(fnr: Fnr) {
        services.person.sjekkTilgangTilPerson(fnr)
            .getOrHandle {
                throw Tilgangssjekkfeil(it, fnr)
            }
    }

    private fun assertHarTilgangTilSak(sakId: UUID) {
        personRepo.hentFnrForSak(sakId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilSøknad(søknadId: UUID) {
        personRepo.hentFnrForSøknad(søknadId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilBehandling(behandlingId: UUID) {
        personRepo.hentFnrForBehandling(behandlingId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilUtbetaling(utbetalingId: UUID30) {
        personRepo.hentFnrForUtbetaling(utbetalingId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilRevurdering(revurderingId: UUID) {
        personRepo.hentFnrForRevurdering(revurderingId)
            .forEach { assertHarTilgangTilPerson(it) }
    }

    private fun assertHarTilgangTilVedtak(vedtakId: UUID) {
        personRepo.hentFnrForVedtak(vedtakId)
            .forEach { assertHarTilgangTilPerson(it) }
    }
}

class Tilgangssjekkfeil(val feil: KunneIkkeHentePerson, val fnr: Fnr) : RuntimeException()
