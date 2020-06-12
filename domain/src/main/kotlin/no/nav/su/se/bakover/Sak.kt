package no.nav.su.se.bakover

import no.nav.su.se.bakover.Either.Left
import no.nav.su.se.bakover.Either.Right

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Sak internal constructor(
        private val fnr: Fødselsnummer,
        private var id: Long = NO_SUCH_IDENTITY,
        private var stønadsperioder: List<Stønadsperiode> = emptyList()
) : Persistent {

    override fun id(): Long = id

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
class SakFactory(
        private val sakRepo: SakRepo,
        private val stønadsperiodeFactory: StønadsperiodeFactory
) {
    fun forFnr(fnr: Fødselsnummer): Sak {
        val eksisterendeIdentitet = sakRepo.sakIdForFnr(fnr)
        return when (eksisterendeIdentitet) {
            null -> Sak(fnr, sakRepo.nySak(fnr), emptyList())
            else -> Sak(fnr, eksisterendeIdentitet, stønadsperiodeFactory.forSak(eksisterendeIdentitet))
        }
    }

    fun forId(sakId: Long): Either<String, Sak> {
        val eksisterendeFødelsnummer = sakRepo.fnrForSakId(sakId)
        return when (eksisterendeFødelsnummer) {
            null -> Left("Det finnes ingen sak med id $sakId")
            else -> Right(Sak(eksisterendeFødelsnummer, sakId, stønadsperiodeFactory.forSak(sakId)))
        }
    }

    fun alle(): List<Sak> = sakRepo.alleSaker().map {
        Sak(fnr = it.second, id = it.first)
    }
}

interface SakRepo {
    fun nySak(fnr: Fødselsnummer): Long
    fun sakIdForFnr(fnr: Fødselsnummer): Long?
    fun fnrForSakId(sakId: Long): Fødselsnummer?
    fun alleSaker(): List<Pair<Long, Fødselsnummer>>
}