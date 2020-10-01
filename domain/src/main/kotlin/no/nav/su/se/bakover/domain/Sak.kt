package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.util.UUID

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val fnr: Fnr,
    private val søknader: MutableList<Søknad> = mutableListOf(),
    private val behandlinger: MutableList<Behandling> = mutableListOf(),
    val oppdrag: Oppdrag,
) {

    private val observers: MutableList<SakObserver> = mutableListOf()

    fun addObserver(observer: SakObserver) = observers.add(observer)

    fun søknader() = søknader.toList()

    fun behandlinger() = behandlinger.toList()

    // TODO get rid of observer - fix in service
    fun nySøknad(søknad: Søknad): Søknad {
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
}

interface SakObserver

interface SakEventObserver : SakObserver {
    fun nySøknadEvent(nySøknadEvent: NySøknadEvent) {}

    data class NySøknadEvent(
        val sakId: UUID,
        val søknadId: UUID,
        val søknadInnhold: SøknadInnhold
    )
}
