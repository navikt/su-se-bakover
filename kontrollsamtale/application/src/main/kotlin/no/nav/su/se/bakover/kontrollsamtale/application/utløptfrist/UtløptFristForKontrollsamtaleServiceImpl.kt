package no.nav.su.se.bakover.kontrollsamtale.application.utløptfrist

import arrow.core.getOrElse
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseService
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleJobRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.kontrollsamtale.domain.UtløptFristForKontrollsamtaleService
import org.slf4j.LoggerFactory
import person.domain.PersonService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class UtløptFristForKontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val journalpostClient: JournalpostClient,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val stansAvYtelseService: StansYtelseService,
    private val sessionFactory: SessionFactory,
    private val clock: Clock,
    private val serviceUser: String,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
    private val kontrollsamtaleJobRepo: KontrollsamtaleJobRepo,
) : UtløptFristForKontrollsamtaleService {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun håndterUtløpsdato(dato: LocalDate): UtløptFristForKontrollsamtaleContext {
        val initialContext = hentEllerOpprettContext(dato)
        val kontrollsamtaler = kontrollsamtaleService.hentInnkalteKontrollsamtalerMedFristUtløpt(dato)
        return initialContext.uprosesserte { kontrollsamtaler.map { it.id } }
            .ifEmpty {
                log.debug("Fant ingen uprosesserte kontrollsamtaler med utløp: {}.", dato)
                return initialContext
            }
            .also {
                log.debug("Fant {} uprosesserte kontrollsamtaler med utløp: {}", it.count(), dato)
            }
            .fold(initialContext) { context, prosesseres ->
                val kontrollsamtale = kontrollsamtaler.single { it.id == prosesseres }
                context.håndter(
                    kontrollsamtale = kontrollsamtale,
                    sak = sakService.hentSak(kontrollsamtale.sakId).getOrElse {
                        throw IllegalStateException("fant ikke sak for kontrollsamtale ${kontrollsamtale.id}, sakId ${kontrollsamtale.sakId}")
                    },
                    hentKontrollnotatMottatt = { saksnummer: Saksnummer, periode: DatoIntervall ->
                        journalpostClient.kontrollnotatMotatt(saksnummer, periode)
                            .mapLeft {
                                UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                            }
                    },
                    sessionFactory = sessionFactory,
                    opprettStans = { sakId: UUID, stansDato: LocalDate, transactionContext: TransactionContext ->
                        stansAvYtelseService.stansAvYtelseITransaksjon(
                            StansYtelseRequest.Opprett(
                                sakId = sakId,
                                saksbehandler = NavIdentBruker.Saksbehandler(serviceUser),
                                fraOgMed = stansDato,
                                revurderingsårsak = Revurderingsårsak(
                                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING,
                                    begrunnelse = Revurderingsårsak.Begrunnelse.create(value = "Automatisk stanset som følge av manglende oppmøte til kontrollsamtale"),
                                ),
                            ),
                            transactionContext = transactionContext,
                        ).let {
                            UtløptFristForKontrollsamtaleContext.OpprettStansTransactionCallback(
                                revurderingId = it.revurdering.id,
                                sendStatistikkCallback = it.sendStatistikkCallback,
                            )
                        }
                    },
                    iverksettStans = { revurderingId: UUID, transactionContext: TransactionContext ->
                        stansAvYtelseService.iverksettStansAvYtelseITransaksjon(
                            revurderingId = revurderingId,
                            attestant = NavIdentBruker.Attestant(serviceUser),
                            transactionContext = transactionContext,
                        ).let {
                            UtløptFristForKontrollsamtaleContext.IverksettStansTransactionCallback(
                                sendUtbetalingCallback = it.sendUtbetalingCallback,
                                sendStatistikkCallback = it.sendStatistikkCallback,
                            )
                        }
                    },
                    lagreContext = { utløptFristForKontrollsamtaleContext: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext ->
                        kontrollsamtaleJobRepo.lagre(utløptFristForKontrollsamtaleContext, transactionContext)
                    },
                    clock = clock,
                    lagreKontrollsamtale = { ks: Kontrollsamtale, transactionContext: TransactionContext ->
                        kontrollsamtaleService.lagre(ks, transactionContext)
                    },
                    hentAktørId = { fnr ->
                        personService.hentAktørIdMedSystembruker(fnr)
                            .mapLeft {
                                UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                            }
                    },
                    opprettOppgave = { oppgaveConfig: OppgaveConfig ->
                        oppgaveService.opprettOppgaveMedSystembruker(oppgaveConfig)
                            .mapLeft {
                                UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                            }
                    },
                )
            }
            .also {
                log.info(it.oppsummering(clock))
            }
    }

    private fun hentEllerOpprettContext(dato: LocalDate): UtløptFristForKontrollsamtaleContext {
        return kontrollsamtaleJobRepo.hent(
            UtløptFristForKontrollsamtaleContext.genererId(dato),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().name}, dato: ${it.id().date}")
        } ?: UtløptFristForKontrollsamtaleContext(
            id = UtløptFristForKontrollsamtaleContext.genererId(dato),
            clock = clock,
        ).also {
            log.info("Oppretter ny context for jobb: ${it.id().name}, dato: ${it.id().date}")
        }
    }
}
