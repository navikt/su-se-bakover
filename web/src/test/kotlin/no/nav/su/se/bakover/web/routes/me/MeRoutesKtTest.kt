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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class MeRoutesKtTest {
    private val meUri = "/me"

    @Test
    fun `GET me returnerer bare Statistikk når brukeren bare har statistikkrollen`() {
        val rolle = Brukerrolle.Statistikk
        testApplication {
            application { testSusebakoverWithMockedDb() }
            client.get(meUri) {
                header(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(roller = listOf(rolle)).asBearerToken(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<UserData>(bodyAsText()).roller.shouldContainExactly(rolle)
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Brukerrolle::class)
    fun `GET me should return NAVIdent and roller`(rolle: Brukerrolle) {
        val navIdent = "navidenten"
        testApplication {
            application { testSusebakoverWithMockedDb() }
            client.get(
                meUri,
            ) {
                header(
                    HttpHeaders.Authorization,

                    jwtStub.createJwtToken(
                        subject = "random",
                        roller = listOf(rolle),
                        navIdent = navIdent,
                    ).asBearerToken(),
                )
            }.apply {
                status shouldBe HttpStatusCode.OK
                deserialize<UserData>(bodyAsText()).let {
                    it.navIdent shouldBe navIdent
                    it.roller.shouldContainExactly(rolle)
                }
            }
        }
    }
}
