package no.nav.su.se.bakover.client.person

import arrow.core.NonEmptyList
import arrow.core.nonEmptyListOf
import arrow.core.right
import com.github.benmanes.caffeine.cache.Cache
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import no.nav.su.se.bakover.client.cache.newCache
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.krr.Kontaktinformasjon
import no.nav.su.se.bakover.client.skjerming.Skjerming
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.Person
import java.time.Year

internal class PersonClientTest {

    @Nested
    inner class `person()` {
        @Test
        fun `andre kall med samme token hentes fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val first = mocks.personClient.person(fnr = mocks.fnr).also {
                it shouldBe mocks.person().right()
            }.getOrFail()
            verify(mocks.pdlClient).person(mocks.fnr, mocks.brukerTokenGenerator.first())
            mocks.personClient.person(fnr = mocks.fnr).getOrFail() shouldBeSameInstanceAs first
            verifyNoMoreInteractions(mocks.pdlClient)
        }

        @Test
        fun `andre kall med nytt token hentes ikke fra cache`() {
            val mocks = PersonClientConfigTestMocks(
                BrukertokenGenerator(nonEmptyListOf("1", "2").map { JwtToken.BrukerToken(it) }),
            )
            val first =
                mocks.personClient.person(fnr = mocks.fnr).also { it shouldBe mocks.person().right() }.getOrFail()
            mocks.personClient.person(fnr = mocks.fnr).also {
                it shouldBe mocks.person().right()
                it.getOrFail() shouldNotBeSameInstanceAs first
            }
            verify(mocks.pdlClient, times(2)).person(eq(mocks.fnr), any())
            verifyNoMoreInteractions(mocks.pdlClient)
        }

        @Test
        fun `andre kall med annet fnr hentes ikke fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val fnr1 = mocks.fnr
            val fnr2 = Fnr.generer()
            val first = mocks.personClient.person(fnr = fnr1).also {
                it shouldBe mocks.person().right()
            }.getOrFail()
            mocks.personClient.person(fnr = fnr2).also {
                it shouldBe mocks.person().right()
                it.getOrFail() shouldNotBeSameInstanceAs first
            }
            verify(mocks.pdlClient).person(eq(fnr1), any())
            verify(mocks.pdlClient).person(eq(fnr2), any())
            verifyNoMoreInteractions(mocks.pdlClient)
        }
    }

    @Nested
    inner class `personMedSystembruker()` {

        @Test
        fun `andre kall med samme token hentes fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val first = mocks.personClient.personMedSystembruker(fnr = mocks.fnr).also {
                it shouldBe mocks.person().right()
            }.getOrFail()
            verify(mocks.pdlClient).personForSystembruker(mocks.fnr)
            mocks.personClient.personMedSystembruker(fnr = mocks.fnr).getOrFail() shouldBeSameInstanceAs first
            verifyNoMoreInteractions(mocks.pdlClient)
        }

        @Test
        fun `første kall med personbruker og andre kall med systembruker hentes fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val first = mocks.personClient.person(fnr = mocks.fnr).also {
                it shouldBe mocks.person().right()
            }.getOrFail()
            verify(mocks.pdlClient).person(mocks.fnr, mocks.brukerTokenGenerator.first())
            verifyNoMoreInteractions(mocks.pdlClient)
            mocks.personClient.personMedSystembruker(fnr = mocks.fnr).getOrFail() shouldBeSameInstanceAs first
            verifyNoMoreInteractions(mocks.pdlClient)
        }
    }

    @Nested
    inner class `aktørId()` {
        @Test
        fun `andre kall med samme token hentes fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val first = mocks.personClient.aktørIdMedSystembruker(fnr = mocks.fnr).also {
                it shouldBe mocks.aktørId.right()
            }.getOrFail()
            verify(mocks.pdlClient).aktørIdMedSystembruker(mocks.fnr)
            mocks.personClient.aktørIdMedSystembruker(fnr = mocks.fnr).getOrFail() shouldBeSameInstanceAs first
            verifyNoMoreInteractions(mocks.pdlClient)
        }
    }

    @Nested
    inner class `aktørIdMedSystembruker()` {
        @Test
        fun `andre kall med samme token hentes fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val first = mocks.personClient.aktørIdMedSystembruker(fnr = mocks.fnr).also {
                it shouldBe mocks.aktørId.right()
            }.getOrFail()
            verify(mocks.pdlClient).aktørIdMedSystembruker(mocks.fnr)
            mocks.personClient.aktørIdMedSystembruker(fnr = mocks.fnr).getOrFail() shouldBeSameInstanceAs first
            verifyNoMoreInteractions(mocks.pdlClient)
        }

        @Test
        fun `første kall med personbruker og andre kall med systembruker hentes fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val first = mocks.personClient.aktørIdMedSystembruker(fnr = mocks.fnr).also {
                it shouldBe mocks.aktørId.right()
            }.getOrFail()
            verify(mocks.pdlClient).aktørIdMedSystembruker(mocks.fnr)
            verifyNoMoreInteractions(mocks.pdlClient)
            mocks.personClient.aktørIdMedSystembruker(fnr = mocks.fnr).getOrFail() shouldBeSameInstanceAs first
            verifyNoMoreInteractions(mocks.pdlClient)
        }
    }

    @Nested
    inner class `sjekkTilgangTilPerson()` {
        @Test
        fun `andre kall med samme token hentes fra cache`() {
            val mocks = PersonClientConfigTestMocks()
            val first = mocks.personClient.sjekkTilgangTilPerson(fnr = mocks.fnr).also {
                it shouldBe Unit.right()
            }.getOrFail()
            verify(mocks.pdlClient).person(mocks.fnr, mocks.brukerTokenGenerator.first())
            mocks.personClient.sjekkTilgangTilPerson(fnr = mocks.fnr).getOrFail() shouldBeSameInstanceAs first
            verifyNoMoreInteractions(mocks.pdlClient)
        }
    }

    /**
     * Rullerer på tokens
     */
    private class BrukertokenGenerator(
        private val brukerTokens: NonEmptyList<JwtToken.BrukerToken> = nonEmptyListOf(JwtToken.BrukerToken("bruker-token")),
    ) : List<JwtToken.BrukerToken> by brukerTokens {

        var currentIndex = 0
        fun next(): JwtToken.BrukerToken = brukerTokens[currentIndex++ % brukerTokens.size]
    }

    private class PersonClientConfigTestMocks(
        val brukerTokenGenerator: BrukertokenGenerator = BrukertokenGenerator(),
        val fnr: Fnr = Fnr.generer(),
        personCacheSpy: Cache<FnrCacheKey, Person> = newCache(cacheName = "person/domain", suMetrics = mock()),
        aktørIdCacheSpy: Cache<FnrCacheKey, AktørId> = newCache(cacheName = "aktoerId", suMetrics = mock()),
    ) {
        val kontaktinformasjon = Kontaktinformasjon(
            epostadresse = "post@e.com",
            mobiltelefonnummer = "12345678",
            reservert = false,
            kanVarsles = true,
            språk = "nb",
        )

        val kodeverkMock: Kodeverk = mock()
        val skjermingMock: Skjerming = mock()
        val kontaktOgReservasjonsregisterMock: KontaktOgReservasjonsregister = mock {
            on { hentKontaktinformasjon(fnr) } doReturn kontaktinformasjon.right()
        }
        val oauthMock: AzureAd = mock()

        val hentBrukerToken = { brukerTokenGenerator.next() }

        val aktørId = AktørId("2751637578706")
        fun pdlData() = PdlData(
            ident = PdlData.Ident(fnr, aktørId),
            navn = PdlData.Navn(
                fornavn = "NYDELIG",
                mellomnavn = null,
                etternavn = "KRONJUVEL",
            ),
            telefonnummer = null,
            adresse = emptyList(),
            statsborgerskap = null,
            sivilstand = null,
            fødsel = PdlData.Fødsel(
                foedselsaar = 1990,
                foedselsdato = null,
            ),
            adressebeskyttelse = null,
            vergemålEllerFremtidsfullmakt = false,
            dødsdato = 22.februar(2022),
        )

        fun person() = Person(
            ident = Ident(
                fnr = fnr,
                aktørId = pdlData().ident.aktørId,
            ),
            navn = Person.Navn(
                fornavn = pdlData().navn.fornavn,
                mellomnavn = pdlData().navn.mellomnavn,
                etternavn = pdlData().navn.etternavn,
            ),
            telefonnummer = pdlData().telefonnummer,
            adresse = emptyList(),
            statsborgerskap = pdlData().statsborgerskap,
            sivilstand = null,
            fødsel = pdlData().fødsel?.let {
                if (it.foedselsdato != null) {
                    Person.Fødsel.MedFødselsdato(it.foedselsdato)
                } else {
                    Person.Fødsel.MedFødselsår(Year.of(it.foedselsaar))
                }
            },
            adressebeskyttelse = pdlData().adressebeskyttelse,
            skjermet = false,
            kontaktinfo = Person.Kontaktinfo(
                epostadresse = kontaktinformasjon.epostadresse,
                mobiltelefonnummer = kontaktinformasjon.mobiltelefonnummer,
                språk = kontaktinformasjon.språk,
                kanKontaktesDigitalt = true,
            ),
            vergemål = pdlData().vergemålEllerFremtidsfullmakt,
            dødsdato = pdlData().dødsdato!!,
        )

        val pdlClient: PdlClient = mock {
            on { person(any(), any()) } doReturn pdlData().right()
            on { personForSystembruker(any()) } doReturn pdlData().right()
            on { aktørIdMedSystembruker(any()) } doReturn aktørId.right()
        }

        val pdlClientConfig: PdlClientConfig = PdlClientConfig(
            vars = ApplicationConfig.ClientsConfig.PdlConfig(
                url = "pdl-url-for-test",
                clientId = "pdl-client-id-for-test",
            ),
            azureAd = oauthMock,
        )

        val personClientConfig = PersonClientConfig(
            kodeverk = kodeverkMock,
            skjerming = skjermingMock,
            kontaktOgReservasjonsregister = kontaktOgReservasjonsregisterMock,
            pdlClientConfig = pdlClientConfig,
        )

        val personClient = PersonClient(
            config = personClientConfig,
            pdlClient = pdlClient,
            hentBrukerToken = hentBrukerToken,
            personCache = personCacheSpy,
            aktørIdCache = aktørIdCacheSpy,
            suMetrics = mock(),
        )
    }
}
