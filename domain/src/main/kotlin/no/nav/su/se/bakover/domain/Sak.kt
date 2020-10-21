package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.time.Clock
import java.util.UUID

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val fnr: Fnr,
    private val søknader: MutableList<Søknad> = mutableListOf(),
    private val behandlinger: MutableList<Behandling> = mutableListOf(),
    val oppdrag: Oppdrag,
) {
    fun søknader() = søknader.toList()

    fun behandlinger() = behandlinger.toList()
}

data class NySak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val fnr: Fnr,
    val søknad: Søknad,
    val oppdrag: Oppdrag
) {
    fun toSak() = Sak(
        id = id,
        opprettet = opprettet,
        fnr = fnr,
        søknader = mutableListOf(søknad),
        behandlinger = mutableListOf(),
        oppdrag = oppdrag
    )
}

class SakFactory(
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock = Clock.systemUTC()
) {
    fun nySak(fnr: Fnr, søknadInnhold: SøknadInnhold): NySak {
        val opprettet = now(clock)
        val sakId = uuidFactory.newUUID()
        return NySak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            søknad = Søknad(
                id = uuidFactory.newUUID(),
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
                lukket = null
            ),
            oppdrag = Oppdrag(
                id = uuidFactory.newUUID30(),
                opprettet = opprettet,
                sakId = sakId
            )
        )
    }
}
