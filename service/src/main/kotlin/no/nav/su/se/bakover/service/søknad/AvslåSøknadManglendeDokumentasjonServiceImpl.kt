package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.nonEmptyListOf
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.OpplysningspliktBeskrivelse
import no.nav.su.se.bakover.domain.grunnlag.Opplysningspliktgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.OpplysningspliktVilkår
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeOpplysningsplikt
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

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
        val avslag = søknadsbehandling
            //TODO transaksjon på tvers av service
            .oppdaterStønadsperiodeVedBehov()
            .leggTilAvslåttOpplysningspliktVilkår()
            .sendTilAttestering(
                saksbehandler = request.saksbehandler,
                fritekst = request.fritekstTilBrev,
            )
            //TODO tillat at saksbehandler og attestant er den samme
            .iverksett(attestant = NavIdentBruker.Attestant("srvsupstonad"))

        return sakService.hentSak(avslag.sakId)
            .mapLeft { KunneIkkeAvslåSøknad.FantIkkeSak }
    }

    private fun Søknadsbehandling.oppdaterStønadsperiodeVedBehov(): Søknadsbehandling {
        return if (stønadsperiode == null) {
            søknadsbehandlingService.oppdaterStønadsperiode(
                request = SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = id,
                    stønadsperiode = Stønadsperiode.create(
                        periode = Periode.create(
                            fraOgMed = LocalDate.now(clock).startOfMonth(),
                            tilOgMed = LocalDate.now(clock).endOfMonth(),
                        ),
                        begrunnelse = "",
                    ),
                    sakId = sakId,
                ),
            ).getOrHandle { throw IllegalStateException(it.toString()) }
        } else {
            this
        }
    }

    private fun Søknadsbehandling.leggTilAvslåttOpplysningspliktVilkår(): Søknadsbehandling.Vilkårsvurdert.Avslag {
        return søknadsbehandlingService.leggTilOpplysningspliktVilkår(
            request = LeggTilOpplysningspliktRequest.Søknadsbehandling(
                behandlingId = id,
                vilkår = OpplysningspliktVilkår.Vurdert.tryCreate(
                    vurderingsperioder = nonEmptyListOf(
                        VurderingsperiodeOpplysningsplikt.create(
                            id = UUID.randomUUID(),
                            opprettet = opprettet,
                            periode = periode,
                            grunnlag = Opplysningspliktgrunnlag(
                                id = UUID.randomUUID(),
                                opprettet = opprettet,
                                periode = periode,
                                beskrivelse = OpplysningspliktBeskrivelse.UtilstrekkeligDokumentasjon,
                            ),
                        ),
                    ),
                ).getOrHandle { throw IllegalArgumentException(it.toString()) },
            ),
        ).getOrHandle { throw IllegalStateException(it.toString()) } as Søknadsbehandling.Vilkårsvurdert.Avslag
    }

    private fun Søknadsbehandling.sendTilAttestering(
        saksbehandler: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Søknadsbehandling.TilAttestering {
        return søknadsbehandlingService.sendTilAttestering(
            request = SøknadsbehandlingService.SendTilAttesteringRequest(
                behandlingId = id,
                saksbehandler = saksbehandler,
                fritekstTilBrev = fritekst,
            ),
        ).getOrHandle { throw IllegalStateException(it.toString()) }
    }

    private fun Søknadsbehandling.iverksett(attestant: NavIdentBruker.Attestant): Søknadsbehandling.Iverksatt {
        return søknadsbehandlingService.iverksett(
            request = SøknadsbehandlingService.IverksettRequest(
                behandlingId = id,
                attestering = Attestering.Iverksatt(
                    attestant = attestant,
                    opprettet = Tidspunkt.now(clock),
                ),
            ),
        ).getOrHandle { throw IllegalStateException(it.toString()) }
    }
}
