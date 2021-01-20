package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
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
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
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
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.FantIkkeBehandling
import no.nav.su.se.bakover.service.behandling.IverksattBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.behandling.KunneIkkeIverksetteBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppdatereBehandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.behandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeUnderkjenneBehandling
import no.nav.su.se.bakover.service.behandling.OpprettManglendeJournalpostOgBrevdistribusjonResultat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
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
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeGjenopptaUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeStanseUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.LocalDate
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
                ): Either<FantIkkeUtbetaling, Utbetaling.OversendtUtbetaling.MedKvittering> = kastKanKunKallesFraAnnenService()

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
            behandling = object : BehandlingService {
                override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.hentBehandling((behandlingId))
                }

                override fun hentBehandlingForUtbetaling(utbetalingId: UUID30) = kastKanKunKallesFraAnnenService()

                override fun underkjenn(
                    behandlingId: UUID,
                    attestering: Attestering.Underkjent
                ): Either<KunneIkkeUnderkjenneBehandling, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.underkjenn(behandlingId, attestering)
                }

                override fun oppdaterBehandlingsinformasjon(
                    behandlingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    behandlingsinformasjon: Behandlingsinformasjon
                ): Either<KunneIkkeOppdatereBehandlingsinformasjon, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.oppdaterBehandlingsinformasjon(
                        behandlingId,
                        saksbehandler,
                        behandlingsinformasjon
                    )
                }

                override fun opprettBeregning(
                    behandlingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler,
                    fraOgMed: LocalDate,
                    tilOgMed: LocalDate,
                    fradrag: List<Fradrag>
                ): Either<KunneIkkeBeregne, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.opprettBeregning(
                        behandlingId,
                        saksbehandler,
                        fraOgMed,
                        tilOgMed,
                        fradrag
                    )
                }

                override fun simuler(
                    behandlingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler
                ): Either<KunneIkkeSimulereBehandling, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.simuler(behandlingId, saksbehandler)
                }

                override fun sendTilAttestering(
                    behandlingId: UUID,
                    saksbehandler: NavIdentBruker.Saksbehandler
                ): Either<KunneIkkeSendeTilAttestering, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.sendTilAttestering(behandlingId, saksbehandler)
                }

                override fun iverksett(
                    behandlingId: UUID,
                    attestant: NavIdentBruker.Attestant
                ): Either<KunneIkkeIverksetteBehandling, IverksattBehandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.iverksett(behandlingId, attestant)
                }

                override fun ferdigstillInnvilgelse(behandling: Behandling) {
                    kastKanKunKallesFraAnnenService()
                }

                override fun opprettManglendeJournalpostOgBrevdistribusjon(): OpprettManglendeJournalpostOgBrevdistribusjonResultat {
                    // Dette er et driftsendepunkt og vi vil ikke returnere kode 6/7/person-sensitive data.

                    return services.behandling.opprettManglendeJournalpostOgBrevdistribusjon()
                }

                override fun opprettSøknadsbehandling(
                    søknadId: UUID
                ): Either<KunneIkkeOppretteSøknadsbehandling, Behandling> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.behandling.opprettSøknadsbehandling(søknadId)
                }

                override fun lagBrevutkast(behandlingId: UUID): Either<KunneIkkeLageBrevutkast, ByteArray> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.lagBrevutkast(behandlingId)
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
            },
            person = object : PersonService {
                override fun hentPerson(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
                    assertHarTilgangTilPerson(fnr)

                    return services.person.hentPerson(fnr)
                }

                override fun hentPersonForSystembruker(fnr: Fnr): Either<KunneIkkeHentePerson, Person> {
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
}

class Tilgangssjekkfeil(val feil: KunneIkkeHentePerson, val fnr: Fnr) : RuntimeException()
