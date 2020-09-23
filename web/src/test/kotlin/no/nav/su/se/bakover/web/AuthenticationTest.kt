package no.nav.su.se.bakover.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import io.ktor.http.HttpStatusCode.Companion.Found
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.common.Config
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Date

const val secureEndpoint = "/authenticated"

internal class AuthenticationTest {

    @Test
    fun `secure endpoint krever autentisering`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(Get, secureEndpoint)
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `secure endpoint ok med gyldig token`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Get, secureEndpoint)
        }.apply {
            assertEquals(OK, response.status())
        }
    }

    @Test
    fun `forespørsel uten påkrevet audience skal svare med 403`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(Authorization, Jwt.create(audience = "wrong_audience"))
            }
        }.apply {
            assertEquals(Forbidden, response.status())
        }
    }

    @Test
    fun `forespørsel uten medlemskap i påkrevet gruppe skal svare med 403`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(Authorization, Jwt.create(groups = listOf("WRONG_GROUP_UUID")))
            }
        }.apply {
            assertEquals(Forbidden, response.status())
        }
    }

    @Test
    fun `forespørsel med utgått token skal svare med 401`() {
        withTestApplication({
            testSusebakover()
        }) {
            handleRequest(Get, secureEndpoint) {
                addHeader(Authorization, Jwt.create(expiresAt = Date.from(Instant.now().minusSeconds(1))))
            }
        }.apply {
            assertEquals(Unauthorized, response.status())
        }
    }

    @Test
    fun `skal ikke logge access eller refresh token ved redirect til frontend`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        lateinit var applog: Logger
        withTestApplication({
            testSusebakover()
            applog = environment.log as Logger
        }) {
            applog.apply { addAppender(appender) }
            defaultRequest(Get, "/callback?code=code&state=state&session_state=session_state") {
            }
        }.apply {
            appender.list.forEach {
                it.message shouldNotContain "302 Found"
                it.message shouldNotContain "callback"
            }
            response.status() shouldBe Found
            response.headers["Location"] shouldBe "${Config.suSeFramoverRedirectUrl}#access#refresh"
        }
    }

    @Test
    fun `kan refreshe tokens`() {
        withTestApplication({
            testSusebakover()
        }) {
            defaultRequest(Get, "auth/refresh") {
                addHeader("refresh_token", "my.refresh.token")
            }
        }.apply {
            assertTrue(response.headers.contains("access_token"))
            assertTrue(response.headers.contains("refresh_token"))
            assertEquals(OK, response.status())
        }
    }
}
