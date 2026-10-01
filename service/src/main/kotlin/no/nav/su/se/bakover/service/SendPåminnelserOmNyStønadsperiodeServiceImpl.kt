package no.nav.su.se.bakover.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.command.PåminnelseNyStønadsperiodeDokumentCommand
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext.Påminnelsesvurdering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.stønadsperiode.SendPåminnelseNyStønadsperiodeJobRepo
import org.slf4j.LoggerFactory
import person.domain.Person
import person.domain.PersonService
import java.time.Clock

interface SendPåminnelserOmNyStønadsperiodeService {
    fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext
}

class SendPåminnelserOmNyStønadsperiodeServiceImpl(
    private val clock: Clock,
    private val sakService: SakService,
    private val sessionFactory: SessionFactory,
    private val brevService: BrevService,
    private val sendPåminnelseNyStønadsperiodeJobRepo: SendPåminnelseNyStønadsperiodeJobRepo,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
) : SendPåminnelserOmNyStønadsperiodeService {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun sendPåminnelser(): SendPåminnelseNyStønadsperiodeContext {
        val initialContext = hentEllerOpprettContext()
        return initialContext.uprosesserte(sakService.hentSakIdSaksnummerOgFnrForAlleSakerNyesteFørst())
            .ifEmpty {
                log.debug("Fant ingen flere saker for mulig utsending av påminnelse om ny stønadsperiode.")
                return initialContext
            }
            .also {
                log.debug("Fant {} saker for mulig utsendelse av påminnelse om ny stønadsperiode", it.count())
            }
            .fold(initialContext) { context, saksnummer ->
                try {
                    val sak = sakService.hentSak(saksnummer).getOrNull()!!

                    håndter(sak, context).fold(
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

    private fun håndter(
        sak: Sak,
        context: SendPåminnelseNyStønadsperiodeContext,
    ): Either<SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse, SendPåminnelseNyStønadsperiodeContext> {
        val person = personService.hentPersonMedSystembruker(sak.fnr, sak.type).getOrElse {
            return SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.FantIkkePerson.left()
        }

        return when (val vurdering = context.skalSendePåminnelse(sak, person)) {
            is Påminnelsesvurdering.SkalSendes -> sendPåminnelse(sak, person, context)
            is Påminnelsesvurdering.SkalIkkeSendes -> markerProsessert(sak, context)
            is Påminnelsesvurdering.BrukerErDødMedYtelseEtterDødsmåned -> {
                oppgaveService.opprettOppgaveMedSystembruker(
                    OppgaveConfig.BrukerErDød(
                        saksnummer = sak.saksnummer,
                        dødsdato = vurdering.dødsdato,
                        årsak = "Oppdaget av jobben for påminnelse om ny stønadsperiode. Saken har ytelse etter dødsmåneden. Påminnelse er ikke sendt.",
                        fnr = sak.fnr,
                        clock = clock,
                        sakstype = sak.type,
                    ),
                ).onLeft {
                    log.error("Kunne ikke opprette oppgave for død bruker for saksnummer ${sak.saksnummer}. Må følges opp manuelt. Feil: $it")
                }
                markerProsessert(sak, context)
            }
        }
    }

    private fun sendPåminnelse(
        sak: Sak,
        person: Person,
        context: SendPåminnelseNyStønadsperiodeContext,
    ): Either<SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse, SendPåminnelseNyStønadsperiodeContext> {
        val sisteVedtak = sak.vedtakstidslinje()?.lastOrNull()
            ?: return SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.FantIkkeVedtak.left()

        val dokumentCommand = PåminnelseNyStønadsperiodeDokumentCommand.ny(
            sak,
            person,
            sisteVedtak.periode.tilOgMed,
        ).getOrElse {
            return it.left()
        }

        return brevService.lagDokumentPdf(command = dokumentCommand)
            .mapLeft { SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse.KunneIkkeLageBrev }
            .map { dokument ->
                sessionFactory.withTransactionContext { tx ->
                    brevService.lagreDokument(
                        dokument.leggTilMetadata(
                            metadata = Dokument.Metadata(sakId = sak.id),
                            // vi kan ikke sende påminnelse til en annen adresse per nå
                            distribueringsadresse = null,
                        ),
                        tx,
                    )
                    context.sendt(sak.saksnummer, clock).also {
                        sendPåminnelseNyStønadsperiodeJobRepo.lagre(it, tx)
                    }
                }
            }
    }

    private fun markerProsessert(
        sak: Sak,
        context: SendPåminnelseNyStønadsperiodeContext,
    ): Either<SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse, SendPåminnelseNyStønadsperiodeContext> {
        return sessionFactory.withTransactionContext { tx ->
            context.prosessert(sak.saksnummer, clock).also {
                sendPåminnelseNyStønadsperiodeJobRepo.lagre(it, tx)
            }
        }.right()
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
