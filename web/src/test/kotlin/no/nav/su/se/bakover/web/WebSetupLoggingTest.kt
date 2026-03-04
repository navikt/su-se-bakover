package no.nav.su.se.bakover.web

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.web.services.Tilgangssjekkfeil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.slf4j.LoggerFactory
import person.domain.KunneIkkeHentePerson

// LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) er ikke thread safe
@Execution(value = ExecutionMode.SAME_THREAD)
class WebSetupLoggingTest {

    @Test
    fun `should log call failed once when route throws`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val loggers = attachTestAppender(appender)

        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    routing {
                        get("/test/runtime-exception") {
                            throw RuntimeException("boom")
                        }
                    }
                }

                val response = client.get("/test/runtime-exception")
                response.status shouldBe HttpStatusCode.InternalServerError
            }

            val errorMessages = appender.list
                .filter { it.level == Level.ERROR }
                .map { it.formattedMessage }

            errorMessages.filter { it.contains("Call failed method=GET path=/test/runtime-exception") }
                .size shouldBe 1

            errorMessages.filter { it.contains("Got Throwable with message=boom routepath /test/runtime-exception method: GET") }
                .shouldContainExactly(emptyList())
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    @Test
    fun `should log 5xx response when route sets status without exception`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val loggers = attachTestAppender(appender)

        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    routing {
                        get("/test/status-500") {
                            call.respondText("500", status = HttpStatusCode.InternalServerError)
                        }
                    }
                }

                val response = client.get("/test/status-500")
                response.status shouldBe HttpStatusCode.InternalServerError
            }

            val warnMessages = appender.list
                .filter { it.level == Level.WARN }
                .map { it.formattedMessage }

            warnMessages.filter { it.contains("5xx response: GET /test/status-500 status=500") }
                .size shouldBe 1
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    @Test
    fun `should not log call failed for tilgangssjekkfeil`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val loggers = attachTestAppender(appender)

        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    routing {
                        get("/test/tilgangssjekkfeil") {
                            throw Tilgangssjekkfeil(
                                feil = KunneIkkeHentePerson.FantIkkePerson,
                                fnr = Fnr("12345678910"),
                            )
                        }
                    }
                }

                val response = client.get("/test/tilgangssjekkfeil")
                response.status shouldBe HttpStatusCode.NotFound
            }

            val errorMessages = appender.list
                .filter { it.level == Level.ERROR }
                .map { it.formattedMessage }
            val warnMessages = appender.list
                .filter { it.level == Level.WARN }
                .map { it.formattedMessage }

            errorMessages.filter { it.contains("Call failed method=GET path=/test/tilgangssjekkfeil") }
                .shouldContainExactly(emptyList())

            warnMessages.filter { it.contains("[Tilgangssjekk] Fant ikke person") }
                .size shouldBe 1
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    @Test
    fun `should log call failed for tilgangssjekkfeil ukjent`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val loggers = attachTestAppender(appender)

        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    routing {
                        get("/test/tilgangssjekkfeil-ukjent") {
                            throw Tilgangssjekkfeil(
                                feil = KunneIkkeHentePerson.Ukjent,
                                fnr = Fnr("12345678910"),
                            )
                        }
                    }
                }

                val response = client.get("/test/tilgangssjekkfeil-ukjent")
                response.status shouldBe HttpStatusCode.InternalServerError
            }

            val errorMessages = appender.list
                .filter { it.level == Level.ERROR }
                .map { it.formattedMessage }

            errorMessages.filter { it.contains("Call failed method=GET path=/test/tilgangssjekkfeil-ukjent") }
                .size shouldBe 1
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    private fun attachTestAppender(appender: ListAppender<ILoggingEvent>): List<Logger> {
        val loggers = listOf(
            LoggerFactory.getLogger("io.ktor.test") as Logger,
            LoggerFactory.getLogger("io.ktor.server.Application") as Logger,
            LoggerFactory.getLogger("no.nav.su.se.bakover.web.Application.StatusPages") as Logger,
        )
        loggers.forEach { it.addAppender(appender) }
        return loggers
    }

    private fun detachTestAppender(appender: ListAppender<ILoggingEvent>, loggers: List<Logger>) {
        loggers.forEach { it.detachAppender(appender) }
        appender.stop()
    }
}
