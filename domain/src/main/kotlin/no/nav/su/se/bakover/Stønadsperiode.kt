package no.nav.su.se.bakover

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.SøknadObserver.SøknadMottattEvent

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Stønadsperiode(
    private var id: Long = NO_SUCH_IDENTITY,
    private val søknadFactory: SøknadFactory,
    private val søknadRepo: StønadsperiodeRepo
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
        søknadRepo.lagreStønadsperiode(sakId = event.sakId, søknadId = event.søknadId).also {
            this.id = it
        }
    }
}

class StønadsperiodeFactory(
    private val søknadRepo: StønadsperiodeRepo,
    private val søknadFactory: SøknadFactory
) {
    fun nyStønadsperiode(sakId: Long, søknadInnhold: SøknadInnhold): Stønadsperiode = Stønadsperiode(
        søknadFactory = søknadFactory,
        søknadRepo = søknadRepo
    )
            .nySøknad(sakId, søknadInnhold)

    fun forSak(sakId: Long): List<Stønadsperiode> = søknadRepo.stønadsperioderForSak(sakId)
            .map {
                Stønadsperiode(
                    id = it,
                    søknadFactory = søknadFactory,
                    søknadRepo = søknadRepo
                )
            }
}