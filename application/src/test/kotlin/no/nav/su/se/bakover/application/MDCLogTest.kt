package no.nav.su.se.bakover.application

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.application.LoggingTest.Companion.konfigurerLogback
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.test.jwt.asBearerToken
import no.nav.su.se.bakover.test.jwt.jwtStub
import no.nav.su.se.bakover.web.SharedRegressionTestData.testSusebakover
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
        testApplication {
            application {
                testSusebakover()
                (environment.log as Logger).also {
                    it.apply { addAppender(appender) }
                }
            }
            client.get("/me") {
                header(
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
