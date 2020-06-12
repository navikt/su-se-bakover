package no.nav.su.se.bakover


private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Stønadsperiode(
        private var id: Long = NO_SUCH_IDENTITY,
        private val søknad: Søknad
) : Persistent {

    override fun id(): Long = id

    fun toJson() = """
        {
            "id":$id,
            "søknad": ${søknad.toJson()}
        }
    """.trimIndent()
}

class StønadsperiodeFactory(
        private val stønadsperiodeRepo: StønadsperiodeRepo,
        private val søknadFactory: SøknadFactory
) {
    fun nyStønadsperiode(sak: Sak, søknad: Søknad) = stønadsperiodeRepo.lagreStønadsperiode(sak.id(), søknad.id())

    fun forSak(sakId: Long): List<Stønadsperiode> = stønadsperiodeRepo.stønadsperioderForSak(sakId)
            .map {
                Stønadsperiode(
                        id = it,
                        søknad = søknadFactory.forStønadsperiode(it)
                )
            }
}

interface StønadsperiodeRepo {
    fun lagreStønadsperiode(sakId: Long, søknadId: Long): Long
    fun stønadsperioderForSak(sakId: Long): List<Long>
}