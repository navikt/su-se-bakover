package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.node.ObjectNode
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.jsonNode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.service.søknad.AvslåSøknadManglendeDokumentasjonService
import no.nav.su.se.bakover.service.søknad.KunneIkkeOppretteSøknad
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.søknad.forNavPapirsøknad
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.web.TestServicesBuilder
import no.nav.su.se.bakover.web.defaultRequest
import no.nav.su.se.bakover.web.inputvalidation.InputFelter
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringFeilResponse
import no.nav.su.se.bakover.web.inputvalidation.UgyldigInputValideringsfeil
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.BoforholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.EktefelleJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.ForNavJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson.Companion.toSøknadsinnholdUføreJson
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.UUID

internal class SøknadInputValidatorTest {
    private val opprettUri = "$SØKNAD_PATH/ufore"
    private val avslagUri = "$SØKNAD_PATH/${UUID.randomUUID()}/avslag"
    private val brevutkastUri = "$avslagUri/brevutkast"
    private val mistenkelig = "javascript:alert(1)"
    private val basis = søknadinnholdUføre(forNav = forNavPapirsøknad()).toSøknadsinnholdUføreJson().let { søknad ->
        søknad.copy(
            ektefelle = EktefelleJson(søknad.formue, søknad.inntektOgPensjon),
            boforhold = søknad.boforhold.copy(
                borPåAdresse = BoforholdJson.AdresseFraSøknad(
                    adresselinje = "Testveien 1",
                    postnummer = "0001",
                    poststed = "Oslo",
                    bruksenhet = "H0101",
                ),
                ingenAdresseGrunn = null,
            ),
        )
    }

    private data class Felt(
        val peker: String,
        val sti: String,
        val maksLengde: Int? = null,
    )

    private val felt = listOf(
        Felt("/type", "type"),
        Felt("/personopplysninger/fnr", "personopplysninger.fnr"),
        Felt("/boforhold/delerBoligMed", "boforhold.delerBoligMed"),
        Felt("/boforhold/ektefellePartnerSamboer/fnr", "boforhold.ektefellePartnerSamboer.fnr"),
        Felt("/boforhold/innlagtPåInstitusjon/datoForInnleggelse", "boforhold.innlagtPåInstitusjon.datoForInnleggelse"),
        Felt("/boforhold/innlagtPåInstitusjon/datoForUtskrivelse", "boforhold.innlagtPåInstitusjon.datoForUtskrivelse"),
        Felt("/boforhold/borPåAdresse/adresselinje", "boforhold.borPåAdresse.adresselinje"),
        Felt("/boforhold/borPåAdresse/postnummer", "boforhold.borPåAdresse.postnummer"),
        Felt("/boforhold/borPåAdresse/poststed", "boforhold.borPåAdresse.poststed"),
        Felt("/boforhold/borPåAdresse/bruksenhet", "boforhold.borPåAdresse.bruksenhet"),
        Felt("/utenlandsopphold/registrertePerioder/0/utreisedato", "utenlandsopphold.registrertePerioder[0].utreisedato"),
        Felt("/utenlandsopphold/registrertePerioder/0/innreisedato", "utenlandsopphold.registrertePerioder[0].innreisedato"),
        Felt("/oppholdstillatelse/typeOppholdstillatelse", "oppholdstillatelse.typeOppholdstillatelse"),
        Felt("/oppholdstillatelse/statsborgerskapAndreLandFritekst", "oppholdstillatelse.statsborgerskapAndreLandFritekst", 500),
        Felt("/forNav/type", "forNav.type"),
        Felt("/forNav/mottaksdatoForSøknad", "forNav.mottaksdatoForSøknad"),
        Felt("/forNav/grunnForPapirinnsending", "forNav.grunnForPapirinnsending"),
        Felt("/forNav/annenGrunn", "forNav.annenGrunn", 500),
        Felt("/formue/boligBrukesTil", "formue.boligBrukesTil", 1000),
        Felt("/formue/eiendomBrukesTil", "formue.eiendomBrukesTil", 1000),
        Felt("/formue/kjøretøy/0/kjøretøyDeEier", "formue.kjøretøy[0].kjøretøyDeEier", 100),
        Felt("/inntektOgPensjon/andreYtelserINav", "inntektOgPensjon.andreYtelserINav", 500),
        Felt("/inntektOgPensjon/søktAndreYtelserIkkeBehandletBegrunnelse", "inntektOgPensjon.søktAndreYtelserIkkeBehandletBegrunnelse", 1000),
        Felt("/inntektOgPensjon/trygdeytelserIUtlandet/0/type", "inntektOgPensjon.trygdeytelserIUtlandet[0].type", 200),
        Felt("/inntektOgPensjon/trygdeytelserIUtlandet/0/valuta", "inntektOgPensjon.trygdeytelserIUtlandet[0].valuta", 50),
        Felt("/inntektOgPensjon/pensjon/0/ordning", "inntektOgPensjon.pensjon[0].ordning", 200),
        Felt("/ektefelle/formue/boligBrukesTil", "ektefelle.formue.boligBrukesTil", 1000),
        Felt("/ektefelle/formue/eiendomBrukesTil", "ektefelle.formue.eiendomBrukesTil", 1000),
        Felt("/ektefelle/formue/kjøretøy/0/kjøretøyDeEier", "ektefelle.formue.kjøretøy[0].kjøretøyDeEier", 100),
        Felt("/ektefelle/inntektOgPensjon/andreYtelserINav", "ektefelle.inntektOgPensjon.andreYtelserINav", 500),
        Felt("/ektefelle/inntektOgPensjon/søktAndreYtelserIkkeBehandletBegrunnelse", "ektefelle.inntektOgPensjon.søktAndreYtelserIkkeBehandletBegrunnelse", 1000),
        Felt("/ektefelle/inntektOgPensjon/trygdeytelserIUtlandet/0/type", "ektefelle.inntektOgPensjon.trygdeytelserIUtlandet[0].type", 200),
        Felt("/ektefelle/inntektOgPensjon/trygdeytelserIUtlandet/0/valuta", "ektefelle.inntektOgPensjon.trygdeytelserIUtlandet[0].valuta", 50),
        Felt("/ektefelle/inntektOgPensjon/pensjon/0/ordning", "ektefelle.inntektOgPensjon.pensjon[0].ordning", 200),
    )

    private fun bodyMedFelt(felt: Felt, verdi: String): String {
        val body = jsonNode(serialize(basis)) as ObjectNode
        val forelder = body.at(felt.peker.substringBeforeLast('/')) as ObjectNode
        forelder.put(felt.peker.substringAfterLast('/'), verdi)
        return serialize(body)
    }

    @Test
    fun `validerer også vergemål i digital søknad`() {
        avviser(
            opprettUri,
            serialize(basis.copy(forNav = ForNavJson.DigitalSøknad(harFullmektigEllerVerge = mistenkelig))),
            forventetFeil("forNav.harFullmektigEllerVerge", "inneholder mistenkelig innhold"),
        )
    }

    private fun forventetFeil(sti: String, begrunnelse: String) = UgyldigInputValideringFeilResponse(
        message = "Ugyldig input",
        code = "ugyldig_felt_input",
        errors = listOf(UgyldigInputValideringsfeil(sti, begrunnelse)),
    )

    private fun avviser(uri: String, body: String, forventet: UgyldigInputValideringFeilResponse) {
        val søknadService = mock<SøknadService>()
        val avslagService = mock<AvslåSøknadManglendeDokumentasjonService>()
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(
                        søknad = søknadService,
                        avslåSøknadManglendeDokumentasjonService = avslagService,
                    ),
                )
            }
            defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                setBody(body)
            }.apply {
                status shouldBe HttpStatusCode.BadRequest
                deserialize<UgyldigInputValideringFeilResponse>(bodyAsText()) shouldBe forventet
            }
        }
        verifyNoInteractions(søknadService, avslagService)
    }

    @Test
    fun `avviser ett mistenkelig strengfelt om gangen før søknaden når service`() {
        felt.forEach { ugyldigFelt ->
            withClue(ugyldigFelt.sti) {
                avviser(
                    opprettUri,
                    bodyMedFelt(ugyldigFelt, mistenkelig),
                    forventetFeil(ugyldigFelt.sti, "inneholder mistenkelig innhold"),
                )
            }
        }
    }

    @Test
    fun `avviser mistenkelig fritekst i avslag og brevutkast`() {
        listOf(avslagUri, brevutkastUri).forEach { uri ->
            avviser(
                uri,
                serialize(AvslagBody(mistenkelig)),
                forventetFeil("fritekst", "inneholder mistenkelig innhold"),
            )
        }
    }

    @Test
    fun `avviser tegn utenfor tillatt tegnsett og viser tegnene`() {
        val ugyldigFelt = felt.first { kandidat -> kandidat.sti == "formue.boligBrukesTil" }
        avviser(
            opprettUri,
            bodyMedFelt(ugyldigFelt, "<b>hei</b>"),
            forventetFeil(ugyldigFelt.sti, "inneholder tegn utenfor tillatt tegnsett: '<>'"),
        )
    }

    @Test
    fun `bevarer egne lengdegrenser for søker og ektefelle`() {
        felt.filter { kandidat -> kandidat.maksLengde != null }.forEach { lengdeFelt ->
            val maksLengde = requireNotNull(lengdeFelt.maksLengde)
            val gyldigVerdi = "a".repeat(maksLengde)
            val søknadService = mock<SøknadService> {
                on { nySøknad(any(), any()) } doReturn KunneIkkeOppretteSøknad.FantIkkePerson.left()
            }
            withClue(lengdeFelt.sti) {
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(services = TestServicesBuilder.services(søknad = søknadService))
                    }
                    defaultRequest(HttpMethod.Post, opprettUri, listOf(Brukerrolle.Saksbehandler)) {
                        setBody(bodyMedFelt(lengdeFelt, gyldigVerdi))
                    }.status shouldBe HttpStatusCode.NotFound
                }
                verify(søknadService).nySøknad(any(), any())
                val forLangVerdi = "${gyldigVerdi}a"
                avviser(
                    opprettUri,
                    bodyMedFelt(lengdeFelt, forLangVerdi),
                    forventetFeil(lengdeFelt.sti, "for lang verdi, antall tegn ${forLangVerdi.length}, maks $maksLengde"),
                )
            }
        }
    }

    @Test
    fun `brevutkast godtar fritekst på grensen og bevarer pdf-svaret`() {
        val fritekst = "a".repeat(InputFelter.FRITEKST.maksLengde)
        val pdf = PdfA("pdf".toByteArray())
        val avslagService = mock<AvslåSøknadManglendeDokumentasjonService> {
            on { genererBrevForhåndsvisning(any()) } doReturn (fnr to pdf).right()
        }
        testApplication {
            application {
                testSusebakoverWithMockedDb(
                    services = TestServicesBuilder.services(avslåSøknadManglendeDokumentasjonService = avslagService),
                )
            }
            defaultRequest(HttpMethod.Post, brevutkastUri, listOf(Brukerrolle.Saksbehandler)) {
                setBody(serialize(AvslagBody(fritekst)))
            }.apply {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.Application.Pdf
                bodyAsText() shouldBe String(pdf.getContent())
            }
        }
        verify(avslagService).genererBrevForhåndsvisning(any())
    }

    @Test
    fun `avviser fritekst over grensen i avslag og brevutkast`() {
        val maksLengde = InputFelter.FRITEKST.maksLengde
        val forLangVerdi = "a".repeat(maksLengde + 1)
        listOf(avslagUri, brevutkastUri).forEach { uri ->
            avviser(
                uri,
                serialize(AvslagBody(forLangVerdi)),
                forventetFeil("fritekst", "for lang verdi, antall tegn ${forLangVerdi.length}, maks $maksLengde"),
            )
        }
    }

    @Test
    fun `avviser ikke-json og manglende påkrevde felt uten å kalle service`() {
        listOf(opprettUri, avslagUri, brevutkastUri).forEach { uri ->
            listOf("{ikke json", "{}").forEach { body ->
                val søknadService = mock<SøknadService>()
                val avslagService = mock<AvslåSøknadManglendeDokumentasjonService>()
                testApplication {
                    application {
                        testSusebakoverWithMockedDb(
                            services = TestServicesBuilder.services(
                                søknad = søknadService,
                                avslåSøknadManglendeDokumentasjonService = avslagService,
                            ),
                        )
                    }
                    defaultRequest(HttpMethod.Post, uri, listOf(Brukerrolle.Saksbehandler)) {
                        setBody(body)
                    }.apply {
                        status shouldBe HttpStatusCode.BadRequest
                        bodyAsText() shouldContain "ugyldig_body"
                    }
                }
                verifyNoInteractions(søknadService, avslagService)
            }
        }
    }
}
