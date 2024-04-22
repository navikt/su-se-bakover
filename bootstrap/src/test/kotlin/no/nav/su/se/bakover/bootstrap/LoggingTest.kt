package no.nav.su.se.bakover.bootstrap

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import com.papertrailapp.logback.Syslog4jAppender
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Isolated
import org.slf4j.LoggerFactory

@Isolated
class LoggingTest {

    @Test
    fun `produksjon`() {
        konfigurerLogback("logback.xml")
        getLogger("ROOT").getAppender("STDOUT_JSON") shouldBe beOfType<ConsoleAppender<ILoggingEvent>>()
        getLogger("auditLogger").getAppender("auditLogger") shouldBe beOfType<Syslog4jAppender<ILoggingEvent>>()
        getLogger("sikkerLogg").getAppender("secureAppender") shouldBe beOfType<RollingFileAppender<ILoggingEvent>>()
        getLogger("ROOT").getAppender("STDOUT") shouldBe null
    }

    @Test
    fun `local`() {
        konfigurerLogback("logback-local.xml")
        getLogger("ROOT").getAppender("STDOUT_JSON") shouldBe null
        getLogger("auditLogger").getAppender("auditLogger") shouldBe beOfType<ConsoleAppender<ILoggingEvent>>()
        getLogger("sikkerLogg").getAppender("secureAppender") shouldBe beOfType<ConsoleAppender<ILoggingEvent>>()
        getLogger("ROOT").getAppender("STDOUT") shouldBe beOfType<ConsoleAppender<ILoggingEvent>>()
    }

    @Test
    fun `test`() {
        konfigurerLogback("logback-test.xml")
        getLogger("ROOT").getAppender("STDOUT_JSON") shouldBe null
        getLogger("auditLogger").getAppender("auditLogger") shouldBe beOfType<ConsoleAppender<ILoggingEvent>>()
        getLogger("sikkerLogg").getAppender("secureAppender") shouldBe beOfType<ConsoleAppender<ILoggingEvent>>()
        getLogger("ROOT").getAppender("STDOUT") shouldBe beOfType<ConsoleAppender<ILoggingEvent>>()
    }

    private fun getLogger(navn: String): Logger {
        return LoggerFactory.getILoggerFactory().getLogger(navn) as Logger
    }

    companion object {
        internal fun konfigurerLogback(resource: String) {
            (LoggerFactory.getILoggerFactory() as LoggerContext).let { loggerContext ->
                loggerContext.reset()
                JoranConfigurator().apply { context = loggerContext }
                    .doConfigure(
                        LoggingTest::class.java.classLoader.getResourceAsStream(resource),
                    )
            }
        }

        @AfterAll
        @JvmStatic
        fun `konfigurer for test`() {
            konfigurerLogback("logback-test.xml")
        }
    }
}
