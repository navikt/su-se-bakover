package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold

class Søknad constructor(
    id: Long,
    private val søknadInnhold: SøknadInnhold
) : PersistentDomainObject<VoidObserver>(id) {
    fun toJson(): String = """
        {
            "id": $id,
            "json": ${søknadInnhold.toJson()}
        }
    """.trimIndent()

    fun nySøknadEvent(sakId: Long) = SakEventObserver.NySøknadEvent(
        sakId = sakId,
        søknadId = id,
        søknadInnhold = søknadInnhold
    )
}
