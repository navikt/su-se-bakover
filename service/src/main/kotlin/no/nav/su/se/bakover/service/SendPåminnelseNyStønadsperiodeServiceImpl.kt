package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.JobContextRepo
import no.nav.su.se.bakover.domain.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.person.PersonService
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate

interface SendPåminnelseNyStønadsperiodeService {
    fun sendPåminnelser(): Either<KunneIkkeSendePåminnelse, SendPåminnelseNyStønadsperiodeContext>
}

sealed interface KunneIkkeSendePåminnelse {
    object FantIkkePerson : KunneIkkeSendePåminnelse
    object KunneIkkeLageBrev : KunneIkkeSendePåminnelse
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

    override fun sendPåminnelser(): Either<KunneIkkeSendePåminnelse, SendPåminnelseNyStønadsperiodeContext> {
        val initialContext = jobContextRepo.hent<SendPåminnelseNyStønadsperiodeContext>(
            SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().jobName}, måned: ${it.id().yearMonth}")
        } ?: SendPåminnelseNyStønadsperiodeContext(clock).also {
            log.info("Oppretter ny context for jobb: ${it.id().jobName}, måned: ${it.id().yearMonth}")
        }

        val endContext = sakRepo.hentSakIdSaksnummerOgFnrForAlleSaker()
            .map { it.saksnummer }
            .toSet()
            .minus(initialContext.prosessert())
            .ifEmpty {
                log.info(
                    "Påminnelser om ny stønadsperiode er sendt ut for alle relevante saker (${
                    initialContext.sendt().count()
                    }) for måned: ${initialContext.id().yearMonth}",
                )
                return initialContext.right()
            }
            .also { log.info("Kontrollerer behov for påminnelse om ny stønadsperiode for ${it.count()} saker") }
            .fold(initialContext) { acc, saksnummer ->
                val sak = sakRepo.hentSak(saksnummer)!!
                val person = personService.hentPerson(sak.fnr)
                    .getOrHandle { return KunneIkkeSendePåminnelse.FantIkkePerson.left() }

                val utløperInneværendeMåned = acc.id().yearMonth
                    .let {
                        Periode.create(
                            LocalDate.of(it.year, it.month, 1),
                            it.atEndOfMonth(),
                        )
                    }
                    .let { måned ->
                        sak.hentPerioderMedLøpendeYtelse().lastOrNull()?.slutterSamtidig(måned) ?: false
                    }

                if (utløperInneværendeMåned) {
                    val dokument = brevService.lagDokument(
                        LagBrevRequest.PåminnelseNyStønadsperiode(
                            person = person,
                            dagensDato = LocalDate.now(clock),
                            saksnummer = sak.saksnummer,
                        ),
                    ).getOrHandle { return KunneIkkeSendePåminnelse.KunneIkkeLageBrev.left() }

                    sessionFactory.withTransactionContext { tx ->
                        brevService.lagreDokument(
                            dokument = dokument.leggTilMetadata(
                                metadata = Dokument.Metadata(
                                    sakId = sak.id,
                                    bestillBrev = true,
                                ),
                            ),
                        )
                        acc.sendt(saksnummer).also {
                            jobContextRepo.lagre(
                                jobContext = it,
                                context = tx,
                            )
                        }
                    }
                } else {
                    sessionFactory.withTransactionContext { tx ->
                        acc.prosessert(saksnummer).also {
                            jobContextRepo.lagre(
                                jobContext = it,
                                context = tx,
                            )
                        }
                    }
                }
            }

        log.info(endContext.oppsummering())
        return endContext.right()
    }
}
