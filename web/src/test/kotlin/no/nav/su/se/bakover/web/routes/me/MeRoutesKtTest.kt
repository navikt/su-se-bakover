package no.nav.su.se.bakover.web.routes.me

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.HttpHeaders
import io.ktor.server.http.HttpMethod
import io.ktor.server.server.testing.handleRequest
import io.ktor.server.server.testing.withTestApplication
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.stubs.asBearerToken
import no.nav.su.se.bakover.web.testSusebakover
import org.junit.jupiter.api.Test

internal class MeRoutesKtTest {

    @Test
    fun `GET me should return NAVIdent and roller`() {
        testApplication(
            {
                testSusebakover()
            },
        ) {
            handleRequest(
                HttpMethod.Get,
                "/me",
            ) {
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant),
                        navIdent = "navidenten",
                    ).asBearerToken(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<UserData>(response.content!!).let {
                    it.navIdent shouldBe "navidenten"
                    it.roller.shouldContainExactly(Brukerrolle.Attestant)
                }
            }
        }
    }
}
