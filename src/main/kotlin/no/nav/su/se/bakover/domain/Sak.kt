package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.se.bakover.Either
import no.nav.su.se.bakover.Either.Left
import no.nav.su.se.bakover.Either.Right
import no.nav.su.se.bakover.Fødselsnummer
import no.nav.su.se.bakover.db.Repository

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

internal class Sak internal constructor(
        private val fnr: Fødselsnummer,
        private var id: Long = NO_SUCH_IDENTITY,
        private var stønadsperioder: List<Stønadsperiode> = emptyList(),
        private val observers: List<SakObserver>,
        private val stønadsperiodeFactory: StønadsperiodeFactory
) {
    init {
        if (id != NO_SUCH_IDENTITY) stønadsperioder = stønadsperiodeFactory.forSak(id)
    }

    fun lagreNySak(repository: Repository): Sak = this.also {
        repository.nySak(fnr).also { sakId ->
            this.id = sakId
            observers.forEach { it.nySakOpprettet(SakObserver.NySakEvent(fnr, id)) }
        }

    }

    fun nySøknad(søknadInnhold: SøknadInnhold): Sak = this.also {
        stønadsperioder += stønadsperiodeFactory.nyStønadsperiode(id, søknadInnhold)
    }

    // TODO: Denne finnes for å støtte endepunktet /soknad?fnr=ident; det vil si "gi meg søknaden til person X", som for øyeblikket ikke nødvendigvis gir mening.
    fun gjeldendeSøknad(): Either<String, Stønadsperiode> = when {
        stønadsperioder.isEmpty() -> Left("Sak $id har ingen stønadsperioder")
        else -> Right(stønadsperioder.last())
    }

    fun toJson() = """
        {
            "id": $id,
            "fnr":"$fnr",
            "stønadsperioder": ${stønadsperioderSomJsonListe()}
        }
    """.trimIndent()

    fun stønadsperioderSomJsonListe(): String = "[ ${stønadsperioder.joinToString(",") { it.toJson() }} ]"
}

// forstår hvordan man konstruerer en sak.
internal class SakFactory(
        private val repository: Repository,
        private val sakObservers: List<SakObserver>,
        private val stønadsperiodeFactory: StønadsperiodeFactory
) {
    fun forFnr(fnr: Fødselsnummer): Sak {
        val eksisterendeIdentitet = repository.sakIdForFnr(fnr)
        return when (eksisterendeIdentitet) {
            null -> Sak(fnr = fnr, observers = sakObservers, stønadsperiodeFactory = stønadsperiodeFactory)
                    .lagreNySak(repository)
            else -> Sak(fnr = fnr, id = eksisterendeIdentitet, observers = sakObservers, stønadsperiodeFactory = stønadsperiodeFactory)
        }
    }

    fun forId(sakId: Long): Either<String, Sak> {
        val eksisterendeFødelsnummer = repository.fnrForSakId(sakId)
        return when (eksisterendeFødelsnummer) {
            null -> Left("Det finnes ingen sak med id $sakId")
            else -> Right(Sak(fnr = eksisterendeFødelsnummer, id = sakId, observers = sakObservers, stønadsperiodeFactory = stønadsperiodeFactory))
        }
    }

    fun alle(): List<Sak> = repository.alleSaker().map { Sak(fnr = it.second, id = it.first, observers = sakObservers, stønadsperiodeFactory = stønadsperiodeFactory) }
}

internal interface SakObserver {
    data class NySakEvent(val fnr: Fødselsnummer, val id: Long)

    fun nySakOpprettet(event: NySakEvent)
}