package no.nav.su.se.bakover.web.utenlandsopphold

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.defaultRequest

fun ApplicationTestBuilder.ugyldiggjørUtenlandsopphold(
    sakId: String,
    utenlandsoppholdId: String,
): String {
    return runBlocking {
        defaultRequest(
            HttpMethod.Delete,
            "/saker/$sakId/utenlandsopphold/$utenlandsoppholdId",
            listOf(Brukerrolle.Saksbehandler),
        ).apply {
            status shouldBe HttpStatusCode.OK
        }.bodyAsText()
    }
}
