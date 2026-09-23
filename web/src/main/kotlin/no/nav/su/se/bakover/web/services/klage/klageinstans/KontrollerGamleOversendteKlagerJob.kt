package no.nav.su.se.bakover.web.services.klage.klageinstans

import no.nav.su.se.bakover.common.domain.job.JobbNavn
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJobMedResultat
import no.nav.su.se.bakover.domain.klage.KlageRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.util.Date

internal class KontrollerGamleOversendteKlagerJob(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {

    companion object {
        fun startJob(
            klageRepo: KlageRepo,
            starttidspunkt: Date,
            periode: Duration,
            clock: Clock,
            runCheckFactory: RunCheckFactory,
        ): KontrollerGamleOversendteKlagerJob {
            val log = LoggerFactory.getLogger(KontrollerGamleOversendteKlagerJob::class.java)
            val jobbNavn = JobbNavn.KONTROLLER_GAMLE_OVERSENDTE_KLAGER.visningsnavn

            return startStoppableJobMedResultat(
                jobName = jobbNavn,
                startAt = starttidspunkt,
                intervall = periode,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod(), runCheckFactory.manTilFredag0600til2100()),
            ) {
                run(
                    klageRepo = klageRepo,
                    clock = clock,
                    log = log,
                )
            }.let(::KontrollerGamleOversendteKlagerJob)
        }

        fun run(
            klageRepo: KlageRepo,
            clock: Clock,
            log: Logger,
        ): JobbResultat {
            val grense = ZonedDateTime.now(clock.withZone(zoneIdOslo))
                .minusMonths(6)
                .toInstant()

            val gamleKlager = klageRepo.hentOversendteKlagerUtenKlageinstanshendelser()
                .map { it.attesteringer.hentSisteAttestering().opprettet }
                .filter { it.instant.isBefore(grense) }

            if (gamleKlager.isEmpty()) {
                return JobbResultat.Ok
            }

            val melding =
                "${gamleKlager.size} oversendte klager har ventet mer enn seks måneder på svar fra Klageinstansen. " +
                    "Eldste oversendelsestidspunkt er ${gamleKlager.minBy { it.instant }}."
            log.error(melding)
            return JobbResultat.DelvisFeilet(melding)
        }
    }
}
