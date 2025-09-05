package no.nav.su.se.bakover.domain.søknadsbehandling

import arrow.core.Either
import behandling.domain.fradrag.LeggTilFradragsgrunnlagRequest
import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import behandling.søknadsbehandling.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlag
import behandling.søknadsbehandling.domain.bosituasjon.LeggTilBosituasjonerCommand
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.FeilVedHentingAvGjeldendeVedtaksdataForPeriode
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.BrevutkastForSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.brev.utkast.KunneIkkeGenerereBrevutkastForSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.KunneIkkeLeggeTilSkattegrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.grunnlag.SøknadsbehandlingSkatt
import no.nav.su.se.bakover.domain.søknadsbehandling.retur.KunneIkkeReturnereSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.simuler.KunneIkkeSimulereBehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.SaksbehandlersAvgjørelse
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.underkjenn.KunneIkkeUnderkjenneSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.vilkår.KunneIkkeLeggeTilVilkår
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.flyktning.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
import vilkår.familiegjenforening.domain.UgyldigFamiliegjenforeningVilkår
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger
import vilkår.vurderinger.domain.KunneIkkeLageGrunnlagsdata
import java.util.UUID
import kotlin.reflect.KClass

interface SøknadsbehandlingService {
    fun opprett(
        request: OpprettRequest,
        hentSak: (() -> Sak)? = null,
    ): Either<KunneIkkeOppretteSøknadsbehandling, Pair<Sak, VilkårsvurdertSøknadsbehandling.Uavklart>>

    fun beregn(request: BeregnRequest): Either<KunneIkkeBeregne, BeregnetSøknadsbehandling>
    fun simuler(request: SimulerRequest): Either<KunneIkkeSimulereBehandling, SimulertSøknadsbehandling>
    fun sendTilAttestering(request: SendTilAttesteringRequest): Either<KunneIkkeSendeSøknadsbehandlingTilAttestering, SøknadsbehandlingTilAttestering>
    fun underkjenn(request: UnderkjennRequest): Either<KunneIkkeUnderkjenneSøknadsbehandling, UnderkjentSøknadsbehandling>
    fun retur(request: ReturRequest): Either<KunneIkkeReturnereSøknadsbehandling, Søknadsbehandling>

    fun genererBrevutkast(
        command: BrevutkastForSøknadsbehandlingCommand,
    ): Either<KunneIkkeGenerereBrevutkastForSøknadsbehandling, Pair<PdfA, Fnr>>

    fun hent(request: HentRequest): Either<FantIkkeBehandling, Søknadsbehandling>

    /**
     * Oppdatering av stønadsperiode tar hensyn til personens alder ved slutten av stønadsperioden.
     */
    fun oppdaterStønadsperiode(request: OppdaterStønadsperiodeRequest): Either<Sak.KunneIkkeOppdatereStønadsperiode, VilkårsvurdertSøknadsbehandling>
    fun leggTilUførevilkår(
        request: LeggTilUførevurderingerRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilUføreVilkår, Søknadsbehandling>

    fun leggTilLovligOpphold(
        request: LeggTilLovligOppholdRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggetilLovligOppholdVilkårForSøknadsbehandling, Søknadsbehandling>

    fun leggTilFamiliegjenforeningvilkår(
        request: LeggTilFamiliegjenforeningRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFamiliegjenforeningVilkårService, Søknadsbehandling>

    fun leggTilFradragsgrunnlag(
        request: LeggTilFradragsgrunnlagRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFradragsgrunnlag, Søknadsbehandling>

    fun leggTilFormuevilkår(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFormuevilkår, Søknadsbehandling>

    fun hentForSøknad(søknadId: UUID): Søknadsbehandling?
    fun persisterSøknadsbehandling(lukketSøknadbehandling: LukketSøknadsbehandling, tx: TransactionContext)
    fun leggTilUtenlandsopphold(
        request: LeggTilFlereUtenlandsoppholdRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, VilkårsvurdertSøknadsbehandling>

    fun leggTilOpplysningspliktVilkår(request: LeggTilOpplysningspliktRequest.Søknadsbehandling): Either<KunneIkkeLeggeTilOpplysningsplikt, VilkårsvurdertSøknadsbehandling>

    fun leggTilPensjonsVilkår(
        request: LeggTilPensjonsVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilPensjonsVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilFlyktningVilkår(
        request: LeggTilFlyktningVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilFlyktningVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilFastOppholdINorgeVilkår(
        request: LeggTilFastOppholdINorgeRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilPersonligOppmøteVilkår(
        request: LeggTilPersonligOppmøteVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilPersonligOppmøteVilkårForSøknadsbehandling, VilkårsvurdertSøknadsbehandling>

    fun leggTilInstitusjonsoppholdVilkår(
        request: LeggTilInstitusjonsoppholdVilkårRequest,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, VilkårsvurdertSøknadsbehandling>

    fun leggTilBosituasjongrunnlag(
        request: LeggTilBosituasjonerCommand,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, VilkårsvurdertSøknadsbehandling>

    fun oppdaterSkattegrunnlag(
        søknadsbehandlingSkatt: SøknadsbehandlingSkatt,
    ): Either<KunneIkkeLeggeTilSkattegrunnlag, Søknadsbehandling>

    fun lagre(søknadsbehandling: Søknadsbehandling)

    /**
     * Henter gjeldende vedtaksdata for perioden som er før perioden som settes for denne søknadsbehandlingen.
     *
     * Eksempel: Hvis vi har et vedtak for ny periode (01.2021 - 12.2021), og vi har fått en ny søknad for
     * perioden 01.2022 - 12.2022, så vil vi hente gjeldende vedtaksdata for perioden 01.2021 - 12.2021.
     */
    fun hentSisteInnvilgetSøknadsbehandlingGrunnlagForSakFiltrerVekkSøknadsbehandling(
        sakId: UUID,
        søknadsbehandlingId: SøknadsbehandlingId,
    ): Either<FeilVedHentingAvGjeldendeVedtaksdataForPeriode, Pair<Periode, GrunnlagsdataOgVilkårsvurderinger>>

    data class OpprettRequest(
        val søknadId: UUID,
        val sakId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed interface KunneIkkeVilkårsvurdere {
        data object FantIkkeBehandling : KunneIkkeVilkårsvurdere
    }

    data class BeregnRequest(
        val behandlingId: SøknadsbehandlingId,
        val begrunnelse: String?,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    sealed interface KunneIkkeBeregne {
        data object FantIkkeBehandling : KunneIkkeBeregne
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
        ) : KunneIkkeBeregne {
            val til: KClass<out BeregnetSøknadsbehandling> = BeregnetSøknadsbehandling::class
        }

        data class UgyldigTilstandForEndringAvFradrag(val feil: KunneIkkeLeggeTilFradragsgrunnlag) : KunneIkkeBeregne
    }

    data class SimulerRequest(
        val behandlingId: SøknadsbehandlingId,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    data class SendTilAttesteringRequest(
        val behandlingId: SøknadsbehandlingId,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val fritekstTilBrev: String,
    )

    data class UnderkjennRequest(
        val behandlingId: SøknadsbehandlingId,
        val attestering: Attestering.Underkjent,
    )

    data class ReturRequest(
        val behandlingId: SøknadsbehandlingId,
        val saksbehandler: NavIdentBruker.Saksbehandler,
    )

    data class HentRequest(
        val behandlingId: SøknadsbehandlingId,
    )

    data object FantIkkeBehandling

    data class OppdaterStønadsperiodeRequest(
        val behandlingId: SøknadsbehandlingId,
        val stønadsperiode: Stønadsperiode,
        val sakId: UUID,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val saksbehandlersAvgjørelse: SaksbehandlersAvgjørelse?,
    )

    sealed interface KunneIkkeLeggeTilUføreVilkår {
        data object FantIkkeBehandling : KunneIkkeLeggeTilUføreVilkår
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUføreVilkår

        data class UgyldigInput(
            val underliggende: LeggTilUførevurderingerRequest.UgyldigUførevurdering,
        ) : KunneIkkeLeggeTilUføreVilkår

        data class Domenefeil(val underliggende: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUførevilkår) : KunneIkkeLeggeTilUføreVilkår
    }

    sealed interface KunneIkkeLeggeTilFamiliegjenforeningVilkårService {
        data object FantIkkeBehandling : KunneIkkeLeggeTilFamiliegjenforeningVilkårService
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkårService

        data class UgyldigFamiliegjenforeningVilkårService(
            val feil: UgyldigFamiliegjenforeningVilkår,
        ) : KunneIkkeLeggeTilFamiliegjenforeningVilkårService

        data class Domenefeil(val underliggende: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilFamiliegjenforeningVilkår) : KunneIkkeLeggeTilFamiliegjenforeningVilkårService
    }

    sealed interface KunneIkkeLeggeTilFradragsgrunnlag {
        data object FantIkkeBehandling : KunneIkkeLeggeTilFradragsgrunnlag
        data object GrunnlagetMåVæreInnenforBehandlingsperioden : KunneIkkeLeggeTilFradragsgrunnlag
        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilFradragsgrunnlag

        data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilFradragsgrunnlag

        data object FradrageneMåSlåsSammen : KunneIkkeLeggeTilFradragsgrunnlag
    }

    sealed interface KunneIkkeLeggeTilUtenlandsopphold {
        data object FantIkkeBehandling : KunneIkkeLeggeTilUtenlandsopphold

        data class UgyldigTilstand(
            val fra: KClass<out Søknadsbehandling>,
            val til: KClass<out Søknadsbehandling>,
        ) : KunneIkkeLeggeTilUtenlandsopphold

        data class Domenefeil(val underliggende: KunneIkkeLeggeTilVilkår.KunneIkkeLeggeTilUtenlandsopphold) : KunneIkkeLeggeTilUtenlandsopphold
    }
}
