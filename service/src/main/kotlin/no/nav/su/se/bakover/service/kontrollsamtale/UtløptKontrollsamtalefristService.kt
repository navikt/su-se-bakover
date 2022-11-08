package no.nav.su.se.bakover.service.kontrollsamtale

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.jobcontext.JobContextRepo
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

interface UtløptFristForKontrollsamtaleService {
    fun håndterUtløpsdato(dato: LocalDate): UtløptFristForKontrollsamtaleContext
}

internal class UtløptFristForKontrollsamtaleServiceImpl(
    private val sakService: SakService,
    private val journalpostClient: JournalpostClient,
    private val kontrollsamtaleRepo: KontrollsamtaleRepo,
    private val revurderingService: RevurderingService,
    private val sessionFactory: SessionFactory,
    private val jobContextRepo: JobContextRepo,
    private val clock: Clock,
    private val serviceUser: String,
    private val oppgaveService: OppgaveService,
    private val personService: PersonService,
) : UtløptFristForKontrollsamtaleService {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun håndterUtløpsdato(dato: LocalDate): UtløptFristForKontrollsamtaleContext {
        val initialContext = hentEllerOpprettContext(dato)
        val kontrollsamtaler = kontrollsamtaleRepo.hentInnkalteKontrollsamtalerMedFristUtløpt(dato)
        return initialContext.uprosesserte { kontrollsamtaler.map { it.id } }
            .ifEmpty {
                log.info("Fant ingen uprosesserte kontrollsamtaler med utløp: $dato.")
                log.info(initialContext.oppsummering())
                return initialContext
            }
            .also {
                log.info("Fant ${it.count()} uprosesserte kontrollsamtaler med utløp: $dato")
            }
            .fold(initialContext) { context, prosesseres ->
                context.håndter(
                    kontrollsamtale = kontrollsamtaler.single { it.id == prosesseres },
                    hentSakInfo = { sakId ->
                        sakService.hentSakInfo(sakId)
                            .mapLeft {
                                UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                            }
                    },
                    hentKontrollnotatMottatt = { saksnummer: Saksnummer, periode: DatoIntervall ->
                        journalpostClient.kontrollnotatMotatt(saksnummer, periode)
                            .mapLeft {
                                UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                            }
                    },
                    sessionFactory = sessionFactory,
                    opprettStans = { sakId: UUID, stansDato: LocalDate, transactionContext: TransactionContext ->
                        revurderingService.stansAvYtelseITransaksjon(
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
                        revurderingService.iverksettStansAvYtelseITransaksjon(
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
                        jobContextRepo.lagre(utløptFristForKontrollsamtaleContext, transactionContext)
                    },
                    clock = clock,
                    lagreKontrollsamtale = { kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext ->
                        kontrollsamtaleRepo.lagre(kontrollsamtale, transactionContext)
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
                log.info(it.oppsummering())
            }
    }

    private fun hentEllerOpprettContext(dato: LocalDate): UtløptFristForKontrollsamtaleContext {
        return jobContextRepo.hent<UtløptFristForKontrollsamtaleContext>(
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
