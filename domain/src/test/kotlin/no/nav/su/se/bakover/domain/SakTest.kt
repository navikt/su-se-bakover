package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

internal val nySakId = Random.nextLong()
internal val nyStønadsperiodeId = Random.nextLong()
internal val nySøknadId = Random.nextLong()
internal val førstegangssøker = Fødselsnummer("01010112345")
internal val eksisterendeSakId = Random.nextLong()
internal val andregangssøker = Fødselsnummer("09090912345")
internal val tomtRepository = TomtSøknadRepo()
internal val repositoryForSøknad = SøknadRepoForNySøknad()

internal class SakTest {

    @Test
    fun `factory må klare å lage en ny sak fra et fnr, når det ikke finnes en sak fra før`() {
        val sak = SakFactory(
                sakRepo = TomtSøknadRepo(),
                stønadsperiodeFactory = StønadsperiodeFactory(
                        tomtRepository,
                        SøknadFactory(tomtRepository)
                )
        ).hentEllerOpprett(førstegangssøker)
        assertEquals(nySakId, sak.id())
    }

    @Test
    fun `factory må klare å hente en eksisterende sak fra repository`() {
        val repository = SøknadRepoForNySøknad()
        val sak = SakFactory(
                sakRepo = repository,
                stønadsperiodeFactory = StønadsperiodeFactory(
                        repository,
                        SøknadFactory(repository)
                )
        ).hentEllerOpprett(andregangssøker)
        assertEquals(eksisterendeSakId, sak.id())
    }

    @Test
    fun `factory må levere en Error ved henting av sak med en identitet som ikke finnes`() {
        val eitherSakOrNothing = SakFactory(
                sakRepo = TomtSøknadRepo(),
                stønadsperiodeFactory = StønadsperiodeFactory(
                        tomtRepository,
                        SøknadFactory(tomtRepository)
                )
        ).hent(nySakId)
        when (eitherSakOrNothing) {
            is Either.Left -> assertTrue(true)
            is Either.Right -> fail("Skulle ikke ha funnet en sak")
        }
    }

    @Test
    fun `factory må klare å hente en sak fra repository basert på en identitet`() {
        val eitherSakOrNothing = SakFactory(
                sakRepo = SøknadRepoForNySøknad(),
                stønadsperiodeFactory = StønadsperiodeFactory(
                        repositoryForSøknad,
                        SøknadFactory(repositoryForSøknad)
                )
        ).hent(eksisterendeSakId)
        when (eitherSakOrNothing) {
            is Either.Left -> fail("Skulle ikke ha fått feil fra søknadFactory")
            is Either.Right -> assertTrue(true)
        }
    }

}

internal class TomtSøknadRepo : SøknadRepo, StønadsperiodeRepo, SakRepo {
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

internal class SøknadRepoForNySøknad : SøknadRepo, StønadsperiodeRepo, SakRepo {
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
