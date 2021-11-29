package no.nav.su.se.bakover.web.søknad.ny

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.HttpHeaders.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.databaseRepos
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import no.nav.su.se.bakover.web.SharedRegressionTestData.testSusebakover
import org.skyscreamer.jsonassert.Customization
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.skyscreamer.jsonassert.comparator.CustomComparator
import javax.sql.DataSource

fun nyDigitalSøknad(
    fnr: String = SharedRegressionTestData.fnr,
    dataSource: DataSource,
): String {
    return nySøknad(
        requestJson = NySøknadJson.Request.nyDigitalSøknad(
            fnr = fnr,
        ),
        brukerrolle = Brukerrolle.Veileder,
        dataSource = dataSource,
    )
}

/**
 * Emulerer at en veileder sender inn en digital søknad
 */
fun nyDigitalSøknadOgVerifiser(
    fnr: String = SharedRegressionTestData.fnr,
    expectedSaksnummerInResponse: Long,
    dataSource: DataSource,
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
        dataSource = dataSource,
    )
}

/**
 * Emulerer at en saksbehandler sender inn en papirsøknad
 * TODO jah: Bør teste at veiledere ikke har tilgang til å sende papirsøknader. Og bør vi teste det her eller i web?
 */
fun nyPapirsøknadOgVerifiser(
    fnr: String = SharedRegressionTestData.fnr,
    expectedSaksnummerInResponse: Long,
    mottaksdato: String = fixedLocalDate.toString(),
    dataSource: DataSource,
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
        dataSource = dataSource,
    )
}

private fun nySøknadOgVerifiser(
    requestJson: String,
    expectedResponseJson: String,
    brukerrolle: Brukerrolle, // TODO jah: Ref Auth; Åpne for å teste kode 6/7/egen ansatt.
    dataSource: DataSource,
): String {
    return nySøknad(
        requestJson = requestJson,
        brukerrolle = brukerrolle,
        dataSource = dataSource,
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
    brukerrolle: Brukerrolle, // TODO jah: Ref Auth; Åpne for å teste kode 6/7/egen ansatt.
    dataSource: DataSource,
): String {
    val repos = databaseRepos(dataSource)
    return withTestApplication(
        {
            testSusebakover(
                databaseRepos = repos,
            )
        },
    ) {
        defaultRequest(
            HttpMethod.Post,
            "/soknad",
            listOf(brukerrolle),
        ) {
            addHeader(ContentType, Json.toString())
            setBody(requestJson)
        }.apply {
            response.status() shouldBe HttpStatusCode.Created
        }.response.content!!
    }
}
