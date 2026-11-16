package no.nav.su.se.bakover.client.person

import arrow.core.left
import arrow.core.right
import com.github.benmanes.caffeine.cache.Cache
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.common.domain.auth.Kontekst
import no.nav.su.se.bakover.common.domain.auth.TokenContext
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import person.domain.Adressebeskyttelse
import person.domain.KunneIkkeHentePerson
import person.domain.SivilstandTyper
import java.util.stream.Stream

class PdlClientWithCacheTest {
    @ParameterizedTest
    @MethodSource("graderinger")
    fun `bevarer adressebeskyttelse i hver forespørsel også ved cache-treff`(
        gradering: Adressebeskyttelse,
        forventetKode6: Boolean,
    ) = runBlocking<Unit> {
        val fnr = expectedPdlDataTemplate.ident.fnr
        val token = "test-token"
        val brukerToken = JwtToken.BrukerToken(token)
        val sakstype = Sakstype.UFØRE
        val data = expectedPdlDataTemplate.copy(adressebeskyttelse = gradering.name)
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken, sakstype) } doReturn data.right()
        }
        val client = PdlClientWithCache(pdlClient, mock())

        repeat(2) {
            withContext(Kontekst.asContextElement(TokenContext(token))) {
                client.person(fnr, brukerToken, sakstype) shouldBe data.right()
                Kontekst.get().statistikkKode6.harKode6(fnr) shouldBe forventetKode6
            }
        }

        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)
    }

    @Test
    fun `feilet personoppslag sperrer statistikk til et nytt oppslag lykkes`() = runBlocking<Unit> {
        val fnr = expectedPdlDataTemplate.ident.fnr
        val token = "test-token"
        val brukerToken = JwtToken.BrukerToken(token)
        val sakstype = Sakstype.UFØRE
        val feil = KunneIkkeHentePerson.Ukjent.left()
        val data = expectedPdlDataTemplate.copy(adressebeskyttelse = Adressebeskyttelse.STRENGT_FORTROLIG.name)
        val pdlClient = mock<PdlClient>()
        whenever(pdlClient.person(fnr, brukerToken, sakstype)).thenReturn(feil, data.right())
        val client = PdlClientWithCache(pdlClient, mock())

        withContext(Kontekst.asContextElement(TokenContext(token))) {
            client.person(fnr, brukerToken, sakstype) shouldBe feil
            shouldThrow<IllegalStateException> { Kontekst.get().statistikkKode6.harKode6(fnr) }

            client.person(fnr, brukerToken, sakstype) shouldBe data.right()
            Kontekst.get().statistikkKode6.harKode6(fnr) shouldBe true
        }

        verify(pdlClient, times(2)).person(fnr, brukerToken, sakstype)
    }

    companion object {
        @JvmStatic
        fun graderinger(): Stream<Arguments> = Stream.of(
            Arguments.of(Adressebeskyttelse.STRENGT_FORTROLIG, true),
            Arguments.of(Adressebeskyttelse.STRENGT_FORTROLIG_UTLAND, true),
            Arguments.of(Adressebeskyttelse.FORTROLIG, false),
            Arguments.of(Adressebeskyttelse.UGRADERT, false),
        )
    }

    private fun cacheKey(
        fnr: Fnr,
        token: JwtToken,
    ) = FnrCacheKey(fnr, token)

    private val expectedPdlDataTemplate = PdlData(
        ident = PdlData.Ident(Fnr("07028820547"), AktørId("2751637578706")),
        navn = PdlData.Navn(
            fornavn = "NYDELIG",
            mellomnavn = null,
            etternavn = "KRONJUVEL",
        ),
        telefonnummer = null,
        adresse = listOf(
            PdlData.Adresse(
                adresselinje = "SANDTAKVEIEN 42",
                postnummer = "9190",
                bruksenhet = null,
                kommunenummer = "5427",
                adressetype = "Bostedsadresse",
                adresseformat = "Vegadresse",
            ),
        ),
        sivilstand = SivilstandResponse(
            type = SivilstandTyper.GIFT,
            relatertVedSivilstand = "12345678901",
        ),
        fødsel = null,
        adressebeskyttelse = null,
        vergemålEllerFremtidsfullmakt = false,
        dødsdato = 21.desember(2021),
        dødsbo = emptyList(),
    )

    @Test
    fun `Cacher resultat fra pdl på andre kall`() {
        val fnr = Fnr("07028820547")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")
        val sakstype = Sakstype.UFØRE

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken, sakstype) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        client.person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)

        client.person(fnr, brukerToken, sakstype)
        spyCache.getIfPresent(cacheKey(fnr, brukerToken)) shouldBe expectedPdlDataTemplate

        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)
    }

    @Test
    fun `Ulikt fnr gir ikke cache-treff`() {
        val fnr = Fnr("07028820547")
        val fnrNummerTo = Fnr("33332882057")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")
        val sakstype = Sakstype.UFØRE

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken, sakstype) } doReturn expectedPdlDataTemplate.right()
            on { person(fnrNummerTo, brukerToken, sakstype) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        client.person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(0)).person(fnrNummerTo, brukerToken, sakstype)

        client.person(fnrNummerTo, brukerToken, sakstype)
        spyCache.getIfPresent(cacheKey(fnr, brukerToken)) shouldBe expectedPdlDataTemplate

        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(1)).person(fnrNummerTo, brukerToken, sakstype)
    }

    @Test
    fun `Systembruker ser cachet resultat fra vanlig bruker`() {
        val fnr = Fnr("07028820547")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")
        val sakstype = Sakstype.UFØRE

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken, sakstype) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        client.person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(0)).personForSystembruker(fnr, sakstype)

        client.personForSystembruker(fnr, sakstype)
        spyCache.getIfPresent(cacheKey(fnr, JwtToken.SystemToken)) shouldBe expectedPdlDataTemplate
        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(0)).personForSystembruker(fnr, sakstype) // bruker her cachet resultat
    }

    @Test
    fun `Vanlig bruker ser ikke caching fra systembruker, blir nytt kall mot pdl`() {
        val fnr = Fnr("07028820547")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")
        val sakstype = Sakstype.UFØRE

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken, sakstype) } doReturn expectedPdlDataTemplate.right()
            on { personForSystembruker(fnr, sakstype) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        spyCache.getIfPresent(cacheKey(fnr, JwtToken.SystemToken)) shouldBe null
        client.personForSystembruker(fnr, sakstype)
        verify(pdlClient, times(1)).personForSystembruker(fnr, sakstype)
        verify(pdlClient, times(0)).person(fnr, brukerToken, sakstype)

        spyCache.getIfPresent(cacheKey(fnr, brukerToken)) shouldBe null

        client.person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(1)).person(fnr, brukerToken, sakstype)
    }

    @Test
    fun `Kan cache aktørid`() {
        val fnr = Fnr("07028820547")
        val aktørid = AktørId("2751637578706")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")
        val sakstype = Sakstype.UFØRE

        val spyCache: Cache<FnrCacheKey, AktørId> = newCache(cacheName = "aktoerId", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { aktørIdMedSystembruker(fnr, sakstype) } doReturn aktørid.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), aktørIdCache = spyCache)

        spyCache.getIfPresent(cacheKey(fnr, JwtToken.SystemToken)) shouldBe null

        client.aktørIdMedSystembruker(fnr, sakstype)
        verify(pdlClient, times(1)).aktørIdMedSystembruker(fnr, sakstype)
        verify(pdlClient, times(0)).person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(0)).personForSystembruker(fnr, sakstype)

        spyCache.getIfPresent(cacheKey(fnr, JwtToken.SystemToken)) shouldBe aktørid

        client.aktørIdMedSystembruker(fnr, sakstype)
        verify(pdlClient, times(1)).aktørIdMedSystembruker(fnr, sakstype)
        verify(pdlClient, times(0)).person(fnr, brukerToken, sakstype)
        verify(pdlClient, times(0)).personForSystembruker(fnr, sakstype)

        spyCache.getIfPresent(cacheKey(fnr, JwtToken.SystemToken)) shouldBe aktørid
        verifyNoMoreInteractions(pdlClient)
    }
}
