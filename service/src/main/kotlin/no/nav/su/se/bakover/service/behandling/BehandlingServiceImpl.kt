package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.beregning.BeregningRepo
import no.nav.su.se.bakover.database.hendelseslogg.HendelsesloggRepo
import no.nav.su.se.bakover.database.oppdrag.OppdragRepo
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering.InternFeil
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering.UgyldigKombinasjonSakOgBehandling
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

internal class BehandlingServiceImpl(
    private val behandlingRepo: BehandlingRepo,
    private val hendelsesloggRepo: HendelsesloggRepo,
    private val beregningRepo: BeregningRepo,
    private val oppdragRepo: OppdragRepo,
    private val utbetalingService: UtbetalingService,
    private val oppgaveClient: OppgaveClient, // TODO use services or repos? probably services
    private val søknadService: SøknadService,
    private val sakService: SakService,
    private val personOppslag: PersonOppslag
) : BehandlingService {
    override fun hentBehandling(behandlingId: UUID): Either<FantIkkeBehandling, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)?.right() ?: FantIkkeBehandling.left()
    }

    val log = LoggerFactory.getLogger(this::class.java)

    override fun underkjenn(
        begrunnelse: String,
        attestant: NavIdentBruker.Attestant,
        behandling: Behandling
    ): Either<Behandling.KunneIkkeUnderkjenne, Behandling> {
        return behandling.underkjenn(begrunnelse, attestant)
            .map {
                behandlingRepo.oppdaterBehandlingStatus(it.id, it.status())
                hendelsesloggRepo.oppdaterHendelseslogg(it.hendelseslogg)
                behandlingRepo.hentBehandling(it.id)!!
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun oppdaterBehandlingsinformasjon(
        behandlingId: UUID,
        behandlingsinformasjon: Behandlingsinformasjon
    ): Behandling {
        return behandlingRepo.hentBehandling(behandlingId)!!
            .oppdaterBehandlingsinformasjon(behandlingsinformasjon) // invoke first to perform state-check
            .let {
                beregningRepo.slettBeregningForBehandling(behandlingId)
                behandlingRepo.oppdaterBehandlingsinformasjon(behandlingId, it.behandlingsinformasjon())
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun opprettBeregning(
        behandlingId: UUID,
        fraOgMed: LocalDate,
        tilOgMed: LocalDate,
        fradrag: List<Fradrag>
    ): Behandling {
        return behandlingRepo.hentBehandling(behandlingId)!!
            .opprettBeregning(fraOgMed, tilOgMed, fradrag) // invoke first to perform state-check
            .let {
                beregningRepo.slettBeregningForBehandling(behandlingId)
                beregningRepo.opprettBeregningForBehandling(behandlingId, it.beregning()!!)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, it.status())
                it
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun simuler(behandlingId: UUID): Either<SimuleringFeilet, Behandling> {
        val behandling = behandlingRepo.hentBehandling(behandlingId)!!
        val utbetalingTilSimulering =
            utbetalingService.lagUtbetaling(
                behandling.sakId,
                Oppdrag.UtbetalingStrategy.Ny(
                    behandler = NavIdentBruker.Attestant("SU"), // TODO pass actual
                    beregning = behandling.beregning()!!
                )
            )
        return utbetalingService.simulerUtbetaling(utbetalingTilSimulering)
            .map { simulertUtbetaling ->
                behandling.leggTilSimulering(simulertUtbetaling.simulering)
                behandlingRepo.leggTilSimulering(behandlingId, simulertUtbetaling.simulering)
                behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
                behandlingRepo.hentBehandling(behandlingId)!!
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun sendTilAttestering(
        sakId: UUID,
        behandlingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler
    ): Either<KunneIkkeSendeTilAttestering, Behandling> {
        val sak = sakService.hentSak(sakId).getOrElse {
            log.info("Fant ikke sak med sakId : $sakId")
            return UgyldigKombinasjonSakOgBehandling.left()
        }

        val behandling = sak.behandlinger()
            .firstOrNull { it.id == behandlingId }
            ?.let { it.sendTilAttestering(saksbehandler) }
            ?: return UgyldigKombinasjonSakOgBehandling.left()
                .also { log.info("Fant ikke behandling $behandlingId på sak med id $sakId") }

        val aktørId = personOppslag.aktørId(sak.fnr).getOrElse {
            log.warn("Fant ikke aktør-id med for fødselsnummer : ${sak.fnr}")
            return KunneIkkeFinneAktørId.left()
        }

        oppgaveClient.opprettOppgave(
            OppgaveConfig.Attestering(
                behandling.sakId.toString(),
                aktørId = aktørId
            )
        ).mapLeft {
            log.error("Kunne ikke opprette Attestering oppgave")
            return InternFeil.left()
        }

        behandlingRepo.settSaksbehandler(behandlingId, saksbehandler)
        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())

        oppgaveClient.ferdigstillFørstegangsOppgave(
            aktørId = aktørId
        )
        return behandling.right()
    }

    // TODO need to define responsibilities for domain and services.
    // TODO refactor the beast
    override fun iverksett(
        behandlingId: UUID,
        attestant: NavIdentBruker.Attestant
    ): Either<Behandling.IverksettFeil, Behandling> {
        return behandlingRepo.hentBehandling(behandlingId)!!.iverksett(attestant) // invoke first to perform state-check
            .map { behandling ->
                return when (behandling.status()) {
                    Behandling.BehandlingsStatus.IVERKSATT_AVSLAG -> {
                        behandlingRepo.attester(behandlingId, attestant)
                        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
                        behandling.right()
                    }
                    Behandling.BehandlingsStatus.IVERKSATT_INNVILGET -> {
                        val utbetaling = utbetalingService.lagUtbetaling(
                            sakId = behandling.sakId,
                            strategy = Oppdrag.UtbetalingStrategy.Ny(
                                behandler = attestant,
                                beregning = behandling.beregning()!!
                            ),
                        )
                        // TODO move this to utbetalingservice probably
                        return utbetalingService.simulerUtbetaling(utbetaling)
                            .mapLeft { return Behandling.IverksettFeil.KunneIkkeSimulere().left() }
                            .map { simulertUtbetaling ->
                                if (simulertUtbetaling.simulering != behandling.simulering()!!) return Behandling.IverksettFeil.InkonsistentSimuleringsResultat()
                                    .left()

                                return utbetalingService.utbetal(utbetaling = simulertUtbetaling)
                                    .mapLeft {
                                        return Behandling.IverksettFeil.Utbetaling().left()
                                    }.map { oversendtUtbetaling ->
                                        behandlingRepo.leggTilUtbetaling(
                                            behandlingId = behandlingId,
                                            utbetalingId = oversendtUtbetaling.id
                                        )
                                        behandlingRepo.attester(behandlingId, attestant)
                                        behandlingRepo.oppdaterBehandlingStatus(behandlingId, behandling.status())
                                        return behandling.right()
                                    }
                            }
                    }
                    else -> throw Behandling.TilstandException(
                        state = behandling.status(),
                        operation = behandling::iverksett.toString()
                    )
                }
            }
    }

    // TODO need to define responsibilities for domain and services.
    override fun opprettSøknadsbehandling(
        sakId: UUID,
        søknadId: UUID
    ): Either<KunneIkkeOppretteSøknadsbehandling, Behandling> {
        // TODO: sjekk at det ikke finnes eksisterende behandling som ikke er avsluttet
        // TODO: + sjekk at søknad ikke er lukket
        return søknadService.hentSøknad(søknadId)
            .map {
                behandlingRepo.opprettSøknadsbehandling(
                    sakId,
                    Behandling(
                        sakId = sakId,
                        søknad = it
                    )
                )
            }.mapLeft {
                KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad
            }
    }
}
