package no.nav.su.se.bakover.web.routes.me

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.web.jwtStub
import no.nav.su.se.bakover.web.testSusebakoverWithMockedDb
import org.junit.jupiter.api.Test

internal class MeRoutesKtTest {

    @Test
    fun `GET me should return NAVIdent and roller`() {
        testApplication {
            application { testSusebakoverWithMockedDb() }
            client.get(
                "/me",
            ) {
                header(
                    HttpHeaders.Authorization,

                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(Brukerrolle.Attestant),
                        navIdent = "navidenten",
                    ).asBearerToken(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<UserData>(bodyAsText()).let {
                    it.navIdent shouldBe "navidenten"
                    it.roller.shouldContainExactly(Brukerrolle.Attestant)
                }
            }
        }
    }
}
