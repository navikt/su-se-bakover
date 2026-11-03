package no.nav.su.se.bakover.web

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import no.nav.su.se.bakover.common.infrastructure.web.ErrorJson
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.web.services.Tilgangssjekkfeil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.Isolated
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.slf4j.LoggerFactory
import person.domain.KunneIkkeHentePerson
import java.time.LocalDate

// LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) er ikke thread safe
@Isolated
@Execution(value = ExecutionMode.SAME_THREAD)
class WebSetupLoggingTest {
    private data class ValideringsBody(val antall: Int)

    private val errorLoggedHeader = "X-Su-Error-Logged"
    private val errorLogged = "true"

    @Test
    fun `ugyldig body logger kategori og korrelasjonsid uten rå input eller stacktrace`() {
        data class UgyldigBody(val antall: String)
        val logger = mock<org.slf4j.Logger>()
        val korrelasjonsId = "test-ugyldig-body"
        testApplication {
            application {
                testSusebakoverWithMockedDb()
                routing {
                    post("/test/ugyldig-body") {
                        call.withBody<ValideringsBody>(log = logger) {
                            error("Ugyldig body skal ikke nå handleren")
                        }
                    }
                }
            }
            client.post("/test/ugyldig-body") {
                headers.append(HttpHeaders.XCorrelationId, korrelasjonsId)
                setBody(serialize(UgyldigBody("ugyldig verdi som ikke skal logges")))
            }.status shouldBe HttpStatusCode.BadRequest
        }
        verify(logger).info("Avvist input kategori=ugyldig_body correlationId={}", korrelasjonsId)
        verifyNoMoreInteractions(logger)
    }

    @Test
    fun `ugyldige datoer og fødselsnumre logges uten rå input og stacktrace`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val loggers = attachTestAppender(appender)
        val korrelasjonsId = "test-parserfeil"
        val ugyldigInput = "ugyldig verdi som ikke skal logges"
        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    routing {
                        get("/test/ugyldig-dato") {
                            LocalDate.parse(ugyldigInput)
                        }
                        get("/test/ugyldig-fnr") {
                            Fnr(ugyldigInput)
                        }
                    }
                }
                listOf("/test/ugyldig-dato", "/test/ugyldig-fnr").forEach { path ->
                    val response = client.get(path) {
                        headers.append(HttpHeaders.XCorrelationId, korrelasjonsId)
                    }
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.headers[errorLoggedHeader] shouldBe null
                }
            }
            val valideringslogger = appender.list.filter { it.formattedMessage.startsWith("Avvist input kategori=") }
            valideringslogger.map { it.formattedMessage }.shouldContainExactly(
                "Avvist input kategori=ugyldig_dato correlationId=$korrelasjonsId",
                "Avvist input kategori=ugyldig_fødselsnummer correlationId=$korrelasjonsId",
            )
            valideringslogger.forEach {
                it.level shouldBe Level.INFO
                it.throwableProxy shouldBe null
            }
            appender.list.none { it.formattedMessage.contains(ugyldigInput) } shouldBe true
            appender.list.none { it.level == Level.ERROR } shouldBe true
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

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
                response.headers[errorLoggedHeader] shouldBe errorLogged
            }

            val errorMessages = appender.list
                .filter { it.level == Level.ERROR }
                .map { it.formattedMessage }

            errorMessages.filter { it.contains("Call failed method=GET path=/test/runtime-exception") }
                .size shouldBe 1

            errorMessages.filter { it.contains("Got Throwable with message=boom routepath /test/runtime-exception method: GET") }
                .shouldContainExactly(emptyList())
            errorMessages.none { it.startsWith("5xx response:") } shouldBe true
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    @Test
    fun `should log 5xx response as error before sending it without exception`() {
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
                response.headers[errorLoggedHeader] shouldBe errorLogged
            }

            val errorMessages = appender.list
                .filter { it.level == Level.ERROR }
                .map { it.formattedMessage }

            errorMessages.filter { it.contains("5xx response: GET /test/status-500 status=500") }
                .size shouldBe 1
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    @Test
    fun `eksplisitt 500 logger feilkode og korrelasjonsid selv om requesten hevder at feilen er logget`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val loggers = attachTestAppender(appender)
        val path = "/test/kontrollert-500"
        val feilkode = "kontrollert_feil"
        val feilmelding = "Privat innhold som ikke skal logges"
        val korrelasjonsId = "test-kontrollert-500"
        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    routing {
                        get(path) {
                            call.svar(
                                HttpStatusCode.InternalServerError.errorJson(
                                    message = feilmelding,
                                    code = feilkode,
                                ),
                            )
                        }
                    }
                }

                val response = client.get(path) {
                    headers.append(errorLoggedHeader, errorLogged)
                    headers.append(HttpHeaders.XCorrelationId, korrelasjonsId)
                }
                response.status shouldBe HttpStatusCode.InternalServerError
                response.headers[errorLoggedHeader] shouldBe errorLogged
            }

            appender.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }.shouldContainExactly(
                "5xx response: GET $path status=500 correlationId=$korrelasjonsId errorCodes=[$feilkode]",
            )
            appender.list.none { it.formattedMessage.contains(feilmelding) } shouldBe true
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    @Test
    fun `503 med flere feil logger bare feilkodene og markerer responsen som logget`() {
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val loggers = attachTestAppender(appender)
        val path = "/test/flere-feil"
        val feilmelding = "Privat innhold som ikke skal logges"
        val førsteFeilkode = "første_feil"
        val andreFeilkode = "andre_feil"
        val korrelasjonsId = "test-flere-feil"
        val feil = listOf(
            ErrorJson(message = feilmelding, code = førsteFeilkode),
            ErrorJson(message = feilmelding, code = andreFeilkode),
        )
        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    routing {
                        get(path) {
                            call.svar(HttpStatusCode.ServiceUnavailable.errorJson(feil))
                        }
                    }
                }

                val response = client.get(path) {
                    headers.append(HttpHeaders.XCorrelationId, korrelasjonsId)
                }
                response.status shouldBe HttpStatusCode.ServiceUnavailable
                response.headers[errorLoggedHeader] shouldBe errorLogged
            }

            appender.list.filter { it.level == Level.ERROR }.map { it.formattedMessage }.shouldContainExactly(
                "5xx response: GET $path status=503 correlationId=$korrelasjonsId errorCodes=[$førsteFeilkode, $andreFeilkode]",
            )
            appender.list.none { it.formattedMessage.contains(feilmelding) } shouldBe true
        } finally {
            detachTestAppender(appender, loggers)
        }
    }

    @Test
    fun `5xx får ikke logget-header når error-logging er slått av`() {
        var applicationLogger: Logger? = null
        var previousLevel: Level? = null
        val path = "/test/uten-error-logging"
        try {
            testApplication {
                application {
                    testSusebakoverWithMockedDb()
                    val logger = log as Logger
                    applicationLogger = logger
                    previousLevel = logger.level
                    logger.level = Level.OFF
                    routing {
                        get(path) {
                            call.respondText("500", status = HttpStatusCode.InternalServerError)
                        }
                    }
                }

                val response = client.get(path)
                response.status shouldBe HttpStatusCode.InternalServerError
                response.headers[errorLoggedHeader] shouldBe null
            }
        } finally {
            applicationLogger?.level = previousLevel
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
                response.headers[errorLoggedHeader] shouldBe null
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
                response.headers[errorLoggedHeader] shouldBe errorLogged
            }

            val errorMessages = appender.list
                .filter { it.level == Level.ERROR }
                .map { it.formattedMessage }

            errorMessages.filter { it.contains("Call failed method=GET path=/test/tilgangssjekkfeil-ukjent") }
                .size shouldBe 1
            errorMessages.none { it.startsWith("5xx response:") } shouldBe true
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
