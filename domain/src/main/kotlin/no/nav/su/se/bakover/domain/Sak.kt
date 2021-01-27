package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonValue
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.domain.behandling.Saksbehandling
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import java.time.Clock
import java.util.UUID

data class Saksnummer(@JsonValue val nummer: Long) {
    override fun toString() = nummer.toString()
}

data class Sak(
    val id: UUID = UUID.randomUUID(),
    val saksnummer: Saksnummer,
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val fnr: Fnr,
    private val søknader: List<Søknad> = emptyList(),
    private val behandlinger: List<Saksbehandling> = emptyList(),
    val utbetalinger: List<Utbetaling>,
) {
    fun søknader() = søknader.toList()

    fun behandlinger() = behandlinger.toList()
}

data class NySak(
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = Tidspunkt.now(),
    val fnr: Fnr,
    val søknad: Søknad.Ny,
)

class SakFactory(
    private val uuidFactory: UUIDFactory = UUIDFactory(),
    private val clock: Clock,
) {
    fun nySak(fnr: Fnr, søknadInnhold: SøknadInnhold): NySak {
        val opprettet = Tidspunkt.now(clock)
        val sakId = uuidFactory.newUUID()
        return NySak(
            id = sakId,
            fnr = fnr,
            opprettet = opprettet,
            søknad = Søknad.Ny(
                id = uuidFactory.newUUID(),
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
            )
        )
    }
}
