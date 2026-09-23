package økonomi.infrastructure.kvittering.job

import no.nav.su.se.bakover.common.domain.job.JobbNavn
import no.nav.su.se.bakover.common.domain.job.JobbResultat
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.infrastructure.job.RunCheckFactory
import no.nav.su.se.bakover.common.infrastructure.job.StoppableJob
import no.nav.su.se.bakover.common.infrastructure.job.startStoppableJobMedResultat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import økonomi.domain.utbetaling.UtbetalingRepo
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime

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
            ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
        ): KontrollerUkvitterteUtbetalingerJob {
            val log = LoggerFactory.getLogger(KontrollerUkvitterteUtbetalingerJob::class.java)
            return startStoppableJobMedResultat(
                jobName = JobbNavn.KONTROLLER_UKVITTERTE_UTBETALINGER.visningsnavn,
                initialDelay = initialDelay,
                intervall = intervall,
                log = log,
                runJobCheck = listOf(runCheckFactory.leaderPod(), runCheckFactory.manTilFredag0600til2100()),
            ) {
                run(
                    utbetalingRepo = utbetalingRepo,
                    clock = clock,
                    maksVentetid = maksVentetid,
                    ordinærÅpningstidOppdrag = ordinærÅpningstidOppdrag,
                    log = log,
                )
            }.let(::KontrollerUkvitterteUtbetalingerJob)
        }

        internal fun run(
            utbetalingRepo: UtbetalingRepo,
            clock: Clock,
            maksVentetid: Duration,
            ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
            log: Logger,
        ): JobbResultat {
            val forsinkedeUtbetalinger = utbetalingRepo.hentUkvitterteUtbetalinger()
                .filter {
                    ventetidInnenforÅpningstid(
                        fraOgMed = it.opprettet.instant,
                        til = clock.instant(),
                        ordinærÅpningstidOppdrag = ordinærÅpningstidOppdrag,
                    ) >= maksVentetid
                }

            if (forsinkedeUtbetalinger.isEmpty()) return JobbResultat.Ok

            val eldsteUtbetaling = forsinkedeUtbetalinger.minBy { it.opprettet }
            val feilmelding =
                "Fant ${forsinkedeUtbetalinger.size} utbetaling(er) som har ventet minst ${maksVentetid.toHours()} timer på kvittering fra OS innenfor Oppdrags åpningstid. " +
                    "Eldste utbetaling ble opprettet ${eldsteUtbetaling.opprettet}."
            log.error(feilmelding)
            return JobbResultat.DelvisFeilet(feilmelding)
        }

        private fun ventetidInnenforÅpningstid(
            fraOgMed: Instant,
            til: Instant,
            ordinærÅpningstidOppdrag: Pair<LocalTime, LocalTime>,
        ): Duration {
            if (!fraOgMed.isBefore(til)) return Duration.ZERO

            var dato = fraOgMed.atZone(zoneIdOslo).toLocalDate()
            val sisteDato = til.atZone(zoneIdOslo).toLocalDate()
            var ventetid = Duration.ZERO

            while (!dato.isAfter(sisteDato)) {
                if (dato.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) {
                    val åpner = dato.atTime(ordinærÅpningstidOppdrag.first).atZone(zoneIdOslo).toInstant()
                    val stenger = dato.atTime(ordinærÅpningstidOppdrag.second).atZone(zoneIdOslo).toInstant()
                    val start = maxOf(fraOgMed, åpner)
                    val slutt = minOf(til, stenger)
                    if (start.isBefore(slutt)) {
                        ventetid = ventetid.plus(Duration.between(start, slutt))
                    }
                }
                dato = dato.plusDays(1)
            }
            return ventetid
        }
    }
}
