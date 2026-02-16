package no.nav.su.se.bakover.web

import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpHeaders.XCorrelationId
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.routing.Route
import io.ktor.util.AttributeKey
import no.nav.su.se.bakover.client.Clients
import no.nav.su.se.bakover.common.CORRELATION_ID_HEADER
import no.nav.su.se.bakover.common.infrastructure.brukerrolle.AzureGroupMapper
import no.nav.su.se.bakover.common.infrastructure.config.ApplicationConfig
import no.nav.su.se.bakover.common.infrastructure.metrics.SuMetrics
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.sikkerlogg
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.jacksonConverter
import no.nav.su.se.bakover.common.person.UgyldigFnrException
import no.nav.su.se.bakover.domain.DatabaseRepos
import no.nav.su.se.bakover.service.dokument.DistribuerDokumentService
import no.nav.su.se.bakover.web.routes.installMetrics
import no.nav.su.se.bakover.web.routes.naisPaths
import no.nav.su.se.bakover.web.routes.naisRoutes
import no.nav.su.se.bakover.web.services.AccessCheckProxy
import no.nav.su.se.bakover.web.services.Services
import no.nav.su.se.bakover.web.services.Tilgangssjekkfeil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import person.domain.KunneIkkeHentePerson
import tilbakekreving.presentation.Tilbakekrevingskomponenter
import vilkår.formue.domain.FormuegrenserFactory
import økonomi.application.utbetaling.ResendUtbetalingService
import java.io.EOFException
import java.io.IOException
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import java.nio.channels.ClosedChannelException
import java.time.Clock
import java.time.format.DateTimeParseException

internal fun Application.setupKtor(
    services: Services,
    tilbakekrevingskomponenter: Tilbakekrevingskomponenter,
    clock: Clock,
    applicationConfig: ApplicationConfig,
    accessCheckProxy: AccessCheckProxy,
    formuegrenserFactoryIDag: FormuegrenserFactory,
    databaseRepos: DatabaseRepos,
    clients: Clients,
    extraRoutes: Route.(services: Services) -> Unit,
    resendUtbetalingService: ResendUtbetalingService,
    suMetrics: SuMetrics,
    distribuerDokumentService: DistribuerDokumentService,
) {
    setupKtorExceptionHandling()

    installMetrics(suMetrics.prometheusMeterRegistry)
    naisRoutes(suMetrics.prometheusMeterRegistry)

    configureAuthentication(clients.azureAd, applicationConfig)
    val azureGroupMapper = AzureGroupMapper(applicationConfig.azure.groups)

    install(ContentNegotiation) {
        register(ContentType.Application.Json, jacksonConverter())
    }

    setupKtorCallId()
    setupKtorCallLogging(azureGroupMapper)

    install(XForwardedHeaders)
    setupKtorRoutes(
        services = services,
        clock = clock,
        applicationConfig = applicationConfig,
        accessCheckProxy = accessCheckProxy,
        extraRoutes = extraRoutes,
        azureGroupMapper = azureGroupMapper,
        formuegrenserFactoryIDag = formuegrenserFactoryIDag,
        databaseRepos = databaseRepos,
        clients = clients,
        tilbakekrevingskomponenter = tilbakekrevingskomponenter,
        resendUtbetalingService = resendUtbetalingService,
        distribuerDokumentService = distribuerDokumentService,
    )
}

const val BRUKER = "USER"
const val TOKENTYPE = "TOKENTYPE"
const val ROLLER = "ROLES"
const val TIME_USED = "TIMEUSED_MS"

private fun ApplicationCall.getJwtToken(): DecodedJWT? {
    val header = request.header(HttpHeaders.Authorization) ?: return null
    val raw = header.substringAfterLast("Bearer ").trim()
    return runCatching { JWT.decode(raw) }.getOrNull()
}

val EXCEPTIONATTRIBUTE_KEY = AttributeKey<Throwable>("exception_key")

private fun Application.setupKtorCallLogging(azureGroupMapper: AzureGroupMapper) {
    install(CallLogging) {
        level = Level.INFO
        filter { call ->
            return@filter call.shouldLogCall()
        }

        callIdMdc(CORRELATION_ID_HEADER)
        // Skulle egentlig benyttet idtype ihht https://docs.nais.io/auth/entra-id/reference/?h=azp_name#claims
        // Men jeg ser at den ikke er med i tokenet så vi sjekker bare på navident
        mdc(TOKENTYPE) { call ->
            call.getJwtToken()?.let { token ->
                val claims = token.claims
                val user = claims["NAVident"]?.asString()
                if (user == null) "MASKINBRUKER" else "PERSONBRUKER"
            }
        }
        mdc(TIME_USED) { call ->
            call.processingTimeMillis().toString()
        }
        mdc(BRUKER) { call ->
            call.getJwtToken()?.let { token ->
                val claims = token.claims
                val user = claims["NAVident"]?.asString() ?: claims["azp_name"]?.asString() ?: "Ukjent"
                user
            }
        }
        // Merk denne vil ikke logge sensitive roller da de ikke finnes i azureGroupMapper
        mdc(ROLLER) { call ->
            call.getJwtToken()?.let { token ->
                token.claims["groups"]?.asList(String::class.java)?.mapNotNull { azureGroupMapper.fromAzureGroup(it) }?.joinToString(",") ?: ""
            }
        }

        disableDefaultColors()
    }

    intercept(ApplicationCallPipeline.Monitoring) {
        try {
            proceed()
        } catch (cause: Throwable) {
            if (
                call.shouldLogCall() &&
                call.attributes.getOrNull(EXCEPTIONATTRIBUTE_KEY) == null &&
                !cause.isLikelyClientAbort()
            ) {
                call.attributes.put(EXCEPTIONATTRIBUTE_KEY, cause)
                val directBufferStats = getDirectBufferStats()
                call.application.log.error(
                    "Call failed method={} path={} correlationId={} status={} directBufferUsedMiB={} directBufferUsedPercent={}",
                    call.request.httpMethod,
                    call.request.path(),
                    call.callId,
                    call.response.status()?.value,
                    directBufferStats?.usedMiB,
                    directBufferStats?.usedPercentOfMax,
                    cause,
                )
            }
            throw cause
        }

        if (!call.shouldLogCall()) return@intercept

        val status = call.response.status() ?: return@intercept
        if (status.value >= 500 && call.attributes.getOrNull(EXCEPTIONATTRIBUTE_KEY) == null) {
            call.application.log.error(
                "5xx response: {} {} status={}",
                call.request.httpMethod,
                call.request.path(),
                status.value,
            )
        }
    }
}

private fun Application.setupKtorCallId() {
    install(CallId) {
        header(XCorrelationId)
        this.generate(length = 17)
        verify { it.isNotEmpty() }
    }
}

private fun Application.setupKtorExceptionHandling(
    log: Logger = LoggerFactory.getLogger("no.nav.su.se.bakover.web.Application.StatusPages"),
) {
    install(StatusPages) {
        exception<Tilgangssjekkfeil> { call, cause ->
            when (cause.feil) {
                is KunneIkkeHentePerson.IkkeTilgangTilPerson -> {
                    call.sikkerlogg("slo opp person hen ikke har tilgang til")
                    log.warn("[Tilgangssjekk] Ikke tilgang til person.", cause)
                    call.svar(Feilresponser.ikkeTilgangTilPerson)
                }

                is KunneIkkeHentePerson.FantIkkePerson -> {
                    log.warn("[Tilgangssjekk] Fant ikke person", cause)
                    call.svar(Feilresponser.fantIkkePerson)
                }

                is KunneIkkeHentePerson.Ukjent -> {
                    log.warn("[Tilgangssjekk] Feil ved oppslag på person", cause)
                    call.svar(Feilresponser.feilVedOppslagPåPerson)
                }
            }
        }
        exception<UgyldigFnrException> { call, cause ->
            log.warn("Got UgyldigFnrException with message=${cause.message}", cause)
            call.svar(
                BadRequest.errorJson(
                    message = cause.message ?: "Ugyldig fødselsnummer",
                    code = "ugyldig_fødselsnummer",
                ),
            )
        }
        exception<DateTimeParseException> { call, cause ->
            log.info("Got ${DateTimeParseException::class.simpleName} with message ${cause.message}")
            call.svar(
                BadRequest.errorJson(
                    message = "Ugyldig dato - datoer må være på isoformat",
                    code = "ugyldig_dato",
                ),
            )
        }
        exception<Throwable> { call, cause ->
            val alreadyLogged = call.attributes.getOrNull(EXCEPTIONATTRIBUTE_KEY) != null
            val isClientAbort = cause.isLikelyClientAbort()
            if (!alreadyLogged && !isClientAbort) {
                call.attributes.put(EXCEPTIONATTRIBUTE_KEY, cause)
                log.error("Got Throwable with message=${cause.message} routepath ${call.request.path()} method: ${call.request.httpMethod}", cause)
            }
            if (!isClientAbort) {
                call.svar(Feilresponser.ukjentFeil)
            }
        }
    }
}

private data class DirectBufferStats(
    val usedBytes: Long,
    val maxDirectMemoryBytes: Long?,
) {
    val usedMiB: String
        get() = formatMiB(usedBytes)

    val usedPercentOfMax: Long?
        get() = maxDirectMemoryBytes
            ?.takeIf { it > 0 }
            ?.let { (usedBytes * 100) / it }
}

private fun getDirectBufferStats(): DirectBufferStats? {
    val direct = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java)
        .firstOrNull { it.name.equals("direct", ignoreCase = true) }
        ?: return null

    return DirectBufferStats(
        usedBytes = direct.memoryUsed,
        maxDirectMemoryBytes = getConfiguredMaxDirectMemoryBytes(),
    )
}

private fun getConfiguredMaxDirectMemoryBytes(): Long? {
    val configuredMaxDirectMemory = ManagementFactory.getRuntimeMXBean().inputArguments
        .firstOrNull { it.startsWith("-XX:MaxDirectMemorySize=") }
        ?.substringAfter("=")
        ?: return null

    return parseJvmMemorySizeToBytes(configuredMaxDirectMemory)
}

private fun parseJvmMemorySizeToBytes(value: String): Long? {
    val normalized = value.trim()
    if (normalized.isEmpty()) return null

    val match = Regex("""(?i)^(\d+)([kmgt]?)b?$""").matchEntire(normalized)
    val amount = match?.groupValues?.get(1)?.toLongOrNull() ?: normalized.toLongOrNull() ?: return null
    val unit = match?.groupValues?.get(2)?.lowercase() ?: ""
    if (amount < 0) return null

    val multiplier = when (unit) {
        "" -> 1L
        "k" -> 1024L
        "m" -> 1024L * 1024L
        "g" -> 1024L * 1024L * 1024L
        "t" -> 1024L * 1024L * 1024L * 1024L
        else -> return null
    }

    if (amount > Long.MAX_VALUE / multiplier) return null
    return amount * multiplier
}

private fun formatMiB(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format("%.1f", mib)
}

private fun Throwable.isLikelyClientAbort(): Boolean {
    return causeChain().any { cause ->
        cause is ClosedChannelException ||
            cause is EOFException ||
            (cause is IOException && cause.message.hasAbortMessage()) ||
            cause::class.java.name.contains("ClientAbortException") ||
            cause::class.java.name.contains("PrematureChannelClosureException") ||
            (cause::class.java.name.contains("NativeIoException") && cause.message.hasAbortMessage())
    }
}

private fun Throwable.causeChain(maxDepth: Int = 8): Sequence<Throwable> = sequence {
    var current: Throwable? = this@causeChain
    var depth = 0
    while (current != null && depth < maxDepth) {
        yield(current)
        current = current.cause
        depth += 1
    }
}

private fun String?.hasAbortMessage(): Boolean {
    if (this == null) return false
    return this.contains("broken pipe", ignoreCase = true) ||
        this.contains("connection reset", ignoreCase = true) ||
        this.contains("forcibly closed", ignoreCase = true)
}

private fun ApplicationCall.pathShouldBeExcluded(paths: List<String>): Boolean {
    return paths.any {
        this.request.path().startsWith(it)
    }
}

private fun ApplicationCall.shouldLogCall(): Boolean {
    if (this.request.httpMethod.value == "OPTIONS") return false
    if (this.pathShouldBeExcluded(naisPaths)) return false
    return true
}
