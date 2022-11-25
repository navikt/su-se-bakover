package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.avslåSøknadPgaManglendeDokumentasjon
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.Clock

internal class AvslåSøknadManglendeDokumentasjonServiceImpl(
    private val clock: Clock,
    private val sakService: SakService,
    private val satsFactory: SatsFactory,
    private val iverksettSøknadsbehandlingService: IverksettSøknadsbehandlingService,
    private val utbetalingService: UtbetalingService,
    private val brevService: BrevService,
) : AvslåSøknadManglendeDokumentasjonService {

    override fun avslå(
        command: AvslåManglendeDokumentasjonCommand,
    ): Either<KunneIkkeAvslåSøknad, Sak> {
        return sakService.hentSakForSøknad(command.søknadId)
            .getOrHandle { throw IllegalArgumentException("Fant ikke søknad ${command.søknadId}. Kan ikke avslå søknad pgr. manglende dokumentasjon.") }
            .avslåSøknadPgaManglendeDokumentasjon(
                command = command,
                clock = clock,
                satsFactory = satsFactory,
                lagDokument = brevService::lagDokument,
                simulerUtbetaling = utbetalingService::simulerUtbetaling,
            ).map {
                iverksettSøknadsbehandlingService.iverksett(it)
                it.sak
            }
    }
}
