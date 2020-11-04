package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.person.PersonRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemming
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.avstemming.AvstemmingFeilet
import no.nav.su.se.bakover.service.avstemming.AvstemmingService
import no.nav.su.se.bakover.service.behandling.BehandlingService
import no.nav.su.se.bakover.service.behandling.FantIkkeBehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeLageBrevutkast
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppretteSøknadsbehandling
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.oppdrag.FantIkkeOppdrag
import no.nav.su.se.bakover.service.oppdrag.OppdragService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.LukkSøknadService
import no.nav.su.se.bakover.service.utbetaling.FantIkkeUtbetaling
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeGjenopptaUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeStanseUtbetalinger
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.LocalDate
import java.util.UUID

class AccessCheckProxy(
    private val personRepo: PersonRepo,
    private val clients: Clients
) {
    fun proxy(services: Services): Services {
        return Services(
            avstemming = object : AvstemmingService {
                override fun avstemming(): Either<AvstemmingFeilet, Avstemming> {
                    return services.avstemming.avstemming()
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
                ) = kastKanKunKallesFraAnnenService()

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
            oppdrag = object : OppdragService {
                override fun hentOppdrag(sakId: UUID): Either<FantIkkeOppdrag, Oppdrag> {
                    assertHarTilgangTilSak(sakId)

                    return services.oppdrag.hentOppdrag(sakId)
                }
            },
            behandling = object : BehandlingService {
                override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.hentBehandling((behandlingId))
                }

                override fun underkjenn(
                    begrunnelse: String,
                    attestant: NavIdentBruker.Attestant,
                    behandling: Behandling
                ): Either<Behandling.KunneIkkeUnderkjenne, Behandling> {
                    assertHarTilgangTilPerson(behandling.fnr)

                    return services.behandling.underkjenn(begrunnelse, attestant, behandling)
                }

                override fun oppdaterBehandlingsinformasjon(
                    behandlingId: UUID,
                    behandlingsinformasjon: Behandlingsinformasjon
                ): Behandling {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.oppdaterBehandlingsinformasjon(behandlingId, behandlingsinformasjon)
                }

                override fun opprettBeregning(
                    behandlingId: UUID,
                    fraOgMed: LocalDate,
                    tilOgMed: LocalDate,
                    fradrag: List<Fradrag>
                ): Behandling {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.opprettBeregning(behandlingId, fraOgMed, tilOgMed, fradrag)
                }

                override fun simuler(
                    behandlingId: UUID,
                    saksbehandler: NavIdentBruker
                ): Either<SimuleringFeilet, Behandling> {
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
                ): Either<Behandling.KunneIkkeIverksetteBehandling, Behandling> {
                    assertHarTilgangTilBehandling(behandlingId)

                    return services.behandling.iverksett(behandlingId, attestant)
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
                    assertHarTilgangTilPerson(fnr)

                    return services.sak.hentSak(fnr)
                }

                override fun opprettSak(sak: NySak) {
                    assertHarTilgangTilPerson(sak.fnr)

                    return services.sak.opprettSak(sak)
                }
            },
            søknad = object : SøknadService {
                override fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Sak> {
                    assertHarTilgangTilPerson(søknadInnhold.personopplysninger.fnr)

                    return services.søknad.nySøknad(søknadInnhold)
                }

                override fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad> {
                    assertHarTilgangTilSøknad(søknadId)

                    return services.søknad.hentSøknad(søknadId)
                }
            },
            brev = object : BrevService {
                override fun lagBrev(request: LagBrevRequest): Either<KunneIkkeLageBrev, ByteArray> {
                    assertHarTilgangTilPerson(request.getFnr())

                    return services.brev.lagBrev(request)
                }

                override fun journalførBrev(
                    request: LagBrevRequest,
                    sakId: UUID
                ): Either<KunneIkkeJournalføreBrev, JournalpostId> {
                    assertHarTilgangTilSak(sakId)

                    return services.brev.journalførBrev(request, sakId)
                }

                override fun distribuerBrev(journalpostId: JournalpostId): Either<KunneIkkeDistribuereBrev, String> {
                    return services.brev.distribuerBrev(journalpostId)
                }
            },
            lukkSøknad = object : LukkSøknadService {
                override fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, Sak> {
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
                override fun ferdigstillAttesteringsoppgave(aktørId: AktørId) = kastKanKunKallesFraAnnenService()
                override fun lukkOppgave(oppgaveId: OppgaveId) = kastKanKunKallesFraAnnenService()
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
        clients.personOppslag.person(fnr)
            .getOrHandle { throw Tilgangssjekkfeil(it, fnr) }
    }

    private fun assertHarTilgangTilSak(sakId: UUID) {
        val fnr = personRepo.hentFnrForSak(sakId)
            ?: return

        assertHarTilgangTilPerson(fnr)
    }

    private fun assertHarTilgangTilSøknad(søknadId: UUID) {
        val fnr = personRepo.hentFnrForSøknad(søknadId)
            ?: return

        assertHarTilgangTilPerson(fnr)
    }

    private fun assertHarTilgangTilBehandling(behandlingId: UUID) {
        val fnr = personRepo.hentFnrForBehandling(behandlingId)
            ?: return

        assertHarTilgangTilPerson(fnr)
    }

    private fun assertHarTilgangTilUtbetaling(utbetalingId: UUID30) {
        val fnr = personRepo.hentFnrForUtbetaling(utbetalingId)
            ?: return

        assertHarTilgangTilPerson(fnr)
    }
}

class Tilgangssjekkfeil(val pdlFeil: PdlFeil, val fnr: Fnr) : RuntimeException(pdlFeil.message)
