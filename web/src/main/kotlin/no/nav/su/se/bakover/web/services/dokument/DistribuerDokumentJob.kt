package no.nav.su.se.bakover.web.services.dokument

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.nais.LeaderPodLookup
import no.nav.su.se.bakover.service.brev.BrevService
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.concurrent.fixedRateTimer

class DistribuerDokumentJob(
    private val brevService: BrevService,
    private val dokumentRepo: DokumentRepo,
    private val leaderPodLookup: LeaderPodLookup,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val jobName = "Journalfør og bestill brevdistribusjon"
    private val periode = Duration.of(1, ChronoUnit.MINUTES).toMillis()

    fun schedule() {
        log.info("Schedulerer jobb med intervall: $periode ms")
        fixedRateTimer(
            name = jobName,
            daemon = true,
            period = periode,
        ) {
            if (isLeaderPod()) {
                dokumentRepo.hentDokumenterForDistribusjon()
                    .map { dokumentdistribusjon ->
                        brevService.journalførDokument(dokumentdistribusjon)
                            .mapLeft {
                                Distribusjonsresultat.Feil.Journalføring(dokumentdistribusjon.id)
                            }
                            .flatMap { journalført ->
                                brevService.distribuerDokument(journalført)
                                    .mapLeft {
                                        Distribusjonsresultat.Feil.Brevdistribusjon(journalført.id)
                                    }
                                    .map { distribuert ->
                                        Distribusjonsresultat.Ok(distribuert.id)
                                    }
                            }
                    }.ifNotEmpty {
                        log.info("Fullført jobb - $jobName")
                        log.info("Ok: ${this.ok()}")
                        log.error("Feil: ${this.feil()}")
                    }
            }
        }
    }

    private sealed class Distribusjonsresultat {
        sealed class Feil {
            data class Journalføring(val id: UUID) : Feil()
            data class Brevdistribusjon(val id: UUID) : Feil()
        }

        data class Ok(val id: UUID) : Distribusjonsresultat()
    }

    private fun List<Either<Distribusjonsresultat.Feil, Distribusjonsresultat.Ok>>.ok() =
        this.filterIsInstance<Either.Right<Distribusjonsresultat.Ok>>()
            .map { it.value }

    private fun List<Either<Distribusjonsresultat.Feil, Distribusjonsresultat.Ok>>.feil() =
        this.filterIsInstance<Either.Left<Distribusjonsresultat.Feil>>()
            .map { it.value }

    private fun isLeaderPod() = leaderPodLookup.amITheLeader(InetAddress.getLocalHost().hostName).isRight()
}
