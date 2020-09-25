package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.MicroInstant
import no.nav.su.se.bakover.common.now
import java.util.UUID

data class Søknad(
    val id: UUID = UUID.randomUUID(),
    val opprettet: MicroInstant = now(),
    val søknadInnhold: SøknadInnhold
) : PersistentDomainObject<VoidObserver>()
