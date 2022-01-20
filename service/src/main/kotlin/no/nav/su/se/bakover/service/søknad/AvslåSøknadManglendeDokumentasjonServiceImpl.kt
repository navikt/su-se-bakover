package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import no.nav.su.se.bakover.common.log
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock

internal class AvslåSøknadManglendeDokumentasjonServiceImpl(
    private val clock: Clock,
    private val søknadsbehandlingService: SøknadsbehandlingService,
    private val vedtakService: VedtakService,
    private val oppgaveService: OppgaveService,
    private val brevService: BrevService,
    private val sessionFactory: SessionFactory,
    private val sakService: SakService,
) : AvslåSøknadManglendeDokumentasjonService {
    override fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Sak> {
        return søknadsbehandlingService.hentForSøknad(request.søknadId)
            ?.let { harBehandlingFraFør(request, it) }
            ?: opprettNyBehandlingFørst(request)
    }

    private fun harBehandlingFraFør(
        request: AvslåManglendeDokumentasjonRequest,
        søknadsbehandling: Søknadsbehandling,
    ): Either<KunneIkkeAvslåSøknad, Sak> {
        return avslå(request, søknadsbehandling)
    }

    private fun opprettNyBehandlingFørst(
        request: AvslåManglendeDokumentasjonRequest,
    ): Either<KunneIkkeAvslåSøknad, Sak> =
        søknadsbehandlingService.opprett(
            request = SøknadsbehandlingService.OpprettRequest(
                søknadId = request.søknadId,
            ),
        ).mapLeft {
            when (it) {
                SøknadsbehandlingService.KunneIkkeOpprette.FantIkkeSøknad -> KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.FantIkkeSøknad
                SøknadsbehandlingService.KunneIkkeOpprette.HarAlleredeÅpenSøknadsbehandling -> KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.HarAlleredeÅpenSøknadsbehandling
                SøknadsbehandlingService.KunneIkkeOpprette.SøknadErLukket -> KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.SøknadErLukket
                SøknadsbehandlingService.KunneIkkeOpprette.SøknadHarAlleredeBehandling -> KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.SøknadHarAlleredeBehandling
                SøknadsbehandlingService.KunneIkkeOpprette.SøknadManglerOppgave -> KunneIkkeAvslåSøknad.KunneIkkeOppretteSøknadsbehandling.SøknadManglerOppgave
            }
        }.map {
            return avslå(request, it)
        }

    private fun avslå(
        request: AvslåManglendeDokumentasjonRequest,
        søknadsbehandling: Søknadsbehandling,
    ): Either<KunneIkkeAvslåSøknad, Sak> {

        val avslag = AvslagManglendeDokumentasjon.tryCreate(
            søknadsbehandling = søknadsbehandling,
            saksbehandler = request.saksbehandler,
            fritekstTilBrev = request.fritekstTilBrev,
            clock = clock,
        ).getOrHandle { return KunneIkkeAvslåSøknad.SøknadsbehandlingIUgyldigTilstandForAvslag.left() }

        val avslagsvedtak: Avslagsvedtak.AvslagVilkår = Avslagsvedtak.fromAvslagManglendeDokumentasjon(
            avslag = avslag,
            clock = clock,
        )

        val dokument = brevService.lagDokument(avslagsvedtak).getOrHandle {
            return when (it) {
                KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling -> KunneIkkeAvslåSøknad.KunneIkkeFinneGjeldendeUtbetaling
                KunneIkkeLageDokument.KunneIkkeGenererePDF -> KunneIkkeAvslåSøknad.KunneIkkeGenererePDF
                KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant -> KunneIkkeAvslåSøknad.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant
                KunneIkkeLageDokument.KunneIkkeHentePerson -> KunneIkkeAvslåSøknad.KunneIkkeHentePerson
            }.left()
        }.leggTilMetadata(
            metadata = Dokument.Metadata(
                sakId = avslagsvedtak.behandling.sakId,
                søknadId = avslagsvedtak.behandling.søknad.id,
                vedtakId = avslagsvedtak.id,
                bestillBrev = true,
            ),
        )

        sessionFactory.withTransactionContext { tx ->
            søknadsbehandlingService.lagre(avslag, tx)
            vedtakService.lagre(avslagsvedtak, tx)
            brevService.lagreDokument(dokument, tx)
        }

        oppgaveService.lukkOppgave(avslag.søknadsbehandling.oppgaveId)
            .mapLeft {
                log.warn("Klarte ikke å lukke oppgave for søknadsbehandling: ${avslag.søknadsbehandling.id}, feil:$it")
            }

        return sakService.hentSak(avslagsvedtak.behandling.sakId)
            .mapLeft { KunneIkkeAvslåSøknad.FantIkkeSak }
    }
}
