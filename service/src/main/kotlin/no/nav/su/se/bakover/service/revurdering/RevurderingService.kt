package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.service.grunnlag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.service.utbetaling.SimulerGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggeTilPersonligOppmøteVilkår
import no.nav.su.se.bakover.service.vilkår.KunneIkkeLeggetilLovligOppholdVilkår
import no.nav.su.se.bakover.service.vilkår.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlereUtenlandsoppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

interface RevurderingService {
    fun hentRevurdering(revurderingId: UUID): AbstraktRevurdering?

    fun stansAvYtelse(
        request: StansYtelseRequest,
    ): Either<KunneIkkeStanseYtelse, StansAvYtelseRevurdering.SimulertStansAvYtelse>

    fun iverksettStansAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse>

    fun gjenopptaYtelse(
        request: GjenopptaYtelseRequest,
    ): Either<KunneIkkeGjenopptaYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>

    fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelse, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse>

    fun opprettRevurdering(
        opprettRevurderingRequest: OpprettRevurderingRequest,
    ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering>

    fun oppdaterRevurdering(
        oppdaterRevurderingRequest: OppdaterRevurderingRequest,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse>

    fun lagreOgSendForhåndsvarsel(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        forhåndsvarselhandling: Forhåndsvarselhandling,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering>

    fun lagBrevutkastForForhåndsvarsling(
        revurderingId: UUID,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>

    fun sendTilAttestering(
        request: SendTilAttesteringRequest,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering>

    fun oppdaterTilbakekrevingsbehandling(
        request: OppdaterTilbakekrevingsbehandlingRequest,
    ): Either<KunneIkkeOppdatereTilbakekrevingsbehandling, SimulertRevurdering>

    fun lagBrevutkastForRevurdering(
        revurderingId: UUID,
        fritekst: String?,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>

    fun iverksett(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering>

    fun underkjenn(
        revurderingId: UUID,
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering>

    fun fortsettEtterForhåndsvarsling(
        request: FortsettEtterForhåndsvarslingRequest,
    ): Either<FortsettEtterForhåndsvarselFeil, Revurdering>

    fun leggTilUførevilkår(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilUføreVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilUtenlandsopphold(
        request: LeggTilFlereUtenlandsoppholdRequest,
    ): Either<KunneIkkeLeggeTilUtenlandsopphold, RevurderingOgFeilmeldingerResponse>

    fun leggTilFradragsgrunnlag(
        request: LeggTilFradragsgrunnlagRequest,
    ): Either<KunneIkkeLeggeTilFradragsgrunnlag, RevurderingOgFeilmeldingerResponse>

    fun leggTilBosituasjongrunnlag(
        request: LeggTilBosituasjonerRequest,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, RevurderingOgFeilmeldingerResponse>

    fun leggTilFormuegrunnlag(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse>

    fun lagBrevutkastForAvslutting(
        revurderingId: UUID,
        fritekst: String?,
    ): Either<KunneIkkeLageBrevutkastForAvsluttingAvRevurdering, Pair<Fnr, ByteArray>>

    fun avsluttRevurdering(
        revurderingId: UUID,
        begrunnelse: String,
        brevvalg: Brevvalg.SaksbehandlersValg?,
    ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering>

    fun leggTilOpplysningspliktVilkår(
        request: LeggTilOpplysningspliktRequest.Revurdering,
    ): Either<KunneIkkeLeggeTilOpplysningsplikt, RevurderingOgFeilmeldingerResponse>

    fun leggTilPensjonsVilkår(
        request: LeggTilPensjonsVilkårRequest,
    ): Either<KunneIkkeLeggeTilPensjonsVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilLovligOppholdVilkår(
        request: LeggTilLovligOppholdRequest,
    ): Either<KunneIkkeLeggetilLovligOppholdVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilFlyktningVilkår(
        request: LeggTilFlyktningVilkårRequest,
    ): Either<KunneIkkeLeggeTilFlyktningVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilFastOppholdINorgeVilkår(
        request: LeggTilFastOppholdINorgeRequest,
    ): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilPersonligOppmøteVilkår(
        request: LeggTilPersonligOppmøteVilkårRequest,
    ): Either<KunneIkkeLeggeTilPersonligOppmøteVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilInstitusjonsoppholdVilkår(
        request: LeggTilInstitusjonsoppholdVilkårRequest
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, RevurderingOgFeilmeldingerResponse>
}

data class RevurderingOgFeilmeldingerResponse(
    val revurdering: Revurdering,
    val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes> = emptyList(),
    val varselmeldinger: List<Varselmelding> = emptyList(),
) {
    fun leggTil(varselmelding: Varselmelding): RevurderingOgFeilmeldingerResponse {
        return copy(varselmeldinger = (varselmeldinger + varselmelding).distinct())
    }

    fun leggTil(varselmeldinger: List<Pair<Boolean, Varselmelding>>): RevurderingOgFeilmeldingerResponse {
        return varselmeldinger.fold(this) { acc, (leggTil, varselmelding) ->
            if (leggTil) acc.leggTil(varselmelding) else acc
        }
    }
}

sealed interface Varselmelding {
    object BeløpsendringUnder10Prosent : Varselmelding
    object FradragOgFormueForEPSErFjernet : Varselmelding
}

object FantIkkeRevurdering

data class SendTilAttesteringRequest(
    val revurderingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
)

enum class Forhåndsvarselhandling {
    INGEN_FORHÅNDSVARSEL,
    FORHÅNDSVARSLE;
}

sealed class KunneIkkeOppretteRevurdering {
    object FantIkkeSak : KunneIkkeOppretteRevurdering()
    object FantIngenVedtakSomKanRevurderes : KunneIkkeOppretteRevurdering()
    object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppretteRevurdering()
    object TidslinjeForVedtakErIkkeKontinuerlig : KunneIkkeOppretteRevurdering()
    object UgyldigÅrsak : KunneIkkeOppretteRevurdering()
    object UgyldigBegrunnelse : KunneIkkeOppretteRevurdering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppretteRevurdering()
    object FantIkkeAktørId : KunneIkkeOppretteRevurdering()
    object KunneIkkeOppretteOppgave : KunneIkkeOppretteRevurdering()
    object FormueSomFørerTilOpphørMåRevurderes : KunneIkkeOppretteRevurdering()
    object EpsFormueMedFlereBosituasjonsperioderMåRevurderes : KunneIkkeOppretteRevurdering()
    data class UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(val periode: Periode) :
        KunneIkkeOppretteRevurdering()

    object HarÅpenBehandling : KunneIkkeOppretteRevurdering()
    object UtenlandsoppholdSomFørerTilOpphørMåRevurderes : KunneIkkeOppretteRevurdering()
}

sealed class KunneIkkeOppdatereRevurdering {
    object FantIkkeSak : KunneIkkeOppdatereRevurdering()
    object FantIngenVedtakSomKanRevurderes : KunneIkkeOppdatereRevurdering()
    object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppdatereRevurdering()
    object TidslinjeForVedtakErIkkeKontinuerlig : KunneIkkeOppdatereRevurdering()
    object UgyldigÅrsak : KunneIkkeOppdatereRevurdering()
    object UgyldigBegrunnelse : KunneIkkeOppdatereRevurdering()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeOppdatereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeOppdatereRevurdering()

    object FantIkkeRevurdering : KunneIkkeOppdatereRevurdering()
    object KanIkkeOppdatereRevurderingSomErForhåndsvarslet : KunneIkkeOppdatereRevurdering()
    object FormueSomFørerTilOpphørMåRevurderes : KunneIkkeOppdatereRevurdering()
    object EpsFormueMedFlereBosituasjonsperioderMåRevurderes : KunneIkkeOppdatereRevurdering()
    data class UteståendeAvkortingMåRevurderesEllerAvkortesINyPeriode(val periode: Periode) :
        KunneIkkeOppdatereRevurdering()

    object UtenlandsoppholdSomFørerTilOpphørMåRevurderes : KunneIkkeOppdatereRevurdering()
}

sealed class KunneIkkeBeregneOgSimulereRevurdering {
    object FantIkkeRevurdering : KunneIkkeBeregneOgSimulereRevurdering()
    object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeBeregneOgSimulereRevurdering()

    data class UgyldigBeregningsgrunnlag(
        val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
    ) : KunneIkkeBeregneOgSimulereRevurdering()

    object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneOgSimulereRevurdering()

    data class KunneIkkeSimulere(val simuleringFeilet: SimuleringFeilet) : KunneIkkeBeregneOgSimulereRevurdering()
    object AvkortingErUfullstendig : KunneIkkeBeregneOgSimulereRevurdering()
    object OpphørAvYtelseSomSkalAvkortes : KunneIkkeBeregneOgSimulereRevurdering()
}

sealed class KunneIkkeForhåndsvarsle {
    object UgyldigTilstandsovergangForForhåndsvarsling : KunneIkkeForhåndsvarsle()
    object FantIkkeRevurdering : KunneIkkeForhåndsvarsle()
    object FantIkkePerson : KunneIkkeForhåndsvarsle()
    object KunneIkkeOppdatereOppgave : KunneIkkeForhåndsvarsle()
    object KunneIkkeHenteNavnForSaksbehandler : KunneIkkeForhåndsvarsle()
    data class MåVæreITilstandenSimulert(val fra: KClass<out Revurdering>) : KunneIkkeForhåndsvarsle()
    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : KunneIkkeForhåndsvarsle()
    object KunneIkkeGenerereDokument : KunneIkkeForhåndsvarsle()
}

sealed class KunneIkkeSendeRevurderingTilAttestering {
    object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering()
    object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering()
    object KanIkkeRegulereGrunnbeløpTilOpphør : KunneIkkeSendeRevurderingTilAttestering()
    object ForhåndsvarslingErIkkeFerdigbehandlet : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeSendeRevurderingTilAttestering()

    object ManglerBeslutningPåForhåndsvarsel : KunneIkkeSendeRevurderingTilAttestering()
    object FeilutbetalingStøttesIkke : KunneIkkeSendeRevurderingTilAttestering()
    data class RevurderingsutfallStøttesIkke(val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes>) :
        KunneIkkeSendeRevurderingTilAttestering()

    object TilbakekrevingsbehandlingErIkkeFullstendig : KunneIkkeSendeRevurderingTilAttestering()
    data class SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
        val revurderingId: UUID,
    ) : KunneIkkeSendeRevurderingTilAttestering()
}

sealed interface KunneIkkeIverksetteRevurdering {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering
    data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteRevurdering
    object IngenEndringErIkkeGyldig : KunneIkkeIverksetteRevurdering
    object FantIkkeRevurdering : KunneIkkeIverksetteRevurdering
    object LagringFeilet : KunneIkkeIverksetteRevurdering
    object HarAlleredeBlittAvkortetAvEnAnnen : KunneIkkeIverksetteRevurdering
    object KunneIkkeAnnulereKontrollsamtale : KunneIkkeIverksetteRevurdering

    data class UgyldigTilstand(
        val fra: KClass<out AbstraktRevurdering>,
        val til: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteRevurdering
}

sealed class KunneIkkeLageBrevutkastForRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForRevurdering()
    object DetSkalIkkeSendesBrev : KunneIkkeLageBrevutkastForRevurdering()
}

sealed class KunneIkkeHentePersonEllerSaksbehandlerNavn {
    object FantIkkePerson : KunneIkkeHentePersonEllerSaksbehandlerNavn()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeHentePersonEllerSaksbehandlerNavn()
}

sealed class KunneIkkeUnderkjenneRevurdering {
    object FantIkkeRevurdering : KunneIkkeUnderkjenneRevurdering()
    object FantIkkeAktørId : KunneIkkeUnderkjenneRevurdering()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeUnderkjenneRevurdering()

    object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneRevurdering()
    object SaksbehandlerOgAttestantKanIkkeVæreSammePerson : KunneIkkeUnderkjenneRevurdering()
}

sealed class KunneIkkeLeggeTilUføreVilkår {
    object FantIkkeBehandling : KunneIkkeLeggeTilUføreVilkår()
    object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : KunneIkkeLeggeTilUføreVilkår()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilUføreVilkår()

    data class UgyldigInput(
        val originalFeil: LeggTilUførevurderingerRequest.UgyldigUførevurdering,
    ) : KunneIkkeLeggeTilUføreVilkår()
}

sealed class KunneIkkeLeggeTilFradragsgrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilFradragsgrunnlag()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilFradragsgrunnlag()

    data class KunneIkkeEndreFradragsgrunnlag(val feil: KunneIkkeLageGrunnlagsdata) :
        KunneIkkeLeggeTilFradragsgrunnlag()
}

sealed class KunneIkkeLeggeTilUtenlandsopphold {
    object FantIkkeBehandling : KunneIkkeLeggeTilUtenlandsopphold()
    object OverlappendeVurderingsperioder : KunneIkkeLeggeTilUtenlandsopphold()
    object PeriodeForGrunnlagOgVurderingErForskjellig : KunneIkkeLeggeTilUtenlandsopphold()
    object AlleVurderingsperioderMåHaSammeResultat : KunneIkkeLeggeTilUtenlandsopphold()
    object MåVurdereHelePerioden : KunneIkkeLeggeTilUtenlandsopphold()
    object VurderingsperiodeUtenforBehandlingsperiode : KunneIkkeLeggeTilUtenlandsopphold()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilUtenlandsopphold()
}

sealed class KunneIkkeLeggeTilBosituasjongrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjongrunnlag()
    object UgyldigData : KunneIkkeLeggeTilBosituasjongrunnlag()
    object KunneIkkeSlåOppEPS : KunneIkkeLeggeTilBosituasjongrunnlag()
    object EpsAlderErNull : KunneIkkeLeggeTilBosituasjongrunnlag()
    data class Konsistenssjekk(val feil: Konsistensproblem.Bosituasjon) : KunneIkkeLeggeTilBosituasjongrunnlag()
    data class KunneIkkeLeggeTilBosituasjon(val feil: Revurdering.KunneIkkeLeggeTilBosituasjon) :
        KunneIkkeLeggeTilBosituasjongrunnlag()
}

sealed class KunneIkkeLeggeTilFormuegrunnlag {
    object FantIkkeRevurdering : KunneIkkeLeggeTilFormuegrunnlag()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilFormuegrunnlag()

    data class Konsistenssjekk(val feil: Konsistensproblem.BosituasjonOgFormue) : KunneIkkeLeggeTilFormuegrunnlag()

    data class KunneIkkeMappeTilDomenet(
        val feil: LeggTilFormuevilkårRequest.KunneIkkeMappeTilDomenet,
    ) : KunneIkkeLeggeTilFormuegrunnlag()
}

sealed class KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger {
    object FantIkkeBehandling : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
    object FantIkkeSak : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
    object FantIngentingSomKanRevurderes : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) :
        KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
}

sealed class StansYtelseRequest {
    abstract val sakId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val fraOgMed: LocalDate
    abstract val revurderingsårsak: Revurderingsårsak

    data class Opprett(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val fraOgMed: LocalDate,
        override val revurderingsårsak: Revurderingsårsak,
    ) : StansYtelseRequest()

    data class Oppdater(
        override val sakId: UUID,
        val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val fraOgMed: LocalDate,
        override val revurderingsårsak: Revurderingsårsak,
    ) : StansYtelseRequest()
}

sealed class KunneIkkeStanseYtelse {
    object FantIkkeRevurdering : KunneIkkeStanseYtelse()
    object FantIkkeSak : KunneIkkeStanseYtelse()
    object SakHarÅpenRevurderingForStansAvYtelse : KunneIkkeStanseYtelse()
    data class SimuleringAvStansFeilet(val feil: SimulerStansFeilet) : KunneIkkeStanseYtelse()
    object KunneIkkeOppretteRevurdering : KunneIkkeStanseYtelse()
    data class UgyldigTypeForOppdatering(val type: KClass<out AbstraktRevurdering>) : KunneIkkeStanseYtelse()
}

sealed class KunneIkkeIverksetteStansYtelse {
    data class KunneIkkeUtbetale(val feil: UtbetalStansFeil) : KunneIkkeIverksetteStansYtelse()
    object FantIkkeRevurdering : KunneIkkeIverksetteStansYtelse()
    data class UgyldigTilstand(
        val faktiskTilstand: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteStansYtelse() {
        val målTilstand: KClass<out StansAvYtelseRevurdering.IverksattStansAvYtelse> =
            StansAvYtelseRevurdering.IverksattStansAvYtelse::class
    }

    object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteStansYtelse()
}

sealed class GjenopptaYtelseRequest {
    abstract val sakId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val revurderingsårsak: Revurderingsårsak

    data class Opprett(
        override val sakId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRequest()

    data class Oppdater(
        override val sakId: UUID,
        val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRequest()
}

sealed class KunneIkkeGjenopptaYtelse {
    object FantIkkeRevurdering : KunneIkkeGjenopptaYtelse()
    object FantIngenVedtak : KunneIkkeGjenopptaYtelse()
    object FantIkkeSak : KunneIkkeGjenopptaYtelse()
    object SakHarÅpenRevurderingForGjenopptakAvYtelse : KunneIkkeGjenopptaYtelse()
    data class KunneIkkeSimulere(val feil: SimulerGjenopptakFeil) : KunneIkkeGjenopptaYtelse()
    object KunneIkkeOppretteRevurdering : KunneIkkeGjenopptaYtelse()
    data class UgyldigTypeForOppdatering(val type: KClass<out AbstraktRevurdering>) : KunneIkkeGjenopptaYtelse()
    object SisteVedtakErIkkeStans : KunneIkkeGjenopptaYtelse()
}

sealed class KunneIkkeIverksetteGjenopptakAvYtelse {
    data class KunneIkkeUtbetale(val feil: UtbetalGjenopptakFeil) : KunneIkkeIverksetteGjenopptakAvYtelse()
    object FantIkkeRevurdering : KunneIkkeIverksetteGjenopptakAvYtelse()
    data class UgyldigTilstand(
        val faktiskTilstand: KClass<out AbstraktRevurdering>,
    ) : KunneIkkeIverksetteGjenopptakAvYtelse() {
        val målTilstand: KClass<out GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse> =
            GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse::class
    }

    object SimuleringIndikererFeilutbetaling : KunneIkkeIverksetteGjenopptakAvYtelse()
}

sealed class KunneIkkeLageBrevutkastForAvsluttingAvRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object RevurderingenErIkkeForhåndsvarslet : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeGenererePDF : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
    object DetSkalIkkeSendesBrev : KunneIkkeLageBrevutkastForAvsluttingAvRevurdering()
}

data class LeggTilBosituasjonerRequest(
    val revurderingId: UUID,
    val bosituasjoner: List<LeggTilBosituasjonRequest>,
) {
    fun toDomain(
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, List<Grunnlag.Bosituasjon.Fullstendig>> {
        return bosituasjoner.map {
            it.toDomain(
                clock = clock,
                hentPerson = hentPerson,
            )
        }.sequence()
    }
}

data class LeggTilBosituasjonRequest(
    val periode: Periode,
    val epsFnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
) {
    fun toDomain(
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, Grunnlag.Bosituasjon.Fullstendig> {
        val log = LoggerFactory.getLogger(this::class.java)

        if ((epsFnr == null && delerBolig == null) || (epsFnr != null && delerBolig != null)) {
            return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
        }

        if (epsFnr != null) {
            val eps = hentPerson(Fnr(epsFnr)).getOrHandle {
                return KunneIkkeLeggeTilBosituasjongrunnlag.KunneIkkeSlåOppEPS.left()
            }

            val epsAlder = if (eps.getAlder(LocalDate.now(clock)) == null) {
                log.error("Alder på EPS er null. Denne har i tidligere PDL kall hatt en verdi")
                return KunneIkkeLeggeTilBosituasjongrunnlag.EpsAlderErNull.left()
            } else eps.getAlder(LocalDate.now(clock))!!

            return when {
                epsAlder >= 67 -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                    fnr = eps.ident.fnr,
                ).right()
                else -> when (ektemakeEllerSamboerUførFlyktning) {
                    true -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = eps.ident.fnr,
                    ).right()
                    false -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = eps.ident.fnr,
                    ).right()
                    null -> return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
                }
            }
        }

        if (delerBolig != null) {
            return when (delerBolig) {
                true -> Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                ).right()
                false -> Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                ).right()
            }
        }

        return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
    }
}

sealed interface KunneIkkeOppdatereTilbakekrevingsbehandling {
    object FantIkkeRevurdering : KunneIkkeOppdatereTilbakekrevingsbehandling
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering> = SimulertRevurdering::class,
    ) : KunneIkkeOppdatereTilbakekrevingsbehandling
}

data class OppdaterTilbakekrevingsbehandlingRequest(
    val revurderingId: UUID,
    val avgjørelse: Avgjørelse,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    enum class Avgjørelse {
        TILBAKEKREV,
        IKKE_TILBAKEKREV
    }
}
