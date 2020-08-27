package no.nav.su.se.bakover.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import java.time.Instant
import java.util.UUID

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    val fnr: Fnr,
    private val søknader: MutableList<Søknad> = mutableListOf(),
    private val behandlinger: MutableList<Behandling> = mutableListOf(),
    private var oppdrag: Oppdrag? = null
) : PersistentDomainObject<SakPersistenceObserver>(), DtoConvertable<SakDto> {
    private val observers: MutableList<SakObserver> = mutableListOf()
    fun addObserver(observer: SakObserver) = observers.add(observer)

    override fun toDto() = SakDto(
        id,
        fnr = fnr,
        søknader = søknader.map { it.toDto() },
        behandlinger = behandlinger.map { it.toDto() },
        opprettet = opprettet,
        utbetalinger = oppdrag?.hentUtbetalinger() ?: emptyList()
    )

    fun nySøknad(søknadInnhold: SøknadInnhold): Søknad {
        val søknad = persistenceObserver.nySøknad(id, søknad = Søknad(søknadInnhold = søknadInnhold))
        søknader.add(søknad)
        val søknadDto = søknad.toDto()
        observers.filterIsInstance(SakEventObserver::class.java).forEach {
            it.nySøknadEvent(
                SakEventObserver.NySøknadEvent(
                    sakId = id,
                    søknadId = søknadDto.id,
                    søknadInnhold = søknadDto.søknadInnhold
                )
            )
        }
        return søknad
    }

    fun opprettSøknadsbehandling(søknadId: UUID): Behandling {
        val søknad = søknader.single { it.toDto().id == søknadId }
        val behandling = persistenceObserver.opprettSøknadsbehandling(id, Behandling(søknad = søknad, sakId = id))
        behandling.opprettVilkårsvurderinger()
        behandlinger.add(behandling)
        return behandling
    }

    fun simulerBehandling(
        behandlingId: UUID,
        simuleringClient: SimuleringClient
    ): Either<SimuleringFeilet, Behandling> {
        val behandling = behandlinger.find { it.toDto().id == behandlingId }!!
        val oppdragTilSimulering = opprettOppdragIfNotExist()
        val utbetalingTilSimulering =
            oppdragTilSimulering.generererUtbetaling(behandling.id, behandling.gjeldendeBeregning().hentPerioder())
        return simuleringClient.simulerOppdrag(utbetalingTilSimulering, fnr.toString()).map { simulering ->
            val oppdrag = oppdrag ?: persistenceObserver.opprettOppdrag(oppdragTilSimulering)
            val utbetaling = oppdrag.opprettUtbetaling(utbetalingTilSimulering)
            utbetaling.addSimulering(simulering)
            behandling.leggTilUtbetaling(utbetaling)
            behandling
        }
    }

    private fun opprettOppdragIfNotExist() = oppdrag ?: Oppdrag(sakId = id)

    fun sendTilAttestering(behandlingId: UUID, aktørId: AktørId, oppgave: OppgaveClient): Either<KunneIkkeOppretteOppgave, Long> {
        // val behandling = behandlinger.find { it.id == behandlingId }!!
        // TODO behandling.sendTilAttestering()
        println(behandlingId)
        return oppgave.opprettOppgave(
            OppgaveConfig.Attestering(
                journalpostId = "",
                sakId = this.id.toString(),
                aktørId = aktørId
            )
        )
    }
}

interface SakObserver

interface SakPersistenceObserver : PersistenceObserver {
    fun nySøknad(sakId: UUID, søknad: Søknad): Søknad
    fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling

    fun opprettOppdrag(oppdrag: Oppdrag): Oppdrag
}

interface SakEventObserver : SakObserver {
    fun nySøknadEvent(nySøknadEvent: NySøknadEvent) {}

    data class NySøknadEvent(
        val sakId: UUID,
        val søknadId: UUID,
        val søknadInnhold: SøknadInnhold
    )
}

data class SakDto(
    val id: UUID,
    val fnr: Fnr,
    val søknader: List<SøknadDto> = emptyList(),
    val behandlinger: List<BehandlingDto> = emptyList(),
    val opprettet: Instant,
    val utbetalinger: List<Utbetaling> = emptyList()
)
