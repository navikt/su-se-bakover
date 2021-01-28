package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import org.slf4j.LoggerFactory
import java.time.Clock

class IverksettSaksbehandlingService(
    private val behandlingRepo: BehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val opprettVedtakssnapshotService: OpprettVedtakssnapshotService,
    private val behandlingMetrics: BehandlingMetrics,
    private val clock: Clock,
    private val microsoftGraphApiClient: MicrosoftGraphApiOppslag,
    private val journalførIverksettingService: JournalførIverksettingService,
    private val distribuerIverksettingsbrevService: DistribuerIverksettingsbrevService,
    private val saksbehandlingRepo: SaksbehandlingRepo
) {
    private val observers: MutableList<EventObserver> = mutableListOf()

    fun addObserver(observer: EventObserver) {
        observers.add(observer)
    }

    private val log = LoggerFactory.getLogger(this::class.java)

    internal fun iverksett(søknadsbehandling: Søknadsbehandling.Iverksatt): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling> {
        return when (søknadsbehandling) {
            is Søknadsbehandling.Iverksatt.Avslag -> TODO()
            is Søknadsbehandling.Iverksatt.Innvilget -> iverksettInnvilgning(søknadsbehandling)
            else -> throw RuntimeException("NEI") // TODO fix
        }
    }

    private fun iverksettInnvilgning(
        søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget
    ): Either<KunneIkkeIverksetteBehandling, Søknadsbehandling.Iverksatt.Utbetalt> {
        return utbetalingService.utbetal(
            sakId = søknadsbehandling.sakId,
            attestant = søknadsbehandling.attestering.attestant,
            beregning = søknadsbehandling.beregning,
            simulering = søknadsbehandling.simulering
        ).mapLeft {
            log.error("Kunne ikke innvilge behandling ${søknadsbehandling.id} siden utbetaling feilet. Feiltype: $it")
            when (it) {
                KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte -> KunneIkkeIverksetteBehandling.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte
                KunneIkkeUtbetale.Protokollfeil -> KunneIkkeIverksetteBehandling.KunneIkkeUtbetale
                KunneIkkeUtbetale.KunneIkkeSimulere -> KunneIkkeIverksetteBehandling.KunneIkkeKontrollsimulere
            }
        }.flatMap { oversendtUtbetaling ->
            val søknadsbehandlingMedUtbetaling = søknadsbehandling.tilUtbetalt(oversendtUtbetaling)

            saksbehandlingRepo.lagre(søknadsbehandlingMedUtbetaling)

            // TODO fix fix
            // opprettVedtakssnapshotService.opprettVedtak(
            //     vedtakssnapshot = Vedtakssnapshot.Innvilgelse.createFromBehandling(søknadsbehandling, oversendtUtbetaling)
            // )
            // behandlingMetrics.incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)

            log.info("Behandling ${søknadsbehandling.id} innvilget med utbetaling ${oversendtUtbetaling.id}")

            return søknadsbehandlingMedUtbetaling.right()
        }
    }
}
