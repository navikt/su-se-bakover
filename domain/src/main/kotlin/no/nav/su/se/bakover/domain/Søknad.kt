package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.util.UUID

class Søknad(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val søknadInnhold: SøknadInnhold
) : PersistentDomainObject<VoidObserver>(id, opprettet), DtoConvertable<SøknadDto> {

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
