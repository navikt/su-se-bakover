package økonomi.infrastructure.kvittering.job

import no.nav.su.se.bakover.common.domain.job.JobbNavn
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJobMedResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.utbetaling.UtbetalingRepo
import java.time.Clock
import java.time.Duration

class KontrollerUkvitterteUtbetalingerJob private constructor(
    private val stoppableJob: StoppableJob,
) : StoppableJob by stoppableJob {
    companion object {
        fun startJob(
            utbetalingRepo: UtbetalingRepo,
            clock: Clock,
            initialDelay: Duration,
            intervall: Duration,
            maksVentetid: Duration,
            runCheckFactory: RunCheckFactory,
        ): KontrollerUkvitterteUtbetalingerJob {
            val log = LoggerFactory.getLogger(KontrollerUkvitterteUtbetalingerJob::class.java)
            return startStoppableJobMedResultat(
                jobName = JobbNavn.KONTROLLER_UKVITTERTE_UTBETALINGER.visningsnavn,
                initialDelay = initialDelay,
                intervall = intervall,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod()),
            ) {
                run(
                    utbetalingRepo = utbetalingRepo,
                    clock = clock,
                    maksVentetid = maksVentetid,
                    log = log,
                )
            }.let(::KontrollerUkvitterteUtbetalingerJob)
        }

        internal fun run(
            utbetalingRepo: UtbetalingRepo,
            clock: Clock,
            maksVentetid: Duration,
            log: Logger,
        ): JobbResultat {
            val sisteAkseptableTidspunkt = clock.instant().minus(maksVentetid)
            val forsinkedeUtbetalinger = utbetalingRepo.hentUkvitterteUtbetalinger()
                .filter { !it.opprettet.instant.isAfter(sisteAkseptableTidspunkt) }

            if (forsinkedeUtbetalinger.isEmpty()) return JobbResultat.Ok

            val eldsteUtbetaling = forsinkedeUtbetalinger.minBy { it.opprettet }
            val feilmelding =
                "Fant ${forsinkedeUtbetalinger.size} utbetaling(er) som har ventet minst ${maksVentetid.toHours()} timer på kvittering fra OS. " +
                    "Eldste utbetaling ble opprettet ${eldsteUtbetaling.opprettet}."
            log.error(feilmelding)
            return JobbResultat.DelvisFeilet(feilmelding)
        }
    }
}
