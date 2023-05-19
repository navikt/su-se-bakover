package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.satser.SatsFactory
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.IverksattAvslåttSøknadsbehandlingResponse
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.AvslåManglendeDokumentasjonCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.KunneIkkeAvslåSøknad
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon.avslåSøknadPgaManglendeDokumentasjon
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import java.time.Clock

class AvslåSøknadManglendeDokumentasjonServiceImpl(
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
        return lagAvslg(command).map {
            iverksettSøknadsbehandlingService.iverksett(it)
            it.sak
        }
    }

    override fun genererBrevForhåndsvisning(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, Pair<Fnr, ByteArray>> {
        return lagAvslg(command).map { it.sak.fnr to it.dokument.generertDokument }
    }

    private fun lagAvslg(command: AvslåManglendeDokumentasjonCommand): Either<KunneIkkeAvslåSøknad, IverksattAvslåttSøknadsbehandlingResponse> {
        return sakService.hentSakForSøknad(command.søknadId)
            .getOrElse { throw IllegalArgumentException("Fant ikke søknad ${command.søknadId}. Kan ikke avslå søknad pga. manglende dokumentasjon.") }
            .avslåSøknadPgaManglendeDokumentasjon(
                command = command,
                clock = clock,
                satsFactory = satsFactory,
                lagDokument = brevService::lagDokument,
                simulerUtbetaling = utbetalingService::simulerUtbetaling,
            )
    }
}
