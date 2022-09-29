package no.nav.su.se.bakover.service.kontrollsamtale

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.jobcontext.JobContextRepo
import no.nav.su.se.bakover.domain.jobcontext.UtløptFristForKontrollsamtaleContext
import no.nav.su.se.bakover.domain.journalpost.JournalpostClient
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.service.revurdering.RevurderingService
import no.nav.su.se.bakover.service.revurdering.StansYtelseRequest
import no.nav.su.se.bakover.service.sak.SakService
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
    private val serviceUser: String = "srvsupstonad",
) : UtløptFristForKontrollsamtaleService {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun håndterUtløpsdato(dato: LocalDate): UtløptFristForKontrollsamtaleContext {
        val initialContext = hentEllerOpprettContext()
        val kontrollsamtaler = kontrollsamtaleRepo.hentInnkalteKontrollsamtalerMedFristUtløpt(dato)
        return initialContext.uprosesserte { kontrollsamtaler.map { it.id } }
            .ifEmpty {
                log.info("Fant ingen flere saker for mulig utsending av påminnelse om ny stønadsperiode.")
                log.info(initialContext.oppsummering())
                return initialContext
            }
            .also {
                log.info("Fant ${it.count()} kontrollsamtaler med utløp: $dato")
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
                    hentKontrollnotatMottatt = { saksnummer: Saksnummer, periode: Periode ->
                        journalpostClient.kontrollnotatMotatt(saksnummer, periode)
                            .mapLeft {
                                UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                            }
                    },
                    sessionFactory = sessionFactory,
                    opprettStans = { sakId: UUID, stansDato: LocalDate, transactionContext: TransactionContext ->
                        revurderingService.stansAvYtelse(
                            StansYtelseRequest.Opprett(
                                sakId = sakId,
                                saksbehandler = NavIdentBruker.Saksbehandler(serviceUser),
                                fraOgMed = stansDato,
                                revurderingsårsak = Revurderingsårsak(
                                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING,
                                    begrunnelse = Revurderingsårsak.Begrunnelse.create(value = "Automatisk stanset som følge av manglende oppmøte til kontrollsamtale"),
                                ),
                            ),
                            sessionContext = transactionContext,
                        ).mapLeft {
                            UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                        }
                    },
                    iverksettStans = { revurderingId: UUID, transactionContext: TransactionContext ->
                        revurderingService.iverksettStansAvYtelse(
                            revurderingId = revurderingId,
                            attestant = NavIdentBruker.Attestant(serviceUser),
                            sessionContext = transactionContext,
                        ).mapLeft {
                            UtløptFristForKontrollsamtaleContext.KunneIkkeHåndtereUtløptKontrollsamtale(it::class.java.toString())
                        }
                    },
                    lagreContext = { utløptFristForKontrollsamtaleContext: UtløptFristForKontrollsamtaleContext, transactionContext: TransactionContext ->
                        jobContextRepo.lagre(utløptFristForKontrollsamtaleContext, transactionContext)
                    },
                    clock = clock,
                    lagreKontrollsamtale = { kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext ->
                        kontrollsamtaleRepo.lagre(kontrollsamtale, transactionContext)
                    },
                )
            }
            .also {
                log.info(it.oppsummering())
            }
    }

    private fun hentEllerOpprettContext(): UtløptFristForKontrollsamtaleContext {
        return jobContextRepo.hent<UtløptFristForKontrollsamtaleContext>(
            UtløptFristForKontrollsamtaleContext.genererIdForTidspunkt(clock),
        )?.also {
            log.info("Gjenbruker eksisterende context for jobb: ${it.id().jobName}, dato: ${it.id().date}")
        } ?: UtløptFristForKontrollsamtaleContext(clock).also {
            log.info("Oppretter ny context for jobb: ${it.id().jobName}, dato: ${it.id().date}")
        }
    }
}
