package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagManglendeDokumentasjon
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.LukketSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.KunneIkkeLageLovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.UgyldigFamiliegjenforeningVilkår
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.revurdering.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.service.revurdering.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.service.vilkår.FullførBosituasjonRequest
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.service.vilkår.LeggTilBosituasjonEpsRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUtenlandsoppholdRequest
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
    fun leggTilUførevilkår(request: LeggTilUførevurderingerRequest): Either<KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling>
    fun leggTilLovligOpphold(request: LeggTilLovligOppholdRequest): Either<KunneIkkeLeggetilLovligOppholdVilkår, Søknadsbehandling>
    fun leggTilFamiliegjenforeningvilkår(request: LeggTilFamiliegjenforeningRequest): Either<KunneIkkeLeggeTilFamiliegjenforeningVilkårService, Søknadsbehandling>
    fun leggTilBosituasjonEpsgrunnlag(request: LeggTilBosituasjonEpsRequest): Either<KunneIkkeLeggeTilBosituasjonEpsGrunnlag, Søknadsbehandling>
    fun fullførBosituasjongrunnlag(request: FullførBosituasjonRequest): Either<KunneIkkeFullføreBosituasjonGrunnlag, Søknadsbehandling>
    fun leggTilFradragsgrunnlag(request: LeggTilFradragsgrunnlagRequest): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling>

    fun leggTilFormuevilkår(request: LeggTilFormuevilkårRequest): Either<KunneIkkeLeggeTilFormuegrunnlag, Søknadsbehandling>
    fun hentForSøknad(søknadId: UUID): Søknadsbehandling?
    fun lukk(lukketSøknadbehandling: LukketSøknadsbehandling, tx: TransactionContext)
    fun lagre(avslag: AvslagManglendeDokumentasjon, tx: TransactionContext)
    fun leggTilUtenlandsopphold(request: LeggTilUtenlandsoppholdRequest): Either<KunneIkkeLeggeTilUtenlandsopphold, Søknadsbehandling.Vilkårsvurdert>
    fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Søknadsbehandling): Either<KunneIkkeLeggeTilOpplysningsplikt, Søknadsbehandling.Vilkårsvurdert>
    fun leggTilPensjonsVilkår(request: LeggTilPensjonsVilkårRequest): Either<KunneIkkeLeggeTilPensjonsVilkår, Søknadsbehandling.Vilkårsvurdert>

    data class OpprettRequest(
        val søknadId: UUID,
    )

    sealed class KunneIkkeOpprette {
        object FantIkkeSøknad : KunneIkkeOpprette()
        object SøknadManglerOppgave : KunneIkkeOpprette()
        object SøknadErLukket : KunneIkkeOpprette()
        object SøknadHarAlleredeBehandling : KunneIkkeOpprette()
        object HarAlleredeÅpenSøknadsbehandling : KunneIkkeOpprette()
    }

    sealed class KunneIkkeVilkårsvurdere {
        object FantIkkeBehandling : KunneIkkeVilkårsvurdere()
    }

    data class BeregnRequest(
        val behandlingId: UUID,
        val begrunnelse: String?,
    )

    sealed class KunneIkkeBeregne {
        object FantIkkeBehandling : KunneIkkeBeregne()
        object KunneIkkeSimulereUtbetaling : KunneIkkeBeregne()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeBeregne()

        data class UgyldigTilstandForEndringAvFradrag(val feil: KunneIkkeLeggeTilFradragsgrunnlag) : KunneIkkeBeregne()
        object AvkortingErUfullstendig : KunneIkkeBeregne()
    }

    data class SimulerRequest(
        val behandlingId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed class KunneIkkeSimulereBehandling {
        data class KunneIkkeSimulere(val feil: no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeSimulereBehandling) : KunneIkkeSimulereBehandling()
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
        object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeSendeTilAttestering()
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
        object KunneIkkeLagePDF : KunneIkkeLageBrev()
        object FantIkkePerson : KunneIkkeLageBrev()
        object FikkIkkeHentetSaksbehandlerEllerAttestant : KunneIkkeLageBrev()
        object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrev()
    }

    data class HentRequest(
        val behandlingId: UUID,
    )

    object FantIkkeBehandling

    sealed class KunneIkkeOppdatereStønadsperiode {
        object FantIkkeBehandling : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
        object FantIkkeSak : SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
        data class KunneIkkeOppdatereStønadsperiode(val feil: Sak.KunneIkkeOppdatereStønadsperiode) :
            SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode()
    }

    data class OppdaterStønadsperiodeRequest(
        val behandlingId: UUID,
        val stønadsperiode: Stønadsperiode,
        val sakId: UUID,
    )

    sealed class KunneIkkeLeggeTilUføreVilkår {
        object FantIkkeBehandling : KunneIkkeLeggeTilUføreVilkår()
        object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : KunneIkkeLeggeTilUføreVilkår()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUføreVilkår()

        data class UgyldigInput(
            val originalFeil: LeggTilUførevurderingerRequest.UgyldigUførevurdering,
        ) : KunneIkkeLeggeTilUføreVilkår()
    }

    sealed interface KunneIkkeLeggeTilFamiliegjenforeningVilkårService {
        object FantIkkeBehandling : KunneIkkeLeggeTilFamiliegjenforeningVilkårService
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkårService

        data class UgyldigFamiliegjenforeningVilkårService(val feil: UgyldigFamiliegjenforeningVilkår) :
            KunneIkkeLeggeTilFamiliegjenforeningVilkårService

        fun KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.tilKunneIkkeLeggeTilFamiliegjenforeningVilkårService() =
            when (this) {
                is KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår.UgyldigTilstand -> UgyldigTilstand(
                    fra = this.fra,
                    til = this.til,
                )
            }
    }

    sealed class KunneIkkeLeggeTilBosituasjonEpsGrunnlag {
        object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()

        object KlarteIkkeHentePersonIPdl : KunneIkkeLeggeTilBosituasjonEpsGrunnlag()

        data class KunneIkkeOppdatereBosituasjon(val feil: KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon) :
            KunneIkkeLeggeTilBosituasjonEpsGrunnlag()
    }

    sealed class KunneIkkeFullføreBosituasjonGrunnlag {
        object FantIkkeBehandling : KunneIkkeFullføreBosituasjonGrunnlag()

        object KlarteIkkeLagreBosituasjon : KunneIkkeFullføreBosituasjonGrunnlag()
        object KlarteIkkeHentePersonIPdl : KunneIkkeFullføreBosituasjonGrunnlag()
        data class KunneIkkeEndreBosituasjongrunnlag(val feil: KunneIkkeLeggeTilGrunnlag.KunneIkkeOppdatereBosituasjon) :
            KunneIkkeFullføreBosituasjonGrunnlag()
    }

    sealed class KunneIkkeLeggeTilFradragsgrunnlag {
        object FantIkkeBehandling : KunneIkkeLeggeTilFradragsgrunnlag()
        object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFradragsgrunnlag()

        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilFradragsgrunnlag()
    }

    sealed class KunneIkkeLeggeTilUtenlandsopphold {
        object FantIkkeBehandling : KunneIkkeLeggeTilUtenlandsopphold()
        object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
        object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold()
        object MåInneholdeKunEnVurderingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
        object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold()
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUtenlandsopphold()
    }

    sealed interface KunneIkkeLeggeTilFormuegrunnlag {
        object FantIkkeSøknadsbehandling : KunneIkkeLeggeTilFormuegrunnlag
        data class KunneIkkeMappeTilDomenet(val feil: LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet) :
            KunneIkkeLeggeTilFormuegrunnlag

        data class KunneIkkeLeggeTilFormuegrunnlagTilSøknadsbehandling(val feil: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår) :
            KunneIkkeLeggeTilFormuegrunnlag
    }

    sealed interface KunneIkkeLeggetilLovligOppholdVilkår {
        object FantIkkeBehandling : KunneIkkeLeggetilLovligOppholdVilkår

        data class UgyldigLovligOppholdVilkår(val feil: KunneIkkeLageLovligOppholdVilkår) :
            KunneIkkeLeggetilLovligOppholdVilkår

        data class FeilVedSøknadsbehandling(val feil: Søknadsbehandling.KunneIkkeLeggeTilLovligOpphold) :
            KunneIkkeLeggetilLovligOppholdVilkår
    }
}
