package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Saksbehandling
import no.nav.su.se.bakover.domain.behandling.Statusovergang
import no.nav.su.se.bakover.domain.behandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.behandling.IverksettSaksbehandlingService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.slf4j.LoggerFactory
import java.util.UUID

sealed class OpprettSaksbehandlingRequest {
    data class Søknadsbehandling(
        val søknadId: UUID
    ) : OpprettSaksbehandlingRequest()
}

sealed class OppdaterBehandlingsinformasjonRequest {
    data class Søknadsbehandling(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val behandlingsinformasjon: Behandlingsinformasjon
    ) : OppdaterBehandlingsinformasjonRequest()
}

sealed class OpprettBeregningRequest {
    data class Søknadsbehandling(
        val behandlingId: UUID,
        val periode: Periode,
        val fradrag: List<Fradrag>
    ) : OpprettBeregningRequest()
}

sealed class OpprettSimuleringRequest {
    data class Søknadsbehandling(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker
    ) : OpprettSimuleringRequest()
}

sealed class SendTilAttesteringRequest {
    data class SøknadsBehandling(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker
    ) : SendTilAttesteringRequest()
}

sealed class AttesterRequest {
    sealed class Søknadsbehandling : AttesterRequest() {
        data class Underkjenn(
            val behandlingId: UUID,
            val attestering: Attestering.Underkjent
        ) : Søknadsbehandling()

        data class Iverksett(
            val behandlingId: UUID,
            val attestant: NavIdentBruker.Attestant
        ) : Søknadsbehandling()
    }
}

interface SaksbehandlingService {
    fun opprett(request: OpprettSaksbehandlingRequest): Either<SaksbehandlingService.Feil, Saksbehandling>
    fun vilkårsvurder(request: OppdaterBehandlingsinformasjonRequest): Either<SaksbehandlingService.Feil, Saksbehandling>
    fun beregn(request: OpprettBeregningRequest): Either<SaksbehandlingService.Feil, Saksbehandling>
    fun simuler(request: OpprettSimuleringRequest): Either<SaksbehandlingService.Feil, Saksbehandling>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<SaksbehandlingService.Feil, Saksbehandling>
    fun attester(request: AttesterRequest): Either<SaksbehandlingService.Feil, Saksbehandling>

    object Feil
}

class SaksbehandlingServiceImpl(
    private val søknadService: SøknadService,
    private val søknadRepo: SøknadRepo,
    private val saksbehandlingRepo: SaksbehandlingRepo,
    private val utbetalingService: UtbetalingService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val iverksettSaksbehandlingService: IverksettSaksbehandlingService
) : SaksbehandlingService {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun opprett(request: OpprettSaksbehandlingRequest): Either<SaksbehandlingService.Feil, Saksbehandling> {
        // TODO errors
        // TODO limit "access" to applicable states
        return when (request) {
            is OpprettSaksbehandlingRequest.Søknadsbehandling -> {
                val søknad = søknadService.hentSøknad(request.søknadId).getOrElse {
                    return SaksbehandlingService.Feil.left()
                }
                if (søknad is Søknad.Lukket) {
                    return SaksbehandlingService.Feil.left()
                }
                if (søknad !is Søknad.Journalført.MedOppgave) {
                    // TODO Prøv å opprette oppgaven hvis den mangler? (systembruker blir kanskje mest riktig?)
                    return SaksbehandlingService.Feil.left()
                }
                if (søknadRepo.harSøknadPåbegyntBehandling(søknad.id)) {
                    // Dersom man legger til avslutting av behandlinger, må denne spørringa spesifiseres.
                    return SaksbehandlingService.Feil.left()
                }

                val opprettet = Saksbehandling.Søknadsbehandling.Opprettet(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    sakId = søknad.sakId,
                    søknad = søknad,
                    oppgaveId = søknad.oppgaveId,
                    fnr = søknad.søknadInnhold.personopplysninger.fnr
                )

                saksbehandlingRepo.lagre(opprettet)
                saksbehandlingRepo.hent(opprettet.id).right()
            }
        }
    }

    fun <T> forsøkStatusovergang(
        saksbehandling: Saksbehandling,
        statusovergang: Statusovergang<T>
    ): Either<SaksbehandlingService.Feil, T> {
        return try {
            saksbehandling.accept(statusovergang)
            statusovergang.get()
                .mapLeft { SaksbehandlingService.Feil } // TODO prolly do some mapping
                .map { it }
        } catch (exception: StatusovergangVisitor.UgyldigStatusovergangException) {
            SaksbehandlingService.Feil.left() // TODO prolly do some mapping
        }
    }

    override fun vilkårsvurder(request: OppdaterBehandlingsinformasjonRequest): Either<SaksbehandlingService.Feil, Saksbehandling> {
        return when (request) {
            is OppdaterBehandlingsinformasjonRequest.Søknadsbehandling -> {
                forsøkStatusovergang(
                    saksbehandling = saksbehandlingRepo.hent(request.behandlingId),
                    statusovergang = Statusovergang.TilVilkårsvurdert(request.behandlingsinformasjon)
                ).mapLeft {
                    it
                }.map {
                    saksbehandlingRepo.lagre(it)
                    saksbehandlingRepo.hent(request.behandlingId)
                }
            }
        }
    }

    override fun beregn(request: OpprettBeregningRequest): Either<SaksbehandlingService.Feil, Saksbehandling> {
        // TODO attestant og saksbehandler kan ikke være samme person.. but why?
        // TODO limit "access" to applicable states
        return when (request) {
            is OpprettBeregningRequest.Søknadsbehandling -> {
                forsøkStatusovergang(
                    saksbehandling = saksbehandlingRepo.hent(request.behandlingId),
                    statusovergang = Statusovergang.TilBeregnet(
                        periode = request.periode,
                        fradrag = request.fradrag
                    )
                ).mapLeft {
                    it
                }.map {
                    saksbehandlingRepo.lagre(it)
                    saksbehandlingRepo.hent(request.behandlingId)
                }
            }
        }
    }

    override fun simuler(request: OpprettSimuleringRequest): Either<SaksbehandlingService.Feil, Saksbehandling> {
        // TODO attestant og saksbehandler kan ikke være samme person.. but why?
        // TODO limit "access" to applicable states
        return when (request) {
            is OpprettSimuleringRequest.Søknadsbehandling -> {
                forsøkStatusovergang(
                    saksbehandling = saksbehandlingRepo.hent(request.behandlingId),
                    statusovergang = Statusovergang.TilSimulert(request.saksbehandler) { uuid: UUID, navIdentBruker: NavIdentBruker, beregning: Beregning ->
                        utbetalingService.simulerUtbetaling(uuid, navIdentBruker, beregning)
                    }
                ).mapLeft {
                    it
                }.map {
                    saksbehandlingRepo.lagre(it)
                    saksbehandlingRepo.hent(request.behandlingId)
                }
            }
        }
    }

    override fun sendTilAttestering(request: SendTilAttesteringRequest): Either<SaksbehandlingService.Feil, Saksbehandling> {
        return when (request) {
            is SendTilAttesteringRequest.SøknadsBehandling -> {
                when (val opprinnelig = saksbehandlingRepo.hent(request.behandlingId)) {
                    is Saksbehandling.Søknadsbehandling.Simulert -> {
                        val aktørId = personService.hentAktørId(opprinnelig.fnr).getOrElse {
                            log.error("Fant ikke aktør-id med for fødselsnummer : ${opprinnelig.fnr}")
                            return SaksbehandlingService.Feil.left() // TODO errors
                        }
                        val eksisterendeOppgaveId: OppgaveId = opprinnelig.oppgaveId

                        val nyOppgaveId: OppgaveId = oppgaveService.opprettOppgave(
                            OppgaveConfig.Attestering(
                                opprinnelig.søknad.id,
                                aktørId = aktørId,
                                // TODO how to handle til "loop" of til attestering -> saksbehandling? possible: allow some null values?
                                // Første gang den sendes til attestering er attestant null, de påfølgende gangene vil den være attestanten som har underkjent.
                                tilordnetRessurs = null
                            )
                        ).getOrElse {
                            log.error("Kunne ikke opprette Attesteringsoppgave. Avbryter handlingen.")
                            return SaksbehandlingService.Feil.left() // TODO errors
                        }.also {
                            // TODO metrics behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.OPPRETTET_OPPGAVE)
                        }

                        saksbehandlingRepo.lagre(
                            Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget(
                                id = opprinnelig.id,
                                opprettet = opprinnelig.opprettet,
                                sakId = opprinnelig.sakId,
                                søknad = opprinnelig.søknad,
                                oppgaveId = nyOppgaveId,
                                behandlingsinformasjon = opprinnelig.behandlingsinformasjon,
                                beregning = opprinnelig.beregning,
                                simulering = opprinnelig.simulering,
                                saksbehandler = request.saksbehandler,
                                fnr = opprinnelig.fnr,
                            )
                        )

                        oppgaveService.lukkOppgave(eksisterendeOppgaveId).map {
                            // TODO metrics behandlingMetrics.incrementTilAttesteringCounter(BehandlingMetrics.TilAttesteringHandlinger.LUKKET_OPPGAVE)
                        }.mapLeft {
                            log.error("Klarte ikke å lukke oppgave. kall til oppgave for oppgaveId ${opprinnelig.oppgaveId} feilet")
                        }

                        saksbehandlingRepo.hent(opprinnelig.id).right()
                    }
                    else -> throw NotImplementedError()
                }
            }
        }
    }

    override fun attester(request: AttesterRequest): Either<SaksbehandlingService.Feil, Saksbehandling> {
        return when (request) {
            is AttesterRequest.Søknadsbehandling.Underkjenn -> {
                when (val opprinnelig = saksbehandlingRepo.hent(request.behandlingId)) {
                    is Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget -> {
                        saksbehandlingRepo.lagre(
                            Saksbehandling.Søknadsbehandling.Attestert.Underkjent(
                                id = opprinnelig.id,
                                opprettet = opprinnelig.opprettet,
                                sakId = opprinnelig.sakId,
                                søknad = opprinnelig.søknad,
                                oppgaveId = opprinnelig.oppgaveId,
                                behandlingsinformasjon = opprinnelig.behandlingsinformasjon,
                                beregning = opprinnelig.beregning,
                                simulering = opprinnelig.simulering,
                                saksbehandler = opprinnelig.saksbehandler,
                                fnr = opprinnelig.fnr,
                                attestering = request.attestering
                            )
                        )
                        saksbehandlingRepo.hent(opprinnelig.id).right()
                    }
                    else -> throw NotImplementedError()
                }
            }
            is AttesterRequest.Søknadsbehandling.Iverksett -> {
                when (val opprinnelig = saksbehandlingRepo.hent(request.behandlingId)) {
                    is Saksbehandling.Søknadsbehandling.TilAttestering.Innvilget -> {
                        iverksettSaksbehandlingService.iverksett(request)
                            .mapLeft { SaksbehandlingService.Feil }
                            .map { it }
                    }
                    else -> throw NotImplementedError()
                }
            }
        }
    }
}
