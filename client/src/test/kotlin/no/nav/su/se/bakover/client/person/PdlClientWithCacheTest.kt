package no.nav.su.se.bakover.client.person

import arrow.core.right
import com.github.benmanes.caffeine.cache.Cache
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.SivilstandTyper

class PdlClientWithCacheTest {

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
        statsborgerskap = "SYR",
        sivilstand = SivilstandResponse(
            type = SivilstandTyper.GIFT,
            relatertVedSivilstand = "12345678901",
        ),
        fødsel = null,
        adressebeskyttelse = null,
        vergemålEllerFremtidsfullmakt = false,
        dødsdato = 21.desember(2021),
    )

    @Test
    fun `Cacher resultat fra pdl på andre kall`() {
        val fnr = Fnr("07028820547")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        client.person(fnr, brukerToken)
        verify(pdlClient, times(1)).person(fnr, brukerToken)

        client.person(fnr, brukerToken)
        spyCache.getIfPresent(Pair(fnr, brukerToken)) shouldBe expectedPdlDataTemplate

        verify(pdlClient, times(1)).person(fnr, brukerToken)
    }

    @Test
    fun `Andre kall hentes ikke fra cache`() {
        val fnr = Fnr("07028820547")
        val fnrNummerTo = Fnr("33332882057")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken) } doReturn expectedPdlDataTemplate.right()
            on { person(fnrNummerTo, brukerToken) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        client.person(fnr, brukerToken)
        verify(pdlClient, times(1)).person(fnr, brukerToken)
        verify(pdlClient, times(0)).person(fnrNummerTo, brukerToken)

        client.person(fnrNummerTo, brukerToken)
        spyCache.getIfPresent(Pair(fnr, brukerToken)) shouldBe expectedPdlDataTemplate

        verify(pdlClient, times(1)).person(fnr, brukerToken)
        verify(pdlClient, times(1)).person(fnrNummerTo, brukerToken)
    }

    @Test
    fun `Systembruker ser cachet resultat fra vanlig bruker`() {
        val fnr = Fnr("07028820547")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        client.person(fnr, brukerToken)
        verify(pdlClient, times(1)).person(fnr, brukerToken)
        verify(pdlClient, times(0)).personForSystembruker(fnr)

        client.personForSystembruker(fnr)
        spyCache.getIfPresent(Pair(fnr, JwtToken.SystemToken)) shouldBe expectedPdlDataTemplate
        verify(pdlClient, times(1)).person(fnr, brukerToken)
        verify(pdlClient, times(0)).personForSystembruker(fnr) // bruker her cachet resultat
    }

    @Test
    fun `Vanlig bruker ser ikke caching fra systembruker, blir nytt kall mot pdl`() {
        val fnr = Fnr("07028820547")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")

        val spyCache: Cache<FnrCacheKey, PdlData> = newCache(cacheName = "person/domain", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { person(fnr, brukerToken) } doReturn expectedPdlDataTemplate.right()
            on { personForSystembruker(fnr) } doReturn expectedPdlDataTemplate.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), spyCache)

        spyCache.getIfPresent(Pair(fnr, JwtToken.SystemToken)) shouldBe null
        client.personForSystembruker(fnr)
        verify(pdlClient, times(1)).personForSystembruker(fnr)
        verify(pdlClient, times(0)).person(fnr, brukerToken)

        spyCache.getIfPresent(Pair(fnr, brukerToken)) shouldBe null

        client.person(fnr, brukerToken)
        verify(pdlClient, times(1)).person(fnr, brukerToken)
    }

    @Test
    fun `Kan cache aktørid`() {
        val fnr = Fnr("07028820547")
        val aktørid = AktørId("2751637578706")
        val brukerToken = JwtToken.BrukerToken("ignored because of mock")

        val spyCache: Cache<FnrCacheKey, AktørId> = newCache(cacheName = "aktoerId", suMetrics = mock<SuMetrics>())
        val pdlClient = mock<PdlClient> {
            on { aktørIdMedSystembruker(fnr) } doReturn aktørid.right()
        }
        val client = PdlClientWithCache(pdlClient, mock(), aktørIdCache = spyCache)

        spyCache.getIfPresent(Pair(fnr, JwtToken.SystemToken)) shouldBe null

        client.aktørIdMedSystembruker(fnr)
        verify(pdlClient, times(1)).aktørIdMedSystembruker(fnr)
        verify(pdlClient, times(0)).person(fnr, brukerToken)
        verify(pdlClient, times(0)).personForSystembruker(fnr)

        spyCache.getIfPresent(Pair(fnr, JwtToken.SystemToken)) shouldBe aktørid

        client.aktørIdMedSystembruker(fnr)
        verify(pdlClient, times(1)).aktørIdMedSystembruker(fnr)
        verify(pdlClient, times(0)).person(fnr, brukerToken)
        verify(pdlClient, times(0)).personForSystembruker(fnr)

        spyCache.getIfPresent(Pair(fnr, JwtToken.SystemToken)) shouldBe aktørid
        verifyNoMoreInteractions(pdlClient)
    }
}
