package no.nav.su.se.bakover.service

import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.jobcontext.JobContextRepo
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.vilkår.FormuegrenserFactory
import org.slf4j.LoggerFactory
import java.time.Clock

interface SendPåminnelserOmNyStønadsperiodeService {
    fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext
}

internal class SendPåminnelserOmNyStønadsperiodeServiceImpl(
    private val clock: Clock,
    private val sakRepo: SakRepo,
    private val sessionFactory: SessionFactory,
    private val brevService: BrevService,
    private val personService: PersonService,
    private val jobContextRepo: JobContextRepo,
    private val formuegrenserFactory: FormuegrenserFactory,
) : SendPåminnelserOmNyStønadsperiodeService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext {
        val initialContext = hentEllerOpprettContext()
        return initialContext.uprosesserte { sakRepo.hentSakIdSaksnummerOgFnrForAlleSaker() }
            .ifEmpty {
                log.info("Fant ingen flere saker for mulig utsending av påminnelse om ny stønadsperiode.")
                log.info(initialContext.oppsummering())
                return initialContext
            }
            .also {
                log.info("Fant ${it.count()} saker for mulig utsendelse av påminnelse om ny stønadsperiode")
            }
            .fold(initialContext) { context, saksnummer ->
                try {
                    val sak = sakRepo.hentSak(saksnummer)!!

                    context.håndter(
                        sak = sak,
                        clock = clock,
                        hentPerson = { fnr ->
                            personService.hentPersonMedSystembruker(fnr)
                                .mapLeft { SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.FantIkkePerson }
                        },
                        sessionFactory = sessionFactory,
                        lagDokument = { request ->
                            brevService.lagDokument(request)
                                .mapLeft { SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.KunneIkkeLageBrev }
                        },
                        lagreDokument = { dokument, tx ->
                            brevService.lagreDokument(dokument, tx)
                        },
                        lagreContext = { ctx, tx ->
                            jobContextRepo.lagre(ctx, tx)
                        },
                        formuegrenserFactory = formuegrenserFactory,
                    ).fold(
                        {
                            log.error("Feil: ${it::class} ved utsending av påminnelse for sak: ${sak.saksnummer}, hopper over.")
                            context
                        },
                        { it },
                    )
                } catch (ex: Throwable) {
                    log.error("Ukjent feil: $ex oppstod ved utsending av påminnelse for sak: $saksnummer")
                    throw ex
                }
            }.also {
                log.info(it.oppsummering())
            }
    }

    private fun hentEllerOpprettContext(): SendPåminnelseNyStønadsperiodeContext {
        return jobContextRepo.hent<SendPåminnelseNyStønadsperiodeContext>(
            SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().name}, måned: ${it.id().yearMonth}")
        } ?: SendPåminnelseNyStønadsperiodeContext(clock).also {
            log.info("Oppretter ny context for jobb: ${it.id().name}, måned: ${it.id().yearMonth}")
        }
    }
}
