package no.nav.su.se.bakover.domain

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingFactory
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.time.Instant
import java.util.UUID

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Instant = now(),
    val fnr: Fnr,
    private val søknader: MutableList<Søknad> = mutableListOf(),
    private val behandlinger: MutableList<Behandling> = mutableListOf(),
    val oppdrag: Oppdrag,
) : PersistentDomainObject<SakPersistenceObserver>() {
    private val observers: MutableList<SakObserver> = mutableListOf()
    fun addObserver(observer: SakObserver) = observers.add(observer)

    fun søknader() = søknader.toList()

    fun behandlinger() = behandlinger.toList()

    fun nySøknad(søknadInnhold: SøknadInnhold): Søknad {
        val søknad = persistenceObserver.nySøknad(id, søknad = Søknad(søknadInnhold = søknadInnhold))
        søknader.add(søknad)
        observers.filterIsInstance(SakEventObserver::class.java).forEach {
            it.nySøknadEvent(
                SakEventObserver.NySøknadEvent(
                    sakId = id,
                    søknadId = søknad.id,
                    søknadInnhold = søknad.søknadInnhold
                )
            )
        }
        return søknad
    }

    fun opprettSøknadsbehandling(søknadId: UUID): Behandling {
        val søknad = søknader.single { it.id == søknadId }
        val behandling = persistenceObserver.opprettSøknadsbehandling(id, Behandling(søknad = søknad, sakId = id))
        behandlinger.add(behandling)
        return behandling
    }

    /**
     * Idempotent. Oppretter en ny stopp behandling dersom det ikke finnes en pågående.
     * Hvis en pågående finnes returneres den istedet.
     */
    fun stoppUtbetalinger(
        stoppbehandlingFactory: StoppbehandlingFactory,
        saksbehandler: Saksbehandler,
        stoppÅrsak: String
    ): Either<KunneIkkeOppretteStoppbehandling, Stoppbehandling> {
        return persistenceObserver.hentPågåendeStoppbehandling(id)?.right()
            ?: stoppbehandlingFactory.nyStoppbehandling(this, saksbehandler, stoppÅrsak)
                .mapLeft { KunneIkkeOppretteStoppbehandling }
                .map { persistenceObserver.nyStoppbehandling(it) }
    }

    object KunneIkkeOppretteStoppbehandling
}

interface SakObserver

interface SakPersistenceObserver : PersistenceObserver {
    fun nySøknad(sakId: UUID, søknad: Søknad): Søknad
    fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling

    fun nyStoppbehandling(nyBehandling: Stoppbehandling.Simulert): Stoppbehandling.Simulert

    /**
     * Verifiserer samtidig at vi kun har 0 eller 1 pågående stoppbehandling.
     * Foreløpig har vi bare en ferdig status: Stoppbehandling.Iverksatt
     */
    fun hentPågåendeStoppbehandling(sakId: UUID): Stoppbehandling?
}

interface SakEventObserver : SakObserver {
    fun nySøknadEvent(nySøknadEvent: NySøknadEvent) {}

    data class NySøknadEvent(
        val sakId: UUID,
        val søknadId: UUID,
        val søknadInnhold: SøknadInnhold
    )
}
