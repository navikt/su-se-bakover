package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.iverksettSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Stønadsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import java.time.Clock

class IverksettSøknadsbehandlingServiceImpl(
    private val sakService: SakService,
    private val clock: Clock,
    private val utbetalingService: UtbetalingService,
    private val sessionFactory: SessionFactory,
    private val søknadsbehandlingRepo: SøknadsbehandlingRepo,
    private val vedtakRepo: VedtakRepo,
    private val kontrollsamtaleService: KontrollsamtaleService,
    private val ferdigstillVedtakService: FerdigstillVedtakService,
    private val brevService: BrevService,
) : IverksettSøknadsbehandlingService {

    private val observers: MutableList<StatistikkEventObserver> = mutableListOf()

    fun addObserver(observer: StatistikkEventObserver) {
        observers.add(observer)
    }

    override fun iverksett(
        command: IverksettSøknadsbehandlingCommand,
    ): Either<KunneIkkeIverksetteSøknadsbehandling, Triple<Sak, Søknadsbehandling.Iverksatt, Stønadsvedtak>> {
        return sakService.hentSakForSøknadsbehandling(command.behandlingId)
            .iverksettSøknadsbehandling(
                command = command,
                lagDokument = brevService::lagDokument,
                clock = clock,
                simulerUtbetaling = utbetalingService::simulerUtbetaling,
            )
            .map {
                it.ferdigstillIverksettelseITransaksjon(
                    sessionFactory = sessionFactory,
                    lagreSøknadsbehandling = søknadsbehandlingRepo::lagre,
                    lagreVedtak = vedtakRepo::lagreITransaksjon,
                    statistikkObservers = observers,
                    lagreDokument = brevService::lagreDokument,
                    lukkOppgave = ferdigstillVedtakService::lukkOppgaveMedBruker,
                    klargjørUtbetaling = utbetalingService::klargjørUtbetaling,
                    opprettPlanlagtKontrollsamtale = kontrollsamtaleService::opprettPlanlagtKontrollsamtale,
                )
                Triple(it.sak, it.søknadsbehandling, it.vedtak)
            }
    }
}
