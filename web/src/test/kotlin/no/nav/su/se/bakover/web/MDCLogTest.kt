package no.nav.su.se.bakover.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.LoggingTest.Companion.konfigurerLogback
import no.nav.su.se.bakover.web.stubs.asBearerToken
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.slf4j.LoggerFactory

/**
 * Målrettet test av MDC verdier som logges av produksjonskonfig.
 * Overstyrer logback-text.xml (plukkes opp og konfigureres automatisk) ved å eksplisitt initiere logback på nytt
 * fra logback.xml. Tilbakestiller dette til logback-test etter endt kjøring. Kjøres i isolasjon for å unngå at
 * endringer i logg-oppsett påvirker andre tester.
 */
@Isolated
class MDCLogTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun `initialiser produksjons-konfigurasjon for logback`() {
            konfigurerLogback("logback.xml")
        }

        @AfterAll
        @JvmStatic
        fun `initialiser test-konfigurasjon for logback`() {
            konfigurerLogback("logback-test.xml")
        }
    }

    @Test
    fun `logs appropriate MDC values`() {
        val rootAppender =
            ((LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).getAppender("STDOUT_JSON")) as ConsoleAppender
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        lateinit var applog: Logger
        withTestApplication(
            {
                testSusebakover()
                applog = environment.log as Logger
            },
        ) {
            applog.apply { addAppender(appender) }
            handleRequest(HttpMethod.Get, secureEndpoint) {
                addHeader(
                    HttpHeaders.Authorization,
                    jwtStub.createJwtToken(roller = listOf(Brukerrolle.Veileder)).asBearerToken(),
                )
            }
        }
        val logStatement = appender.list.first { it.message.contains("200 OK") }
        val logbackFormatted = String(rootAppender.encoder.encode(logStatement))
        logStatement.mdcPropertyMap shouldContainKey "X-Correlation-ID"
        logStatement.mdcPropertyMap shouldContainKey "Authorization"
        logbackFormatted shouldContain "X-Correlation-ID"
        logbackFormatted shouldNotContain "Authorization"
    }
}
