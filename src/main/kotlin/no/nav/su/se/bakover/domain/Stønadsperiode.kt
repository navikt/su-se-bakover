package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.db.Repository

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

internal class Stønadsperiode(
        private var id: Long = NO_SUCH_IDENTITY,
        private val søknadFactory: SøknadFactory
) {
    private lateinit var søknad: Søknad

    init {
        if (id != NO_SUCH_IDENTITY) søknad = søknadFactory.forStønadsperiode(id)
    }

    fun lagre(sakId: Long, søknad: Pair<Long, Søknad>, repository: Repository) = this.also {
        this.søknad = søknad.second
        id = repository.lagreStønadsperiode(sakId, søknad.first)
    }

    fun toJson() = """
        {
            "id":$id,
            "søknad": ${søknad.toJson()}
        }
    """.trimIndent()
}

internal class StønadsperiodeFactory(
        private val repository: Repository,
        private val søknadFactory: SøknadFactory
) {
    fun nyStønadsperiode(sakId: Long, søknadInnhold: SøknadInnhold): Stønadsperiode {
        return Stønadsperiode(søknadFactory = søknadFactory)
                .lagre(sakId, søknadFactory.nySøknad(sakId, søknadInnhold), repository)
    }

    fun forSak(sakId: Long): List<Stønadsperiode> = repository.stønadsperioderForSak(sakId)
            .map { Stønadsperiode(id = it, søknadFactory = søknadFactory) }
}