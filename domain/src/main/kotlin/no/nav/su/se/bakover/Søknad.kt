package no.nav.su.se.bakover

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.Either.Left
import no.nav.su.se.bakover.Either.Right
import org.json.JSONObject

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Søknad internal constructor(
        private var id: Long = NO_SUCH_IDENTITY,
        private val søknadInnhold: SøknadInnhold
) : Observable<SøknadObserver>() {

    internal fun lagreSøknad(sakId: Long, søknadRepo: SøknadRepo): Søknad = this.also {
        id = søknadRepo.lagreSøknad(søknadInnhold.toJson())
        observers.forEach {
            it.søknadMottatt(
                    SøknadObserver.SøknadMottattEvent(
                            sakId = sakId,
                            søknadId = id,
                            søknadInnhold = søknadInnhold
                    )
            )
        }
    }

    fun toJson(): String = """
        {
            "id": $id,
            "json": ${søknadInnhold.toJson()}
        }
    """.trimIndent()
}

// forstår hvordan man bygger et søknads-domeneobjekt.
class SøknadFactory(
        private val søknadRepo: SøknadRepo,
        private val observers: Array<SøknadObserver>
) {
    fun nySøknad(sakId: Long, søknadInnhold: SøknadInnhold, observer: SøknadObserver) = Søknad(
            søknadInnhold = søknadInnhold
    )
            .subscribe<Søknad>(*observers, observer)
            .lagreSøknad(sakId, søknadRepo)
            .unsubscribe<Søknad>(observer)

    fun forStønadsperiode(stønadsperiodeId: Long): Søknad {
        return when (val søknad = søknadRepo.søknadForStønadsperiode(stønadsperiodeId)) {
            null -> throw RuntimeException("Stønadsperiode without søknad")
            else -> Søknad(
                    id = søknad.first,
                    søknadInnhold = fromJson(søknad.second)
            )
        }
    }

    fun forId(søknadId: Long): Either<String, Søknad> = søknadRepo.søknadForId(søknadId)?.let {
        Right(Søknad(id = it.first, søknadInnhold = fromJson(it.second)))
    } ?: Left("Fant ingen søknad med id $søknadId")

    private fun fromJson(json: String) = SøknadInnhold.fromJson(JSONObject(json))
}

interface SøknadObserver {
    data class SøknadMottattEvent(
            val sakId: Long,
            val søknadId: Long,
            val søknadInnhold: SøknadInnhold
    )

    fun søknadMottatt(event: SøknadMottattEvent): Any
}

// forstår hvordan man kan lagre og hente saker fra et persistenslag
interface SøknadRepo {
    fun lagreSøknad(json: String): Long
    fun søknadForStønadsperiode(stønadsperiodeId: Long): Pair<Long, String>?
    fun søknadForId(id: Long): Pair<Long, String>?
}