package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
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
import no.nav.su.se.bakover.domain.behandling.Revurdering
import no.nav.su.se.bakover.domain.behandling.SimulertRevurdering
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.KunneIkkeRevurdere
import no.nav.su.se.bakover.service.revurdering.RevurderingService
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
import no.nav.su.se.bakover.service.søknad.lukk.LukketSøknad
import no.nav.su.se.bakover.service.søknadsbehandling.FantIkkeBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillSøknadsbehandingIverksettingService
import no.nav.su.se.bakover.service.søknadsbehandling.HentBehandlingRequest
import no.nav.su.se.bakover.service.søknadsbehandling.IverksettSøknadsbehandlingRequest
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeOppdatereBehandlingsinformasjon
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.KunneIkkeUnderkjenneBehandling
import no.nav.su.se.bakover.service.søknadsbehandling.OppdaterSøknadsbehandlingsinformasjonRequest
import no.nav.su.se.bakover.service.søknadsbehandling.OpprettBeregningRequest
import no.nav.su.se.bakover.service.søknadsbehandling.OpprettBrevRequest
import no.nav.su.se.bakover.service.søknadsbehandling.OpprettSimuleringRequest
import no.nav.su.se.bakover.service.søknadsbehandling.OpprettSøknadsbehandlingRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.søknadsbehandling.UnderkjennSøknadsbehandlingRequest
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeGjenopptaUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeStanseUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.util.UUID

open class AccessCheckProxy(
    private val personRepo: PersonRepo,
    private val services: Services
) {
    fun proxy(): Services {
        return Services(
            avstemming = object : AvstemmingService {
                override fun avstemming(): Either<AvstemmingFeilet, Avstemming> {
                    return services.avstemming.avstemming()
                }

                override fun avstemming(
                    fraOgMed: Tidspunkt,
                    tilOgMed: Tidspunkt
                ): Either<AvstemmingFeilet, Avstemming> {
                    return services.avstemming.avstemming(fraOgMed, tilOgMed)
                }
            },
            utbetaling = object : UtbetalingService {
                override fun hentUtbetaling(utbetalingId: UUID30): Either<FantIkkeUtbetaling, Utbetaling> {
                    assertHarTilgangTilUtbetaling(utbetalingId)

                    return services.utbetaling.hentUtbetaling(utbetalingId)
                }

                override fun oppdaterMedKvittering(
                    avstemmingsnøkkel: Avstemmingsnøkkel,
                    kvittering: Kvittering
                ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> =
                    kastKanKunKallesFraAnnenService()

                override fun simulerUtbetaling(
                    sakId: UUID,
                    saksbehandler: NavIdentBruker,
                    beregning: Beregning
                ): Either<SimuleringFeilet, Utbetaling.SimulertUtbetaling> {
                    assertHarTilgangTilSak(sakId)

                    return services.utbetaling.simulerUtbetaling(sakId, saksbehandler, beregning)
                }

                override fun utbetal(
                    sakId: UUID,
                    attestant: NavIdentBruker,
                    beregning: Beregning,
                    simulering: Simulering
                ): Either<KunneIkkeUtbetale, Utbetaling.OversendtUtbetaling.UtenKvittering> {
                    assertHarTilgangTilSak(sakId)

                    return services.utbetaling.utbetal(sakId, attestant, beregning, simulering)
                }

                override fun stansUtbetalinger(
                    sakId: UUID,
                    saksbehandler: NavIdentBruker
                ): Either<KunneIkkeStanseUtbetalinger, Sak> {
                    assertHarTilgangTilSak(sakId)

                    return services.utbetaling.stansUtbetalinger(sakId, saksbehandler)
                }

                override fun gjenopptaUtbetalinger(
                    sakId: UUID,
                    saksbehandler: NavIdentBruker
                ): Either<KunneIkkeGjenopptaUtbetalinger, Sak> {
                    assertHarTilgangTilSak(sakId)

                    return services.utbetaling.gjenopptaUtbetalinger(sakId, saksbehandler)
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

                override fun opprettSak(sak: NySak) {
                    assertHarTilgangTilPerson(sak.fnr)

                    return services.sak.opprettSak(sak)
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

                override fun journalførBrev(
                    request: LagBrevRequest,
                    saksnummer: Saksnummer
                ) = kastKanKunKallesFraAnnenService()

                override fun distribuerBrev(journalpostId: JournalpostId) = kastKanKunKallesFraAnnenService()
            },
            lukkSøknad = object : LukkSøknadService {
                override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, LukketSøknad> {
                    assertHarTilgangTilSøknad(request.søknadId)

                    return services.lukkSøknad.lukkSøknad(request)
                }

                override fun lagBrevutkast(
                    request: LukkSøknadRequest
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
                override fun opprett(request: OpprettSøknadsbehandlingRequest): Either<KunneIkkeOppretteSøknadsbehandling, Søknadsbehandling> {
                    assertHarTilgangTilSøknad(request.søknadId)
                    return services.søknadsbehandling.opprett(request)
                }

                override fun vilkårsvurder(request: OppdaterSøknadsbehandlingsinformasjonRequest): Either<KunneIkkeOppdatereBehandlingsinformasjon, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.vilkårsvurder(request)
                }

                override fun beregn(request: OpprettBeregningRequest): Either<KunneIkkeBeregne, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.beregn(request)
                }

                override fun simuler(request: OpprettSimuleringRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.simuler(request)
                }

                override fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.sendTilAttestering(request)
                }

                override fun underkjenn(request: UnderkjennSøknadsbehandlingRequest): Either<KunneIkkeUnderkjenneBehandling, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.underkjenn(request)
                }

                override fun iverksett(request: IverksettSøknadsbehandlingRequest): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.iverksett(request)
                }

                override fun brev(request: OpprettBrevRequest): Either<KunneIkkeLageBrevutkast, ByteArray> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.brev(request)
                }

                override fun hent(request: HentBehandlingRequest): Either<FantIkkeBehandling, Søknadsbehandling> {
                    assertHarTilgangTilBehandling(request.behandlingId)
                    return services.søknadsbehandling.hent(request)
                }
            },
            ferdigstillSøknadsbehandingIverksettingService = object : FerdigstillSøknadsbehandingIverksettingService {
                override fun hentBehandlingForUtbetaling(utbetalingId: UUID30) = kastKanKunKallesFraAnnenService()

                override fun ferdigstillInnvilgelse(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) =
                    kastKanKunKallesFraAnnenService()

                override fun opprettManglendeJournalpostOgBrevdistribusjon(): FerdigstillSøknadsbehandingIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat {
                    // Dette er et driftsendepunkt og vi vil ikke returnere kode 6/7/person-sensitive data.
                    return services.ferdigstillSøknadsbehandingIverksettingService.opprettManglendeJournalpostOgBrevdistribusjon()
                }
            },
            revurdering = object : RevurderingService {
                override fun opprettRevurdering(
                    sakId: UUID,
                    periode: Periode,
                    saksbehandler: NavIdentBruker.Saksbehandler
                ): Either<KunneIkkeRevurdere, Revurdering> {
                    assertHarTilgangTilSak(sakId)
                    return services.revurdering.opprettRevurdering(sakId, periode, saksbehandler)
                }

                override fun beregnOgSimuler(
                    revurderingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    fradrag: List<Fradrag>
                ): Either<KunneIkkeRevurdere, SimulertRevurdering> {
                    assertHarTilgangTilSak(revurderingId)
                    return services.revurdering.beregnOgSimuler(
                        revurderingId = revurderingId,
                        saksbehandler = saksbehandler,
                        fradrag = fradrag
                    )
                }

                override fun sendTilAttestering(
                    revurderingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler
                ): Either<KunneIkkeRevurdere, Revurdering> {
                    assertHarTilgangTilSak(revurderingId)
                    return services.revurdering.sendTilAttestering(revurderingId, saksbehandler)
                }

                override fun lagBrevutkast(revurderingId: UUID, fritekst: String?): Either<KunneIkkeRevurdere, ByteArray> {
                    assertHarTilgangTilSak(revurderingId)
                    return services.revurdering.lagBrevutkast(revurderingId, fritekst)
                }
            }
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
}

class Tilgangssjekkfeil(val feil: KunneIkkeHentePerson, val fnr: Fnr) : RuntimeException()
