package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
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
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.service.skatt.SkattDokumentService
import no.nav.su.se.bakover.vedtak.application.FerdigstillVedtakService
import no.nav.su.se.bakover.vedtak.application.VedtakService
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
    private val oppgaveService: OppgaveService,
    private val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val brevService: BrevService,
    private val skattDokumentService: SkattDokumentService,
    private val satsFactory: SatsFactory,
) : IverksettSøknadsbehandlingService {

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    override fun iverksett(
        command: IverksettSøknadsbehandlingCommand,
    ): Either<KunneIkkeIverksetteSøknadsbehandling, Triple<Sak, IverksattSøknadsbehandling, Stønadsvedtak>> {
        return sakService.hentSakForSøknadsbehandling(command.behandlingId)
            .iverksettSøknadsbehandling(
                command = command,
                genererPdf = brevService::lagDokument,
                clock = clock,
                simulerUtbetaling = utbetalingService::simulerUtbetaling,
                satsFactory = satsFactory,
            )
            .map {
                iverksett(it)
                Triple(it.sak, it.søknadsbehandling, it.vedtak)
            }
    }

    override fun iverksett(
        iverksattSøknadsbehandlingResponse: IverksattSøknadsbehandlingResponse<*>,
    ) {
        iverksattSøknadsbehandlingResponse.ferdigstillIverksettelseITransaksjon(
            klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
            sessionFactory = sessionFactory,
            lagreSøknadsbehandling = søknadsbehandlingRepo::lagre,
            lagreVedtak = vedtakService::lagreITransaksjon,
            statistikkObservers = observers,
            opprettPlanlagtKontrollsamtale = opprettPlanlagtKontrollsamtaleService::opprett,
            lagreDokument = brevService::lagreDokument,
            lukkOppgave = ferdigstillVedtakService::lukkOppgaveMedBruker,
        ) { vedtak, tx -> skattDokumentService.genererOgLagre(vedtak, tx) }
    }
}
