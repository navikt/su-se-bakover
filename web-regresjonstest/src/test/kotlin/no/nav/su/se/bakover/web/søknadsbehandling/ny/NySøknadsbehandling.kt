package no.nav.su.se.bakover.web.søknadsbehandling.ny

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.contentType
import io.ktor.server.testing.setBody
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import no.nav.su.se.bakover.web.SharedRegressionTestData.testSusebakover
import javax.sql.DataSource

internal fun nySøknadsbehandling(
    sakId: String,
    søknadId: String,
    brukerrolle: Brukerrolle,
    dataSource: DataSource,
): String {
    val repos = SharedRegressionTestData.databaseRepos(dataSource)
    return withTestApplication(
        {
            testSusebakover(
                databaseRepos = repos,
            )
        },
    ) {
        defaultRequest(
            HttpMethod.Post,
            "/saker/$sakId/behandlinger",
            listOf(brukerrolle),
        ) {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("""{"soknadId":"$søknadId"}""")
        }.apply {
            response.status() shouldBe HttpStatusCode.Created
            response.contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
        }.response.content!!
    }
}
