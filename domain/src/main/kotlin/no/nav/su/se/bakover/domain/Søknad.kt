package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Søknad constructor(
        private val id: Long = NO_SUCH_IDENTITY,
        private val søknadInnhold: SøknadInnhold
) {
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