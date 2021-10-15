package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.util.UUID

interface AvslåSøknadManglendeDokumentasjon {
    fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Vedtak.Avslag>
}

class AvslåSøknadManglendeDokumentasjonService(
    private val clock: Clock = Clock.systemUTC(),
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val vedtakService: VedtakService,
    private val oppgaveService: OppgaveService,
) : AvslåSøknadManglendeDokumentasjon {
    override fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Vedtak.Avslag> {
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

        oppgaveService.lukkOppgave(avslag.søknadsbehandling.oppgaveId)
            .mapLeft {
                log.warn("Klarte ikke å lukke oppgave for søknadsbehandling: ${avslag.søknadsbehandling.id}, feil:$it")
            }

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
