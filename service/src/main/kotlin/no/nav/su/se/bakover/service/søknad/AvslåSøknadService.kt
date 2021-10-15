package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.util.UUID

class AvslåSøknadService(
    private val clock: Clock = Clock.systemUTC(),
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val vedtakService: VedtakService,
) {
    fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Vedtak.Avslag> {
        return søknadsbehandlingService.hentForSøknad(request.søknadId)
            ?.let { harBehandlingFraFør(request, it) }
            ?: opprettNyBehandlingFørst(request)
    }

    private fun harBehandlingFraFør(
        request: AvslåManglendeDokumentasjonRequest,
        søknadsbehandling: Søknadsbehandling,
    ): Either<KunneIkkeAvslåSøknad, Vedtak.Avslag> {
        return avslå(request, søknadsbehandling)
    }

    private fun opprettNyBehandlingFørst(
        request: AvslåManglendeDokumentasjonRequest,
    ): Either<KunneIkkeAvslåSøknad, Vedtak.Avslag> {
        val søknadsbehandling = søknadsbehandlingService.opprett(
            request = SøknadsbehandlingService.OpprettRequest(
                søknadId = request.søknadId,
            ),
        ).getOrHandle { return KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.left() }

        return avslå(request, søknadsbehandling)
    }

    private fun avslå(
        request: AvslåManglendeDokumentasjonRequest,
        søknadsbehandling: Søknadsbehandling,
    ): Either<KunneIkkeAvslåSøknad, Vedtak.Avslag> {

        val avslag = AvslagManglendeDokumentasjon.tryCreate(
            søknadsbehandling = søknadsbehandling,
            saksbehandler = request.saksbehandler,
            fritekstTilBrev = request.fritekstTilBrev,
            clock = clock,
        ).getOrHandle { return KunneIkkeAvslåSøknad.SøknadsbehandlingIUgyldigTilstandForAvslag.left() }

        val vedtak = Vedtak.Avslag.fromAvslagManglendeDokumentasjon(
            avslag = avslag,
            clock = clock,
        )
        // TODO transaksjon
        søknadsbehandlingService.lagre(avslag)
        vedtakService.lagre(vedtak)
        // TODO brev + fritekst
        // TODO oppgave

        return vedtak.right()
    }
}

sealed class KunneIkkeAvslåSøknad {
    object KunneIkkeOppretteSøknadsbehandling : KunneIkkeAvslåSøknad()
    object SøknadsbehandlingIUgyldigTilstandForAvslag : KunneIkkeAvslåSøknad()
}

data class AvslåManglendeDokumentasjonRequest(
    val søknadId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String = "",
)
