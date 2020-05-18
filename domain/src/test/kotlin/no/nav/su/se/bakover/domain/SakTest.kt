package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.*
import no.nav.su.se.bakover.Repository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal val nySakId = Random.nextLong()
internal val nyStønadsperiodeId = Random.nextLong()
internal val nySøknadId = Random.nextLong()
internal val førstegangssøker = Fødselsnummer("01010112345")
internal val eksisterendeSakId = Random.nextLong()
internal val andregangssøker = Fødselsnummer("09090912345")
internal val søknadInnhold = SøknadInnholdTestdataBuilder.build()
internal val tomtRepository = TomtRepository()
internal val repositoryForSøknad = RepositoryForNySøknad()

internal class SakTest {

    @Test
    fun `factory må klare å lage en ny sak fra et fnr, når det ikke finnes en sak fra før`() {
        val nySakTest = AssertNySakOpprettet()
        SakFactory(
            repository = TomtRepository(),
            stønadsperiodeFactory = StønadsperiodeFactory(
                tomtRepository,
                SøknadFactory(tomtRepository, emptyArray())
            ),
            sakObservers = listOf(nySakTest)
        )
                .forFnr(førstegangssøker)

        assertTrue(nySakTest.nySak, "Ny sak event skulle blitt trigget")
    }

    @Test
    fun `factory må klare å hente en sak fra repository, og så legge på en ny søknad`() {
        val repository = RepositoryForNySøknad()
        val nySøknadTest = AssertNySøknadMottat()
        SakFactory(
            repository = repository,
            stønadsperiodeFactory = StønadsperiodeFactory(
                repository,
                SøknadFactory(repository, arrayOf(nySøknadTest))
            ),
            sakObservers = emptyList()
        )
                .forFnr(andregangssøker)
                .nySøknad(søknadInnhold)

        assertTrue(nySøknadTest.nySøknad, "Søknad mottatt event skulle blitt trigget")
    }

    @Test
    fun `factory må levere en Error ved henting av sak med en identitet som ikke finnes`() {
        val eitherSakOrNothing = SakFactory(
            repository = TomtRepository(),
            stønadsperiodeFactory = StønadsperiodeFactory(
                tomtRepository,
                SøknadFactory(tomtRepository, emptyArray())
            ),
            sakObservers = emptyList()
        ).forId(nySakId)
        when (eitherSakOrNothing) {
            is Either.Left -> assertTrue(true)
            is Either.Right -> fail("Skulle ikke ha funnet en sak")
        }
    }

    @Test
    fun `factory må klare å hente en sak fra repository basert på en identitet`() {
        val eitherSakOrNothing = SakFactory(
            repository = RepositoryForNySøknad(),
            stønadsperiodeFactory = StønadsperiodeFactory(
                repositoryForSøknad,
                SøknadFactory(repositoryForSøknad, emptyArray())
            ),
            sakObservers = emptyList()
        ).forId(eksisterendeSakId)
        when (eitherSakOrNothing) {
            is Either.Left -> fail("Skulle ikke ha fått feil fra søknadFactory")
            is Either.Right -> assertTrue(true)
        }
    }

}

internal class TomtRepository : Repository, SakRepo {
    override fun nySak(fnr: Fødselsnummer): Long = nySakId
    override fun sakIdForFnr(fnr: Fødselsnummer): Long? = null
    override fun lagreSøknad(json: String): Long = nySøknadId
    override fun fnrForSakId(sakId: Long): Fødselsnummer? = null
    override fun søknadForStønadsperiode(stønadsperiodeId: Long): Pair<Long, String>? = null
    override fun alleSaker(): List<Pair<Long, Fødselsnummer>> = emptyList()
    override fun søknadForId(id: Long): Pair<Long, String>? = null
    override fun lagreStønadsperiode(sakId: Long, søknadId: Long): Long = nyStønadsperiodeId
    override fun stønadsperioderForSak(sakId: Long): List<Long> = emptyList()
}

internal class RepositoryForNySøknad : Repository, SakRepo {
    override fun nySak(fnr: Fødselsnummer): Long = throw RuntimeException("Skulle ikke lagre sak")
    override fun sakIdForFnr(fnr: Fødselsnummer): Long? = eksisterendeSakId
    override fun lagreSøknad(json: String): Long = nySøknadId
    override fun fnrForSakId(sakId: Long): Fødselsnummer? = andregangssøker
    override fun søknadForStønadsperiode(stønadsperiodeId: Long): Pair<Long, String>? = Pair(nySøknadId, SøknadInnholdTestdataBuilder.build().toJson())
    override fun alleSaker(): List<Pair<Long, Fødselsnummer>> = emptyList()
    override fun søknadForId(id: Long): Pair<Long, String>? = null
    override fun lagreStønadsperiode(sakId: Long, søknadId: Long): Long = nyStønadsperiodeId
    override fun stønadsperioderForSak(sakId: Long): List<Long> = listOf(nyStønadsperiodeId)
}

internal class AssertNySakOpprettet : SakObserver {
    var nySak: Boolean = false
    override fun nySakOpprettet(event: SakObserver.NySakEvent) {
        nySak = true
        assertEquals(SakObserver.NySakEvent(fnr = førstegangssøker, id = nySakId), event)
    }
}

internal class AssertNySøknadMottat : SøknadObserver {
    var nySøknad = false
    override fun søknadMottatt(event: SøknadObserver.SøknadMottattEvent) {
        nySøknad = true
        assertEquals(
                SøknadObserver.SøknadMottattEvent(
                        correlationId = "her skulle vi sikkert hatt en korrelasjonsid",
                        sakId = eksisterendeSakId,
                        søknadId = nySøknadId,
                        søknadInnhold = søknadInnhold
                ), event)
    }
}
