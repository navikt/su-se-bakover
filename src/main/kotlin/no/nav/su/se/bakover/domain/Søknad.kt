package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.Either
import no.nav.su.se.bakover.Either.*
import no.nav.su.se.bakover.Fødselsnummer
import no.nav.su.se.bakover.db.Repository

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE
internal class Søknad internal constructor(
    private var id: Long = NO_SUCH_IDENTITY,
    private val json: String,
    private val observers: List<SøknadObserver>
) {
    internal fun lagreSøknad(sakId: Long, repository: Repository, fnr: Fødselsnummer): Søknad = this.also {
        id = repository.nySøknad(sakId, json)
        observers.forEach { it.søknadMottatt(SøknadObserver.SøknadMottattEvent(sakId = sakId, søknadId = id, søknadstekst = json, fnr = fnr)) }
    }

    fun toJson():String = """
        {
            "id": $id,
            "json": $json
        }
    """.trimIndent()
}

// forstår hvordan man bygger et søknads-domeneobjekt.
internal class SøknadFactory(private val repository: Repository, private val observers: List<SøknadObserver>) {
    fun forSak(sakId: Long, søknadstekst: String, fnr: Fødselsnummer): Søknad = Søknad(json = søknadstekst, observers = observers).lagreSøknad(sakId, repository, fnr)
    fun alleForSak(sakId: Long): List<Søknad> = repository.søknaderForSak(sakId).map { Søknad(id = it.first, json = it.second, observers = observers) }
    fun forId(søknadId: Long): Either<String, Søknad> = repository.søknadForId(søknadId)?.let {
        Right(Søknad(id = it.first, json = it.second, observers = observers))
    } ?: Left("Fant ingen søknad med id $søknadId")
}

internal interface SøknadObserver {
    data class SøknadMottattEvent(val sakId: Long, val søknadId: Long, val søknadstekst: String, val fnr: Fødselsnummer)
    fun søknadMottatt(event: SøknadMottattEvent)
}
