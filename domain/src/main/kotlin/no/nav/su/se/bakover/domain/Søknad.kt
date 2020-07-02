package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.domain.dto.DtoConvertable

class Søknad constructor(
    id: Long,
    private val søknadInnhold: SøknadInnhold
) : PersistentDomainObject<VoidObserver>(id), DtoConvertable<SøknadDto> {

    override fun toDto() = SøknadDto(
        id = id,
        søknadInnhold = søknadInnhold
    )

    fun nySøknadEvent(sakId: Long) = SakEventObserver.NySøknadEvent(
        sakId = sakId,
        søknadId = id,
        søknadInnhold = søknadInnhold
    )
}

data class SøknadDto(
    val id: Long,
    val søknadInnhold: SøknadInnhold
)
