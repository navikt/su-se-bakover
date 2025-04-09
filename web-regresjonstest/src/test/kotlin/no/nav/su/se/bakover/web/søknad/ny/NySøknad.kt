package no.nav.su.se.bakover.web.søknad.ny

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.application.defaultRequest
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.web.SharedRegressionTestData
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

/**
 * Dersom det allerede finnes en sak knyttet til [fnr] opprettes det en ny søknad på den eksisterende saken
 */
fun nyDigitalSøknad(
    fnr: String = SharedRegressionTestData.fnr,
    client: HttpClient,
): String {
    return nySøknad(
        requestJson = NySøknadJson.Request.nyDigitalSøknad(
            fnr = fnr,
        ),
        brukerrolle = Brukerrolle.Veileder,
        client = client,
    )
}

fun nyDigitalAlderssøknad(
    brukerFnr: String = SharedRegressionTestData.fnr,
    epsFnr: String = SharedRegressionTestData.epsFnr,
    client: HttpClient,
): String {
    return nyAlderssøknad(
        requestJson = NySøknadJson.Request.nyDigitalAlderssøknad(
            brukerFnr = brukerFnr,
            epsFnr = epsFnr,
        ),
        brukerrolle = Brukerrolle.Veileder,
        client = client,
    )
}

/**
 * Emulerer at en veileder sender inn en digital søknad
 */
fun ApplicationTestBuilder.nyDigitalSøknadOgVerifiser(
    fnr: String = SharedRegressionTestData.fnr,
    expectedSaksnummerInResponse: Long,
): String {
    return nySøknadOgVerifiser(
        requestJson = NySøknadJson.Request.nyDigitalSøknad(
            fnr = fnr,
        ),
        expectedResponseJson = NySøknadJson.Response.nyDititalUføreSøknad(
            fnr = fnr,
            saksnummer = expectedSaksnummerInResponse,
        ),
        brukerrolle = Brukerrolle.Veileder,
    )
}

/**
 * Emulerer at en saksbehandler sender inn en papirsøknad
 * TODO jah: Bør teste at veiledere ikke har tilgang til å sende papirsøknader. Og bør vi teste det her eller i web?
 */
fun ApplicationTestBuilder.nyPapirsøknadOgVerifiser(
    fnr: String = SharedRegressionTestData.fnr,
    expectedSaksnummerInResponse: Long,
    mottaksdato: String = fixedLocalDate.toString(),
): String {
    return nySøknadOgVerifiser(
        requestJson = NySøknadJson.Request.nyPapirsøknad(
            fnr = fnr,
            mottaksdato = mottaksdato,
        ),
        expectedResponseJson = NySøknadJson.Response.nyPapirsøknad(
            fnr = fnr,
            saksnummer = expectedSaksnummerInResponse,
            mottaksdato = mottaksdato,
        ),
        brukerrolle = Brukerrolle.Saksbehandler,
    )
}

private fun ApplicationTestBuilder.nySøknadOgVerifiser(
    requestJson: String,
    expectedResponseJson: String,
    // TODO jah: Ref Auth; Åpne for å teste kode 6/7/egen ansatt.
    brukerrolle: Brukerrolle,
): String {
    return nySøknad(
        requestJson = requestJson,
        brukerrolle = brukerrolle,
        client = this.client,
    ).also {
        JSONAssert.assertEquals(
            expectedResponseJson,
            it,
            CustomComparator(
                JSONCompareMode.STRICT,
                Customization(
                    "søknad.id",
                ) { _, _ -> true },
                Customization(
                    "søknad.sakId",
                ) { _, _ -> true },
            ),
        )
    }
}

/**
 * Ny søknad har en deterministisk respons, så vi gjør bare assertingen inline.
 */
private fun nySøknad(
    requestJson: String,
    // TODO jah: Ref Auth; Åpne for å teste kode 6/7/egen ansatt.
    brukerrolle: Brukerrolle,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/soknad/ufore",
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(requestJson)
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}

private fun nyAlderssøknad(
    requestJson: String,
    // TODO jah: Ref Auth; Åpne for å teste kode 6/7/egen ansatt.
    brukerrolle: Brukerrolle,
    client: HttpClient,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Post,
            "/soknad/alder",
            listOf(brukerrolle),
            client = client,
        ) {
            setBody(requestJson)
        }.apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.Created
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
