package no.nav.su.se.bakover.service

import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.JobContext
import no.nav.su.se.bakover.domain.JobContextRepo
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.person.PersonService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate

interface SendPåminnelseNyStønadsperiodeService {
    fun sendPåminnelser()
}

internal class SendPåminnelseNyStønadsperiodeServiceImpl(
    private val clock: Clock,
    private val sakRepo: SakRepo,
    private val sessionFactory: SessionFactory,
    private val brevService: BrevService,
    private val personService: PersonService,
    private val jobContextRepo: JobContextRepo,
) : SendPåminnelseNyStønadsperiodeService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun sendPåminnelser() {
        val start = System.currentTimeMillis()

        val context = jobContextRepo.hent<JobContext.SendPåminnelseNyStønadsperiodeContext>(
            JobContext.SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().jobName}, måned: ${it.id().yearMonth}")
        } ?: JobContext.SendPåminnelseNyStønadsperiodeContext(clock).also {
            log.info("Oppretter ny context for jobb: ${it.id().jobName}, måned: ${it.id().yearMonth}")
        }

        sakRepo.hentSakIdSaksnummerOgFnrForAlleSaker()
            .map { it.saksnummer }
            .toSet()
            .minus(context.prosessert())
            .ifEmpty {
                log.info(
                    "Påminnelser om ny stønadsperiode er sendt ut for alle relevante saker (${
                        context.sendt().count()
                    }) for måned: ${context.id().yearMonth}",
                )
                return
            }
            .also { log.info("Kontrollerer behov for påminnelse om ny stønadsperiode for ${it.count()} saker") }
            .fold(context) { ctx, saksnummerSomProsesseres ->
                val sak = sakRepo.hentSak(saksnummerSomProsesseres)!!
                val person = personService.hentPerson(sak.fnr)
                    .getOrHandle { TODO() }

                if (true) {
                    val dokument = brevService.lagDokument(
                        LagBrevRequest.PåminnelseNyStønadsperiode(
                            person = person,
                            dagensDato = LocalDate.now(clock),
                            saksnummer = sak.saksnummer,
                        ),
                    ).getOrHandle { TODO() }

                    sessionFactory.withTransactionContext { tx ->
                        brevService.lagreDokument(
                            dokument = dokument.leggTilMetadata(
                                metadata = Dokument.Metadata(
                                    sakId = sak.id,
                                    bestillBrev = true,
                                ),
                            ),
                        )
                        ctx.sendt(saksnummerSomProsesseres).also {
                            jobContextRepo.lagre(
                                jobContext = it,
                                context = tx,
                            )
                        }
                    }
                } else {
                    ctx.prosessert(saksnummerSomProsesseres).also {
                        sessionFactory.withTransactionContext { tx ->
                            jobContextRepo.lagre(
                                jobContext = it,
                                context = tx,
                            )
                        }
                    }
                }
            }

        val end = System.currentTimeMillis()
        log.info("Fullført utsendelse av påminnelser om ny stønadsperode på: ${(end - start)}ms")
    }
}
