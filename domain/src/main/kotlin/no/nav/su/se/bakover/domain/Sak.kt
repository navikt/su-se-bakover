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

class SakFactory(
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock = Clock.systemUTC()
) {
    fun nySak(fnr: Fnr): Sak {
        val opprettet = now(clock)
        val sakId = uuidFactory.newUUID()
        return Sak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            oppdrag = Oppdrag(
                id = uuidFactory.newUUID30(),
                opprettet = opprettet,
                sakId = sakId
            )
        )
    }
}
