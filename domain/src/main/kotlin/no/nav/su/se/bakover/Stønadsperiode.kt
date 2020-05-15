package no.nav.su.se.bakover

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.SøknadObserver.SøknadMottattEvent

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Stønadsperiode(
    private var id: Long = NO_SUCH_IDENTITY,
    private val søknadFactory: SøknadFactory,
    private val repository: Repository
) : SøknadObserver {
    private lateinit var søknad: Søknad

    init {
        if (id != NO_SUCH_IDENTITY) søknad = søknadFactory.forStønadsperiode(id)
    }

    fun nySøknad(sakId: Long, søknadInnhold: SøknadInnhold) = this.also {
        søknad = søknadFactory.nySøknad(sakId, søknadInnhold, this)
    }

    fun toJson() = """
        {
            "id":$id,
            "søknad": ${søknad.toJson()}
        }
    """.trimIndent()

    override fun søknadMottatt(event: SøknadMottattEvent) {
        repository.lagreStønadsperiode(sakId = event.sakId, søknadId = event.søknadId).also {
            this.id = it
        }
    }
}

class StønadsperiodeFactory(
        private val repository: Repository,
        private val søknadFactory: SøknadFactory
) {
    fun nyStønadsperiode(sakId: Long, søknadInnhold: SøknadInnhold): Stønadsperiode = Stønadsperiode(
        søknadFactory = søknadFactory,
        repository = repository
    )
            .nySøknad(sakId, søknadInnhold)

    fun forSak(sakId: Long): List<Stønadsperiode> = repository.stønadsperioderForSak(sakId)
            .map {
                Stønadsperiode(
                    id = it,
                    søknadFactory = søknadFactory,
                    repository = repository
                )
            }
}