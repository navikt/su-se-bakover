package no.nav.su.se.bakover.domain

import arrow.core.Either
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.OppdragFactory
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import java.time.Instant
import java.util.UUID

data class Sak(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Instant = now(),
    private val fnr: Fnr,
    private val søknader: MutableList<Søknad> = mutableListOf(),
    private val behandlinger: MutableList<Behandling> = mutableListOf(),
    private val oppdrag: MutableList<Oppdrag> = mutableListOf()
) : PersistentDomainObject<SakPersistenceObserver>(), DtoConvertable<SakDto> {
    private val observers: MutableList<SakObserver> = mutableListOf()
    fun addObserver(observer: SakObserver) = observers.add(observer)

    override fun toDto() = SakDto(
        id,
        fnr = fnr,
        søknader = søknader.map { it.toDto() },
        behandlinger = behandlinger.map { it.toDto() },
        opprettet = opprettet,
        oppdrag = oppdrag
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
        val behandling = persistenceObserver.opprettSøknadsbehandling(id, Behandling(søknad = søknad))
        behandling.opprettVilkårsvurderinger()
        behandlinger.add(behandling)
        return behandling
    }

    fun fullførBehandling(behandlingId: UUID, simuleringClient: SimuleringClient): Either<SimuleringFeilet, Behandling> {
        val behandling = behandlinger.find { it.toDto().id == behandlingId }!!
        val oppdragTilSimulering = opprettOppdrag(behandling)
        return simuleringClient.simulerOppdrag(oppdragTilSimulering).map {
            val oppdrag = persistenceObserver.opprettOppdrag(oppdragTilSimulering)
            oppdrag.addSimulering(it)
            this.oppdrag.add(oppdrag)
            behandling.addOppdrag(oppdrag)
            behandling
        }
    }

    private fun opprettOppdrag(behandling: Behandling): Oppdrag {
        return OppdragFactory(
            behandling = behandling.genererOppdragsinformasjon(),
            sak = genererOppdragsinformasjon()
        ).build()
    }

    internal data class SakOppdragsinformasjon(
        val sakId: UUID,
        val sisteOppdrag: Oppdrag?,
        val fnr: String
    ) {
        fun hasOppdrag() = sisteOppdrag != null
    }

    fun sisteOppdrag() = oppdrag.lastOrNull()

    private fun genererOppdragsinformasjon() = SakOppdragsinformasjon(
        sakId = id,
        sisteOppdrag = sisteOppdrag(), // TODO: Hvordan skal vi velge oppdrag? Er oppdraget simulert?  attestert? ferdig? utbetalt?
        fnr = fnr.toString()
    )
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
    val oppdrag: List<Oppdrag> = emptyList()
)
