package no.nav.su.se.bakover.common.infrastructure.job

import arrow.core.Either
import no.nav.su.se.bakover.common.CorrelationId
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
 *Vil starte en daemon thread som betyr at VMen ikke vil vente på denne tråden for å avslutte.
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
    return startStoppableJob(
        jobName = jobName,
        log = log,
        runJobCheck = runJobCheck,
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
    return startStoppableJob(
        jobName = jobName,
        log = log,
        runJobCheck = runJobCheck,
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

private fun startStoppableJob(
    jobName: String,
    log: Logger,
    runJobCheck: List<RunJobCheck>,
    job: (CorrelationId) -> Unit,
    scheduleJob: (TimerTask.() -> Unit) -> Timer,
): StoppableJob {
    return scheduleJob {
        Either.catch {
            runJobCheck.shouldRun().ifTrue {
                log.debug("Kjører skeduleringsjobb '$jobName'.")
                withCorrelationId { job(it) }
                log.debug("Fullførte skeduleringsjobb '$jobName'.")
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
