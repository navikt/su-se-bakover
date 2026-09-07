package no.nav.su.se.bakover.common.infrastructure.job

import arrow.core.Either
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.domain.job.JobbKjøring
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.infrastructure.correlation.withCorrelationId
import no.nav.su.se.bakover.common.sikkerLogg
import org.jetbrains.kotlin.utils.addToStdlib.ifTrue
import org.slf4j.Logger
import java.time.Duration
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.fixedRateTimer

interface StoppableJob {
    val jobName: String

    /**
     * Idempotent. Forventer bare at ingen nye jobber blir startet. Pågående kjører ferdig.
     */
    fun stop()
}

/**
 * Starter en jobb som venter [initialDelay] før den kjører jobben i et fast [intervall].
 * Vil starte en daemon thread som betyr at VMen ikke vil vente på denne tråden for å avslutte.
 *
 * @param job Wrappes i en correlationId og en try-catch for å logge eventuelle feil.
 * @param runJobCheck Liste av RunJobCheck som må returnere true for at jobben skal kjøre. Ved tom liste, kjøres jobben alltid.
 */
fun startStoppableJob(
    jobName: String,
    initialDelay: Duration,
    intervall: Duration,
    log: Logger,
    runJobCheck: List<RunJobCheck>,
    job: (CorrelationId) -> Unit,
): StoppableJob {
    log.info("Starter skeduleringsjobb '$jobName'. Intervall: hvert ${intervall.toMinutes()}. minutt. Initial delay: ${initialDelay.toMinutes()} minutt(er)")

    return startStoppableJobInternal(
        jobName = jobName,
        log = log,
        runJobCheck = runJobCheck,
        intervall = intervall,
        job = { correlationId ->
            job(correlationId)
            JobbResultat.Ok
        },
    ) {
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
            action = it,
        )
    }
}

/**
 * Som [startStoppableJob], men jobben returnerer [JobbResultat] for å signalisere delvise feil.
 */
fun startStoppableJobMedResultat(
    jobName: String,
    initialDelay: Duration,
    intervall: Duration,
    log: Logger,
    runJobCheck: List<RunJobCheck>,
    job: (CorrelationId) -> JobbResultat,
): StoppableJob {
    log.info("Starter skeduleringsjobb '$jobName'. Intervall: hvert ${intervall.toMinutes()}. minutt. Initial delay: ${initialDelay.toMinutes()} minutt(er)")

    return startStoppableJobInternal(
        jobName = jobName,
        log = log,
        runJobCheck = runJobCheck,
        intervall = intervall,
        job = job,
    ) {
        fixedRateTimer(
            name = jobName,
            daemon = true,
            initialDelay = initialDelay.toMillis(),
            period = intervall.toMillis(),
            action = it,
        )
    }
}

/**
 * Starter en jobb som venter til et gitt tidspunkt ([startAt]) før den kjører jobben i et fast [intervall].
 * Vil starte en daemon thread som betyr at VMen ikke vil vente på denne tråden for å avslutte.
 *
 * @param job Wrappes i en correlationId og en try-catch for å logge eventuelle feil.
 * @param runJobCheck Liste av RunJobCheck som må returnere true for at jobben skal kjøre. Default er en tom liste. Ved tom liste, kjøres jobben alltid.
 */
fun startStoppableJob(
    jobName: String,
    startAt: Date,
    intervall: Duration,
    log: Logger,
    runJobCheck: List<RunJobCheck>,
    job: (CorrelationId) -> Unit,
): StoppableJob {
    log.info("Starter skeduleringsjobb '$jobName'. Intervall: hvert ${intervall.toMinutes()}. minutt. Starter kl. $startAt.")
    return startStoppableJobInternal(
        jobName = jobName,
        log = log,
        runJobCheck = runJobCheck,
        intervall = intervall,
        job = { correlationId ->
            job(correlationId)
            JobbResultat.Ok
        },
    ) {
        fixedRateTimer(
            name = jobName,
            daemon = true,
            startAt = startAt,
            period = intervall.toMillis(),
            action = it,
        )
    }
}

/**
 * Som [startStoppableJob] med startAt, men jobben returnerer [JobbResultat] for å signalisere delvise feil.
 */
fun startStoppableJobMedResultat(
    jobName: String,
    startAt: Date,
    intervall: Duration,
    log: Logger,
    runJobCheck: List<RunJobCheck>,
    job: (CorrelationId) -> JobbResultat,
): StoppableJob {
    log.info("Starter skeduleringsjobb '$jobName'. Intervall: hvert ${intervall.toMinutes()}. minutt. Starter kl. $startAt.")
    return startStoppableJobInternal(
        jobName = jobName,
        log = log,
        runJobCheck = runJobCheck,
        intervall = intervall,
        job = job,
    ) {
        fixedRateTimer(
            name = jobName,
            daemon = true,
            startAt = startAt,
            period = intervall.toMillis(),
            action = it,
        )
    }
}

private val tracer = GlobalOpenTelemetry.getTracer("no.nav.su.se.bakover.jobs")
fun wrapJobWithOtel(
    jobName: String,
    log: Logger,
    job: (CorrelationId) -> JobbResultat,
): (CorrelationId) -> JobbResultat = { correlationId ->
    val span = tracer.spanBuilder(jobName)
        .setSpanKind(SpanKind.INTERNAL)
        .startSpan()

    try {
        span.makeCurrent().use {
            job(correlationId)
        }
    } catch (ex: Exception) {
        span.recordException(ex)
        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, ex.message ?: "Ukjent feil")
        log.error("Skeduleringsjobb '$jobName' feilet", ex)
        throw ex
    } finally {
        span.end()
    }
}

private fun startStoppableJobInternal(
    jobName: String,
    log: Logger,
    runJobCheck: List<RunJobCheck>,
    intervall: Duration,
    job: (CorrelationId) -> JobbResultat,
    scheduleJob: (TimerTask.() -> Unit) -> Timer,
): StoppableJob {
    val jobWithSpan = wrapJobWithOtel(jobName, log, job)
    val jobbKjøringRepo = JobbKjøringPersistering.hentRepo()
    return scheduleJob {
        Either.catch {
            runJobCheck.shouldRun().ifTrue {
                log.debug("Kjører skeduleringsjobb '$jobName'.")
                val kjøring = JobbKjøring.startet(jobbNavn = jobName, intervall = intervall)
                jobbKjøringRepo?.let {
                    Either.catch { it.lagre(kjøring) }.onLeft { e ->
                        log.warn("Kunne ikke lagre jobbkjøring-start for '$jobName'", e)
                    }
                }
                Either.catch {
                    var jobbResultat: JobbResultat = JobbResultat.Ok
                    withCorrelationId { jobbResultat = jobWithSpan(it) }
                    jobbResultat
                }.fold(
                    ifLeft = { throwable ->
                        jobbKjøringRepo?.let {
                            Either.catch { it.oppdater(kjøring.feilet(throwable.message)) }.onLeft { e ->
                                log.warn("Kunne ikke oppdatere jobbkjøring-feil for '$jobName'", e)
                            }
                        }
                        throw throwable
                    },
                    ifRight = { resultat ->
                        when (resultat) {
                            is JobbResultat.Ok -> {
                                jobbKjøringRepo?.let {
                                    Either.catch { it.oppdater(kjøring.fullført()) }.onLeft { e ->
                                        log.warn("Kunne ikke oppdatere jobbkjøring-fullført for '$jobName'", e)
                                    }
                                }
                                log.debug("Fullførte skeduleringsjobb '$jobName'.")
                            }
                            is JobbResultat.DelvisFeilet -> {
                                jobbKjøringRepo?.let {
                                    Either.catch { it.oppdater(kjøring.fullførtMedFeil(resultat.melding)) }.onLeft { e ->
                                        log.warn("Kunne ikke oppdatere jobbkjøring-delvis-feilet for '$jobName'", e)
                                    }
                                }
                                log.warn("Skeduleringsjobb '$jobName' fullført med delvise feil: ${resultat.melding}")
                            }
                        }
                    },
                )
            }
                ?: log.debug("Skeduleringsjobb '$jobName' kjører ikke pga. startKriterier i runJobCheck. Eksempelvis er vi ikke leader pod.")
        }.onLeft {
            log.error(
                "Skeduleringsjobb '$jobName' feilet. Se sikkerlog for mer kontekst.",
                RuntimeException("Trigger stacktrace for enklere debug."),
            )
            sikkerLogg.error("Skeduleringsjobb '$jobName' feilet med stacktrace:", it)
        }
    }.let { timer ->
        object : StoppableJob {
            override val jobName = jobName
            override fun stop() {
                Either.catch {
                    timer.cancel()
                }.onRight {
                    log.info("Skeduleringsjobb '$jobName' stoppet. Pågående kjøringer ferdigstilles.")
                }.onLeft {
                    log.error("Skeduleringsjobb '$jobName': Feil ved kall til stop()/kanseller Timer.", it)
                }
            }
        }
    }
}
