package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.now
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.util.UUID

data class Søknad(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Instant = now(),
    private val søknadInnhold: SøknadInnhold
) : PersistentDomainObject<VoidObserver>(), DtoConvertable<SøknadDto> {

    override fun toDto() = SøknadDto(
        id = id,
        søknadInnhold = søknadInnhold,
        opprettet = opprettet
    )
}

data class SøknadDto(
    val id: UUID,
    val søknadInnhold: SøknadInnhold,
    val opprettet: Instant
)
