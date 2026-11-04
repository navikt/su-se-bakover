package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.domain.tid.idagOslo
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.fritekst.FritekstService
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import no.nav.su.se.bakover.domain.notat.ReferanseType
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksattSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.OpprettKontrollsamtaleVedNyStønadsperiodeService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.hentSøknadsbehandlingEllerKast
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.service.brev.lagreVedtaksbrevMedKopi
import no.nav.su.se.bakover.service.notat.VedtaksnotatJournalføringService
import no.nav.su.se.bakover.service.skatt.SkattDokumentService
import no.nav.su.se.bakover.service.statistikk.SakStatistikkService
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.slf4j.LoggerFactory
import person.domain.Person
import person.domain.PersonService
import satser.domain.SatsFactory
import vedtak.domain.Stønadsvedtak
import økonomi.application.utbetaling.UtbetalingService
import java.time.Clock

class IverksettSøknadsbehandlingServiceImpl(
    private val sakService: SakService,
    private val clock: Clock,
    private val utbetalingService: UtbetalingService,
    private val sessionFactory: SessionFactory,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val vedtakService: VedtakService,
    private val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val brevService: BrevService,
    private val skattDokumentService: SkattDokumentService,
    private val satsFactory: SatsFactory,
    private val fritekstService: FritekstService,
    private val sakStatistikkService: SakStatistikkService,
    private val mottakerService: MottakerService,
    private val personService: PersonService,
    private val oppgaveService: OppgaveService,
    private val vedtaksnotatJournalføringService: VedtaksnotatJournalføringService,
) : IverksettSøknadsbehandlingService {

    private val log = LoggerFactory.getLogger(this::class.java)
    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    override fun iverksett(
        command: IverksettSøknadsbehandlingCommand,
    ): Either<KunneIkkeIverksetteSøknadsbehandling, Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak>> {
        val fritekst = fritekstService.hentFritekst(
            referanseId = command.behandlingId.value,
            type = FritekstType.VEDTAKSBREV_SØKNADSBEHANDLING,
        ).map { it.fritekst }.getOrElse { "" }

        val sak = sakService.hentSakForSøknadsbehandling(command.behandlingId)

        val søknadsbehandling = sak.hentSøknadsbehandlingEllerKast(command).getOrElse { return it.left() }
        if (søknadsbehandling.skalSendeVedtaksbrev()) {
            val manglerAdresse = personService.hentPerson(sak.fnr, sak.type).getOrElse {
                return KunneIkkeIverksetteSøknadsbehandling.FantIkkeAdresseTilBruker("Fant ikke bruker i PDL").left()
            }.adresse.isNullOrEmpty()
            if (manglerAdresse) {
                return KunneIkkeIverksetteSøknadsbehandling.FantIkkeAdresseTilBruker("Fant ikke adresse til bruker i PDL")
                    .left()
            }
        }

        return sak.iverksettSøknadsbehandling(
            command = command,
            søknadsbehandling = søknadsbehandling,
            genererPdf = brevService::lagDokumentPdf,
            clock = clock,
            simulerUtbetaling = utbetalingService::simulerUtbetaling,
            satsFactory = satsFactory,
            fritekst = fritekst,
        )
            .map {
                iverksett(it)
                Triple(it.sak, it.søknadsbehandling, it.vedtak)
            }
    }

    override fun iverksett(
        iverksattSøknadsbehandlingResponse: IverksattSøknadsbehandlingResponse<*>,
    ) {
        val lagreVedtaksbrevMedKopi = lagreVedtaksbrevMedKopi(
            brevService = brevService,
            mottakerService = mottakerService,
            referanseType = ReferanseTypeMottaker.SØKNAD,
            referanseId = iverksattSøknadsbehandlingResponse.vedtak.behandling.id.value,
            sakId = iverksattSøknadsbehandlingResponse.vedtak.behandling.sakId,
        )

        iverksattSøknadsbehandlingResponse.ferdigstillIverksettelseITransaksjon(
            klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
            sessionFactory = sessionFactory,
            lagreSøknadsbehandling = søknadsbehandlingRepo::lagre,
            lagreVedtak = vedtakService::lagreITransaksjon,
            statistikkObservers = observers,
            opprettPlanlagtKontrollsamtale = opprettPlanlagtKontrollsamtaleService::opprett,
            lagreDokumentMedKopi = lagreVedtaksbrevMedKopi,
            lukkOppgave = ferdigstillVedtakService::lukkOppgaveMedBruker, // TODO: Flytt denne ut.
            lagreSakstatistikk = { statistikk, tx ->
                sakStatistikkService.lagre(statistikk, tx)
            },
        ) { vedtak, tx -> skattDokumentService.genererOgLagre(vedtak, tx) }

        // TODO: Opprett GOSYS-oppgave her dersom EPS fyller 67 år i løpet av stønadsperioden.

        val sak = iverksattSøknadsbehandlingResponse.sak
        val søknad = iverksattSøknadsbehandlingResponse.vedtak.behandling.søknad
        // var bursdagEPS: LocalDate? = null

        if (iverksattSøknadsbehandlingResponse.søknadsbehandling is IverksattSøknadsbehandling.Innvilget) {
            val fnrEPS = søknad.søknadInnhold.boforhold.ektefellePartnerSamboer?.fnr
            if (fnrEPS != null) {
                personService.hentPerson(fnrEPS, sak.type)
                    .onLeft {
                        log.error("Kunne ikke hente EPS for å sjekke om EPS fyller 67 år i løpet av stønadsperioden. SakId=${sak.id}.")
                    }
                    .onRight { personEPS ->
                        when (val fødsel = personEPS.fødsel) {
                            is Person.Fødsel.MedFødselsdato -> {
                                val periode = iverksattSøknadsbehandlingResponse.søknadsbehandling.periode

                                if (false) {
                                    // if (personEPS.fyller67ILøpetAvPeriode(periode) == true) {
                                    val fødselsdatoEPS = fødsel.dato
                                    val bursdagEPS = fødselsdatoEPS.plusYears(67)

                                    if (bursdagEPS.isAfter(idagOslo(clock))) {
                                        oppgaveService.opprettOppgave(
                                            config = OppgaveConfig.PåmminelseOmEPSFyller67År(
                                                saksnummer = sak.saksnummer,
                                                bursdagEps = bursdagEPS,
                                                fnr = sak.fnr,
                                                clock = clock,
                                                sakstype = sak.type,
                                                journalpostId = iverksattSøknadsbehandlingResponse.vedtak.behandling.søknad.journalpostId,
                                            ),
                                        ).onLeft { feil ->
                                            log.error("Kunne ikke opprette Gosys-oppgave ettersom EPS fyller 67 år i løpet av stønadsperioden. SakId: ${sak.id}. Originalfeil: $feil")
                                        }
                                    }
                                }
                            }
                            is Person.Fødsel.MedFødselsår, null -> {
                                log.error("Kunne ikke hente eksakt bursdagsdato for EPS for å sjekke om EPS fyller 67 år i løpet av stønadsperioden. SakId=${sak.id}.")
                            }
                        }
                    }
            }
        }

        vedtaksnotatJournalføringService.journalførHvisFinnes(
            sakId = iverksattSøknadsbehandlingResponse.vedtak.behandling.sakId,
            referanseId = iverksattSøknadsbehandlingResponse.vedtak.behandling.id.value,
            referanseType = ReferanseType.SØKNADSBEHANDLING,
        )
    }
}
