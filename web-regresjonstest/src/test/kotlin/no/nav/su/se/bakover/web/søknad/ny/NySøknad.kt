package no.nav.su.se.bakover.web.søknad.ny

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import no.nav.su.se.bakover.domain.bruker.Brukerrolle
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator

/**
 * Dersom det allerede finnes en sak knyttet til [fnr] opprettes det en ny søknad på den eksisterende saken
 */
fun TestApplicationEngine.nyDigitalSøknad(
    fnr: String = SharedRegressionTestData.fnr,
): String {
    return nySøknad(
        requestJson = NySøknadJson.Request.nyDigitalSøknad(
            fnr = fnr,
        ),
        brukerrolle = Brukerrolle.Veileder,
    )
}

/**
 * Emulerer at en veileder sender inn en digital søknad
 */
fun TestApplicationEngine.nyDigitalSøknadOgVerifiser(
    fnr: String = SharedRegressionTestData.fnr,
    expectedSaksnummerInResponse: Long,
): String {
    return nySøknadOgVerifiser(
        requestJson = NySøknadJson.Request.nyDigitalSøknad(
            fnr = fnr,
        ),
        expectedResponseJson = NySøknadJson.Response.nyDititalSøknad(
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
fun TestApplicationEngine.nyPapirsøknadOgVerifiser(
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

private fun TestApplicationEngine.nySøknadOgVerifiser(
    requestJson: String,
    expectedResponseJson: String,
    brukerrolle: Brukerrolle, // TODO jah: Ref Auth; Åpne for å teste kode 6/7/egen ansatt.
): String {
    return nySøknad(
        requestJson = requestJson,
        brukerrolle = brukerrolle,
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
private fun TestApplicationEngine.nySøknad(
    requestJson: String,
    brukerrolle: Brukerrolle, // TODO jah: Ref Auth; Åpne for å teste kode 6/7/egen ansatt.
): String {
    return defaultRequest(
        HttpMethod.Post,
        "/soknad",
        listOf(brukerrolle),
    ) {
        addHeader(ContentType, Json.toString())
        setBody(requestJson)
    }.apply {
        response.status() shouldBe HttpStatusCode.Created
        response.contentType() shouldBe io.ktor.http.ContentType.parse("application/json; charset=UTF-8")
    }.response.content!!
}
