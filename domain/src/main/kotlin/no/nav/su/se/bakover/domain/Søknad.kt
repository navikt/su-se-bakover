package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.now
import java.util.UUID

data class Søknad(
    val sakId: UUID,
    val id: UUID = UUID.randomUUID(),
    val opprettet: Tidspunkt = now(),
    val søknadInnhold: SøknadInnhold,
    val trukket: Trukket? = null,
)

data class Trukket(
    val tidspunkt: Tidspunkt,
    val saksbehandler: Saksbehandler
)
