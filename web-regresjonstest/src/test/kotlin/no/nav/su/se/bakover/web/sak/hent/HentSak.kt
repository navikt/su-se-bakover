package no.nav.su.se.bakover.web.sak.hent

import io.kotest.matchers.shouldBe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest
import no.nav.su.se.bakover.web.SharedRegressionTestData.testSusebakover
import javax.sql.DataSource

/**
 * TODO jah: Autentisering/Autorisering (Gjelder generelt for integrasjonsendepunktene).
 *  Denne skal f.eks. kun være tilgjengelig for saksbehandler/attestant.
 *  I tillegg er visse personer/saker beskyttet. Kode 6/7/Egen ansatt.
 */
internal fun hentSak(sakId: String, dataSource: DataSource): String {
    val repos = SharedRegressionTestData.databaseRepos(dataSource)
    return withTestApplication(
        {
            testSusebakover(databaseRepos = repos)
        },
    ) {
        defaultRequest(
            HttpMethod.Get,
            "/saker/$sakId",
            listOf(Brukerrolle.Saksbehandler),
        ) {
            addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        }.apply {
            response.status() shouldBe HttpStatusCode.OK
        }.response.content!!
    }
}
