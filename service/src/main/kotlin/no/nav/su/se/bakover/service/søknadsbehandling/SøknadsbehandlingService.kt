package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingRequest
import java.util.UUID
import kotlin.reflect.KClass

interface SøknadsbehandlingService {
    fun opprett(request: OpprettRequest): Either<KunneIkkeOpprette, Søknadsbehandling.Vilkårsvurdert.Uavklart>
    fun vilkårsvurder(request: VilkårsvurderRequest): Either<KunneIkkeVilkårsvurdere, Søknadsbehandling.Vilkårsvurdert>
    fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, Søknadsbehandling.Beregnet>
    fun simuler(request: SimulerRequest): Either<KunneIkkeSimulereBehandling, Søknadsbehandling.Simulert>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeTilAttestering, Søknadsbehandling.TilAttestering>
    fun underkjenn(request: UnderkjennRequest): Either<KunneIkkeUnderkjenne, Søknadsbehandling.Underkjent>
    fun iverksett(request: IverksettRequest): Either<KunneIkkeIverksette, Søknadsbehandling.Iverksatt>
    fun brev(request: BrevRequest): Either<KunneIkkeLageBrev, ByteArray>
    fun hent(request: HentRequest): Either<FantIkkeBehandling, Søknadsbehandling>
    fun oppdaterStønadsperiode(request: OppdaterStønadsperiodeRequest): Either<KunneIkkeOppdatereStønadsperiode, Søknadsbehandling>
    fun leggTilUføregrunnlag(request: LeggTilUførevurderingRequest): Either<KunneIkkeLeggeTilGrunnlag, Søknadsbehandling>
    fun leggTilBosituasjonEpsgrunnlag(request: LeggTilBosituasjonEpsRequest): Either<KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Søknadsbehandling>
    fun fullførBosituasjongrunnlag(request: FullførBosituasjonRequest): Either<KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling>
    fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling>

    data class OpprettRequest(
        val søknadId: UUID,
    )

    sealed class KunneIkkeOpprette {
        object FantIkkeSøknad : KunneIkkeOpprette()
        object SøknadManglerOppgave : KunneIkkeOpprette()
        object SøknadErLukket : KunneIkkeOpprette()
        object SøknadHarAlleredeBehandling : KunneIkkeOpprette()
    }

    data class VilkårsvurderRequest(
        val behandlingId: UUID,
        val behandlingsinformasjon: Behandlingsinformasjon,
    )

    sealed class KunneIkkeVilkårsvurdere {
        object FantIkkeBehandling : KunneIkkeVilkårsvurdere()
        object HarIkkeEktefelle : KunneIkkeVilkårsvurdere()
    }

    data class BeregnRequest(
        val behandlingId: UUID,
        val begrunnelse: String?,
    )

    sealed class KunneIkkeBeregne {
        object FantIkkeBehandling : KunneIkkeBeregne()
        object IkkeLovMedFradragUtenforPerioden : KunneIkkeBeregne()
        object UgyldigFradragstype : KunneIkkeBeregne()
        object HarIkkeEktefelle : KunneIkkeBeregne()
    }

    data class SimulerRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed class KunneIkkeSimulereBehandling {
        data class KunneIkkeSimulere(val simuleringFeilet: SimuleringFeilet) : KunneIkkeSimulereBehandling()
        object FantIkkeBehandling : KunneIkkeSimulereBehandling()
    }

    data class SendTilAttesteringRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val fritekstTilBrev: String,
    )

    sealed class KunneIkkeSendeTilAttestering {
        object FantIkkeBehandling : KunneIkkeSendeTilAttestering()
        object KunneIkkeFinneAktørId : KunneIkkeSendeTilAttestering()
        object KunneIkkeOppretteOppgave : KunneIkkeSendeTilAttestering()
    }

    data class UnderkjennRequest(
        val behandlingId: UUID,
        val attestering: Attestering.Underkjent,
    )

    sealed class KunneIkkeUnderkjenne {
        object FantIkkeBehandling : KunneIkkeUnderkjenne()
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeUnderkjenne()
        object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenne()
        object FantIkkeAktørId : KunneIkkeUnderkjenne()
    }

    data class IverksettRequest(
        val behandlingId: UUID,
        val attestering: Attestering,
    )

    sealed class BrevRequest {
        abstract val behandling: Søknadsbehandling

        data class MedFritekst(
            override val behandling: Søknadsbehandling,
            val fritekst: String,
        ) : BrevRequest()

        data class UtenFritekst(
            override val behandling: Søknadsbehandling,
        ) : BrevRequest()
    }

    sealed class KunneIkkeLageBrev {
        data class KanIkkeLageBrevutkastForStatus(val status: BehandlingsStatus) : KunneIkkeLageBrev()
        object KunneIkkeLagePDF : KunneIkkeLageBrev()
        object FantIkkePerson : KunneIkkeLageBrev()
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeLageBrev()
        object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrev()
    }

    data class HentRequest(
        val behandlingId: UUID,
    )

    object FantIkkeBehandling
    object KunneIkkeHenteAktiveBehandlinger

    sealed class KunneIkkeOppdatereStønadsperiode {
        object FantIkkeBehandling : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
        object FraOgMedDatoKanIkkeVæreFør2021 : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
        object PeriodeKanIkkeVæreLengreEnn12Måneder : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
    }

    data class OppdaterStønadsperiodeRequest(
        val behandlingId: UUID,
        val stønadsperiode: Stønadsperiode,
    )

    sealed class KunneIkkeLeggeTilGrunnlag {
        object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlag()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilGrunnlag()

        object UføregradOgForventetInntektMangler : KunneIkkeLeggeTilGrunnlag()
        object PeriodeForGrunnlagOgVurderingErForskjellig : KunneIkkeLeggeTilGrunnlag()
        object OverlappendeVurderingsperioder : KunneIkkeLeggeTilGrunnlag()
        object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : KunneIkkeLeggeTilGrunnlag()
    }

    sealed class KunneIkkeLeggeTilBosituasjonEpsGrunnlag {
        object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()

        object KlarteIkkeHentePersonIPdl : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()

        object FradragForEpsSomIkkeHarEPS : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()
        object FradragManglerBosituasjon : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()
        object MåLeggeTilBosituasjonFørFradrag : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()
    }

    sealed class KunneIkkeFullføreBosituasjonGrunnlag {
        object FantIkkeBehandling : KunneIkkeFullføreBosituasjonGrunnlag()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeFullføreBosituasjonGrunnlag()

        object KlarteIkkeLagreBosituasjon : KunneIkkeFullføreBosituasjonGrunnlag()
        object KlarteIkkeHentePersonIPdl : KunneIkkeFullføreBosituasjonGrunnlag()
        object FradragForEpsSomIkkeHarEPS : KunneIkkeFullføreBosituasjonGrunnlag()
        object FradragManglerBosituasjon : KunneIkkeFullføreBosituasjonGrunnlag()
        object MåLeggeTilBosituasjonFørFradrag : KunneIkkeFullføreBosituasjonGrunnlag()
    }

    sealed class KunneIkkeLeggeTilFradragsgrunnlag {
        object FantIkkeBehandling : KunneIkkeLeggeTilFradragsgrunnlag()
        object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag()
        object UgyldigFradragstypeForGrunnlag : KunneIkkeLeggeTilFradragsgrunnlag()
        object PeriodeMangler : KunneIkkeLeggeTilFradragsgrunnlag()
        object HarIkkeEktelle : KunneIkkeLeggeTilFradragsgrunnlag()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFradragsgrunnlag()

        object FradragForEpsSomIkkeHarEPS : KunneIkkeLeggeTilFradragsgrunnlag()
        object FradragManglerBosituasjon : KunneIkkeLeggeTilFradragsgrunnlag()
        object MåLeggeTilBosituasjonFørFradrag : KunneIkkeLeggeTilFradragsgrunnlag()
    }
}
