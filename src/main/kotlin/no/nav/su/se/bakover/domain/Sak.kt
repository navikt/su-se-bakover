package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.Either
import no.nav.su.se.bakover.Either.*
import no.nav.su.se.bakover.db.Repository

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE
internal class Sak internal constructor(
    private val fnr: String,
    private var id: Long = NO_SUCH_IDENTITY,
    private var søknader:List<Søknad> = emptyList(),
    private val observers: List<SakObserver>,
    private val søknadFactory: SøknadFactory
) {
    init {
        if (id != NO_SUCH_IDENTITY) søknader = søknadFactory.alleForSak(id)
    }

    fun lagreNySak(repository: Repository): Sak = this.also {
        id = repository.nySak(fnr)
        observers.forEach { it.nySakOpprettet(SakObserver.NySakEvent(fnr, id)) }
    }

    fun nySøknad(søknadstekst: String): Sak = this.also {
        søknader = søknader + søknadFactory.forSak(id, søknadstekst)
    }

    // TODO: Denne finnes for å støtte endepunktet /soknad?fnr=ident; det vil si "gi meg søknaden til person X", som for øyeblikket ikke nødvendigvis gir mening.
    fun gjeldendeSøknad(): Either<String, Søknad> = when {
        søknader.isEmpty() -> Error("Sak $id har ingen søknader")
        else -> Value(søknader.last())
    }

    fun toJson() = """
        {
            "id":"$id",
            "fnr":"$fnr",
            "søknader": [ ${alleSøknaderSomEnJsonListe()}]
        }
    """.trimIndent()

    fun alleSøknaderSomEnJsonListe(): String = "[ ${søknader.joinToString(",") { it.toJson() }} ]"
}

// forstår hvordan man konstruerer en sak.
internal class SakFactory(
    private val repository: Repository,
    private val sakObservers: List<SakObserver>,
    private val søknadFactory: SøknadFactory
) {
    fun forFnr(fnr: String): Sak {
        val repoId = repository.sakIdForFnr(fnr)
        return when (repoId) {
            null -> Sak(fnr = fnr, observers = sakObservers, søknadFactory = søknadFactory).lagreNySak(repository)
            else -> Sak(fnr = fnr, id = repoId, observers = sakObservers, søknadFactory = søknadFactory)
        }
    }

    fun forId(sakId: Long): Either<String, Sak> {
        val repoFnr = repository.fnrForSakId(sakId)
        return when(repoFnr) {
            null -> Error("Det finnes ingen sak med id $sakId")
            else -> Value(Sak(fnr = repoFnr, id = sakId, observers = sakObservers, søknadFactory = søknadFactory))
        }
    }

    fun alle(): List<Sak> = repository.alleSaker().map { Sak(id = it.first, fnr = it.second, observers = sakObservers, søknadFactory = søknadFactory) }
}

internal interface SakObserver {
    data class NySakEvent(val fnr: String, val id: Long)
    fun nySakOpprettet(event: NySakEvent)
}