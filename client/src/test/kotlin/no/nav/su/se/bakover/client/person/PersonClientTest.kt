package no.nav.su.se.bakover.client.person

import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.kodeverk.Kodeverk
import no.nav.su.se.bakover.client.krr.KontaktOgReservasjonsregister
import no.nav.su.se.bakover.client.krr.Kontaktinformasjon
import no.nav.su.se.bakover.client.skjerming.Skjerming
import no.nav.su.se.bakover.common.auth.AzureAd
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.token.JwtToken
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.test.getOrFail
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import person.domain.Person
import java.time.Year

internal class PersonClientTest {

    @Test
    fun `kontaktinfo lastes lazy`() {
        val mocks = PersonClientConfigTestMocks()

        val person = mocks.personClient.person(fnr = mocks.fnr).getOrFail()

        verify(mocks.pdlClient).person(eq(mocks.fnr), eq(mocks.brukerToken))
        verifyNoInteractions(mocks.kontaktOgReservasjonsregisterMock)

        person.kontaktinfo() shouldBe Person.Kontaktinfo(
            epostadresse = mocks.kontaktinformasjon.epostadresse,
            mobiltelefonnummer = mocks.kontaktinformasjon.mobiltelefonnummer,
            språk = mocks.kontaktinformasjon.språk,
            kanKontaktesDigitalt = true,
        )

        verify(mocks.kontaktOgReservasjonsregisterMock).hentKontaktinformasjon(mocks.fnr)
    }

    @Test
    fun `personMedSystembruker mapper data`() {
        val mocks = PersonClientConfigTestMocks()

        val result = mocks.personClient.personMedSystembruker(fnr = mocks.fnr).getOrFail()

        result.shouldBeEqualToIgnoringFields(mocks.person(), Person::kontaktinfo)
        verify(mocks.pdlClient).personForSystembruker(mocks.fnr)
    }

    @Test
    fun `aktørIdMedSystembruker mapper data`() {
        val mocks = PersonClientConfigTestMocks()

        mocks.personClient.aktørIdMedSystembruker(fnr = mocks.fnr) shouldBe mocks.aktørId.right()

        verify(mocks.pdlClient).aktørIdMedSystembruker(mocks.fnr)
    }

    @Test
    fun `sjekkTilgangTilPerson kaller pdl`() {
        val mocks = PersonClientConfigTestMocks()

        mocks.personClient.sjekkTilgangTilPerson(fnr = mocks.fnr) shouldBe Unit.right()

        verify(mocks.pdlClient).person(eq(mocks.fnr), eq(mocks.brukerToken))
    }

    private class PersonClientConfigTestMocks(
        val fnr: Fnr = Fnr("07028820547"),
    ) {
        val kontaktinformasjon = Kontaktinformasjon(
            epostadresse = "post@e.com",
            mobiltelefonnummer = "12345678",
            reservert = false,
            kanVarsles = true,
            språk = "nb",
        )

        val kodeverkMock: Kodeverk = mock()
        val skjermingMock: Skjerming = mock {
            on { erSkjermet(any(), any()) } doReturn false
        }
        val kontaktOgReservasjonsregisterMock: KontaktOgReservasjonsregister = mock {
            on { hentKontaktinformasjon(fnr) } doReturn kontaktinformasjon.right()
        }
        val oauthMock: AzureAd = mock()

        val brukerToken = JwtToken.BrukerToken("bruker-token")
        val hentBrukerToken = { brukerToken }

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
            kontaktinfo = {
                Person.Kontaktinfo(
                    epostadresse = kontaktinformasjon.epostadresse,
                    mobiltelefonnummer = kontaktinformasjon.mobiltelefonnummer,
                    språk = kontaktinformasjon.språk,
                    kanKontaktesDigitalt = true,
                )
            },
            vergemål = pdlData().vergemålEllerFremtidsfullmakt,
            dødsdato = pdlData().dødsdato!!,
        )

        val pdlClient: PdlClientWithCache = mock {
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
            suMetrics = mock<SuMetrics>(),
            pdlClient = pdlClient,
            hentBrukerToken = hentBrukerToken,
        )
    }
}
