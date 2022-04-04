package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.JobContextRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
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
        val initialContext = hentEllerOpprettNyContext()

        val endContext = finnSakerForProsessering(initialContext)
            .ifEmpty {
                log.info("Fant ingen flere saker for mulig utsending av påminnelse om ny stønadsperiode.")
                log.info(initialContext.oppsummering())
                return initialContext.right()
            }
            .also {
                log.info("Fant ${it.count()} saker for mulig utsendelse av påminnelse om ny stønadsperiode")
            }
            .fold(initialContext) { accContext, saksnummer ->
                val sak = sakRepo.hentSak(saksnummer)!!

                val utløperInneværendeMåned = sak.hentPerioderMedLøpendeYtelse()
                    .lastOrNull()?.slutterSamtidig(accContext.id().tilPeriode()) ?: false

                if (utløperInneværendeMåned) {
                    sendPåminnelse(sak, accContext)
                        .fold(
                            {
                                log.error("Feil: ${it::class} ved utsending av påminnelse for ${sak.saksnummer}, hopper over.")
                                accContext
                            },
                            { it },
                        )
                } else {
                    ikkeSendPåminnelse(sak, accContext)
                }
            }

        log.info(endContext.oppsummering())
        return endContext.right()
    }

    private fun sendPåminnelse(
        sak: Sak,
        context: SendPåminnelseNyStønadsperiodeContext,
    ): Either<KunneIkkeSendePåminnelse, SendPåminnelseNyStønadsperiodeContext> {
        return personService.hentPerson(sak.fnr)
            .mapLeft { KunneIkkeSendePåminnelse.FantIkkePerson }
            .flatMap { person ->
                brevService.lagDokument(
                    LagBrevRequest.PåminnelseNyStønadsperiode(
                        person = person,
                        dagensDato = LocalDate.now(clock),
                        saksnummer = sak.saksnummer,
                    ),
                ).mapLeft { KunneIkkeSendePåminnelse.KunneIkkeLageBrev }
                    .map { dokument ->
                        sessionFactory.withTransactionContext { tx ->
                            brevService.lagreDokument(
                                dokument = dokument.leggTilMetadata(
                                    metadata = Dokument.Metadata(
                                        sakId = sak.id,
                                        bestillBrev = true,
                                    ),
                                ),
                                transactionContext = tx,
                            )
                            context.sendt(sak.saksnummer).also {
                                jobContextRepo.lagre(
                                    jobContext = it,
                                    transactionContext = tx,
                                )
                            }
                        }
                    }
            }
    }

    private fun ikkeSendPåminnelse(
        sak: Sak,
        context: SendPåminnelseNyStønadsperiodeContext,
    ): SendPåminnelseNyStønadsperiodeContext {
        return sessionFactory.withTransactionContext { tx ->
            context.prosessert(sak.saksnummer).also {
                jobContextRepo.lagre(
                    jobContext = it,
                    transactionContext = tx,
                )
            }
        }
    }

    private fun finnSakerForProsessering(initialContext: SendPåminnelseNyStønadsperiodeContext): Set<Saksnummer> {
        return sakRepo.hentSakIdSaksnummerOgFnrForAlleSaker()
            .map { it.saksnummer }
            .toSet()
            .minus(initialContext.prosessert())
    }

    private fun hentEllerOpprettNyContext(): SendPåminnelseNyStønadsperiodeContext {
        return jobContextRepo.hent<SendPåminnelseNyStønadsperiodeContext>(
            SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().jobName}, måned: ${it.id().yearMonth}")
        } ?: SendPåminnelseNyStønadsperiodeContext(clock).also {
            log.info("Oppretter ny context for jobb: ${it.id().jobName}, måned: ${it.id().yearMonth}")
        }
    }
}
