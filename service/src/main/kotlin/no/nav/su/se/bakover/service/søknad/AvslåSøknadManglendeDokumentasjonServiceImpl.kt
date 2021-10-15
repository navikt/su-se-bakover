package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock

internal class AvslåSøknadManglendeDokumentasjonServiceImpl(
    private val clock: Clock = Clock.systemUTC(),
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val vedtakService: VedtakService,
    private val oppgaveService: OppgaveService,
    private val brevService: BrevService,
) : AvslåSøknadManglendeDokumentasjonService {
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

        oppgaveService.lukkOppgave(avslag.søknadsbehandling.oppgaveId)
            .mapLeft {
                log.warn("Klarte ikke å lukke oppgave for søknadsbehandling: ${avslag.søknadsbehandling.id}, feil:$it")
            }

        val dokument = brevService.lagDokument(vedtak).getOrHandle {
            return when (it) {
                KunneIkkeLageDokument.FantIkkePerson -> KunneIkkeAvslåSøknad.FantIkkePerson
                KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeAvslåSøknad.KunneIkkeFinneGjeldendeUtbetaling
                KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeAvslåSøknad.KunneIkkeGenererePDF
                KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeAvslåSøknad.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeAvslåSøknad.KunneIkkeHentePerson
            }.left()
        }

        brevService.lagreDokument(
            dokument.leggTilMetadata(
                metadata = Dokument.Metadata(
                    sakId = vedtak.behandling.sakId,
                    søknadId = vedtak.behandling.søknad.id,
                    vedtakId = vedtak.id,
                    bestillBrev = true,
                ),
            ),
        )

        return vedtak.right()
    }
}
