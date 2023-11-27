package no.nav.su.se.bakover.service

import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.sak.SakRepo
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo
import org.slf4j.LoggerFactory
import vilkår.formue.domain.FormuegrenserFactory
import java.time.Clock

interface SendPåminnelserOmNyStønadsperiodeService {
    fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext
}

class SendPåminnelserOmNyStønadsperiodeServiceImpl(
    private val clock: Clock,
    private val sakRepo: SakRepo,
    private val sessionFactory: SessionFactory,
    private val brevService: BrevService,
    private val sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo,
    private val formuegrenserFactory: FormuegrenserFactory,
) : SendPåminnelserOmNyStønadsperiodeService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext {
        val initialContext = hentEllerOpprettContext()
        return initialContext.uprosesserte { sakRepo.hentSakIdSaksnummerOgFnrForAlleSaker() }
            .ifEmpty {
                log.debug("Fant ingen flere saker for mulig utsending av påminnelse om ny stønadsperiode.")
                return initialContext
            }
            .also {
                log.debug("Fant {} saker for mulig utsendelse av påminnelse om ny stønadsperiode", it.count())
            }
            .fold(initialContext) { context, saksnummer ->
                try {
                    val sak = sakRepo.hentSak(saksnummer)!!

                    context.håndter(
                        sak = sak,
                        clock = clock,
                        sessionFactory = sessionFactory,
                        lagDokument = { command ->
                            brevService.lagDokument(command = command)
                                .mapLeft { SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.KunneIkkeLageBrev }
                        },
                        lagreDokument = { dokument, tx ->
                            brevService.lagreDokument(dokument, tx)
                        },
                        lagreContext = { ctx, tx ->
                            sendPåminnelseNyStønadsperiodeJobRepo.lagre(ctx, tx)
                        },
                        formuegrenserFactory = formuegrenserFactory,
                    ).fold(
                        {
                            log.error("Feil: ${it::class} ved utsending av påminnelse for sak: ${sak.saksnummer}, hopper over.")
                            context.feilet(
                                SendPåminnelseNyStønadsperiodeContext.Feilet(sak.saksnummer, it.toString()),
                                clock,
                            ).also {
                                sessionFactory.withTransactionContext { tx ->
                                    sendPåminnelseNyStønadsperiodeJobRepo.lagre(it, tx)
                                }
                            }
                        },
                        { it },
                    )
                } catch (ex: Throwable) {
                    log.error("Feil oppstod ved utsending av påminnelse for sak: $saksnummer.", ex)
                    context.feilet(
                        SendPåminnelseNyStønadsperiodeContext.Feilet(saksnummer, ex.toString()),
                        clock,
                    ).also {
                        sessionFactory.withTransactionContext { tx ->
                            sendPåminnelseNyStønadsperiodeJobRepo.lagre(it, tx)
                        }
                    }
                }
            }.also {
                log.info(it.oppsummering(clock))
            }
    }

    private fun hentEllerOpprettContext(): SendPåminnelseNyStønadsperiodeContext {
        return sendPåminnelseNyStønadsperiodeJobRepo.hent(
            SendPåminnelseNyStønadsperiodeContext.genererIdForTidspunkt(clock),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().name}, måned: ${it.id().yearMonth}")
        } ?: SendPåminnelseNyStønadsperiodeContext(clock).also {
            log.info("Oppretter ny context for jobb: ${it.id().name}, måned: ${it.id().yearMonth}")
        }
    }
}
