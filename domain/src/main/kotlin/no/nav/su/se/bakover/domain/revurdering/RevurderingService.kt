package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerGjenopptakFeil
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.revurdering.forhåndsvarsel.FortsettEtterForhåndsvarselFeil
import no.nav.su.se.bakover.domain.revurdering.forhåndsvarsel.FortsettEtterForhåndsvarslingRequest
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingRequest
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.flyktning.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkår
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest
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

    /**
     * Konsument er ansvarlig for [transactionContext] og tilhørende commit/rollback. Dette innebærer også at
     * konsument må kalle aktuelle callbacks som returneres på et fornuftig tidspunkt.
     *
     * @throws IverksettStansAvYtelseTransactionException for alle feilsituasjoner vi selv har rådighet over.
     *
     * @return [StansAvYtelseITransaksjonResponse.revurdering] simulert revurdering for stans
     * @return [StansAvYtelseITransaksjonResponse.sendStatistikkCallback] callback som publiserer statistikk på kafka
     */
    fun stansAvYtelseITransaksjon(
        request: StansYtelseRequest,
        transactionContext: TransactionContext,
    ): StansAvYtelseITransaksjonResponse

    fun iverksettStansAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteStansYtelse, StansAvYtelseRevurdering.IverksattStansAvYtelse>

    /**
     * Konsument er ansvarlig for [transactionContext] og tilhørende commit/rollback. Dette innebærer også at
     * konsument må kalle aktuelle callbacks som returneres på et fornuftig tidspunkt.
     *
     * @throws IverksettStansAvYtelseTransactionException for alle feilsituasjoner vi selv har rådighet over.
     *
     * @return [IverksettStansAvYtelseITransaksjonResponse.revurdering] iverksatt revurdering for stans
     * @return [IverksettStansAvYtelseITransaksjonResponse.vedtak] vedtak for stans
     * @return [IverksettStansAvYtelseITransaksjonResponse.sendUtbetalingCallback] callback som publiserer utbetalinger på kø
     * @return [IverksettStansAvYtelseITransaksjonResponse.sendStatistikkCallback] callback som publiserer statistikk på kafka
     */
    fun iverksettStansAvYtelseITransaksjon(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
        transactionContext: TransactionContext,
    ): IverksettStansAvYtelseITransaksjonResponse

    fun gjenopptaYtelse(
        request: GjenopptaYtelseRequest,
    ): Either<KunneIkkeGjenopptaYtelse, GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse>

    fun iverksettGjenopptakAvYtelse(
        revurderingId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteGjenopptakAvYtelse, GjenopptaYtelseRevurdering.IverksattGjenopptakAvYtelse>

    fun opprettRevurdering(
        command: OpprettRevurderingCommand,
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
        saksbehandler: NavIdentBruker.Saksbehandler,
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
        request: LeggTilInstitusjonsoppholdVilkårRequest,
    ): Either<KunneIkkeLeggeTilInstitusjonsoppholdVilkår, RevurderingOgFeilmeldingerResponse>

    fun defaultTransactionContext(): TransactionContext
}

data class RevurderingOgFeilmeldingerResponse(
    val revurdering: Revurdering,
    val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes> = emptyList(),
    val varselmeldinger: List<Varselmelding> = emptyList(),
) {
    private fun leggTil(varselmelding: Varselmelding): RevurderingOgFeilmeldingerResponse {
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
    FORHÅNDSVARSLE,
    ;
}

sealed class KunneIkkeOppdatereRevurdering {
    object MåVelgeInformasjonSomSkalRevurderes : KunneIkkeOppdatereRevurdering()
    object UgyldigÅrsak : KunneIkkeOppdatereRevurdering()
    object UgyldigBegrunnelse : KunneIkkeOppdatereRevurdering()
    data class FeilVedOppdateringAvRevurdering(val feil: Sak.KunneIkkeOppdatereRevurdering) : KunneIkkeOppdatereRevurdering()
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

    data class KunneIkkeSimulere(val simuleringFeilet: SimulerUtbetalingFeilet) : KunneIkkeBeregneOgSimulereRevurdering()
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
    object SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving : KunneIkkeIverksetteRevurdering

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
    object SakHarÅpenBehandling : KunneIkkeStanseYtelse()
    data class SimuleringAvStansFeilet(val feil: SimulerStansFeilet) : KunneIkkeStanseYtelse()
    object KunneIkkeOppretteRevurdering : KunneIkkeStanseYtelse()
    data class UgyldigTypeForOppdatering(val type: KClass<out AbstraktRevurdering>) : KunneIkkeStanseYtelse()

    data class UkjentFeil(val msg: String) : KunneIkkeStanseYtelse()
}

data class StansAvYtelseITransaksjonResponse(
    val revurdering: StansAvYtelseRevurdering.SimulertStansAvYtelse,
    val sendStatistikkCallback: () -> Unit,
)

data class StansAvYtelseTransactionException(
    override val message: String,
    val feil: KunneIkkeStanseYtelse,
) : RuntimeException(message) {
    companion object {
        fun KunneIkkeStanseYtelse.exception(): StansAvYtelseTransactionException {
            return when (this) {
                KunneIkkeStanseYtelse.FantIkkeRevurdering -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                KunneIkkeStanseYtelse.FantIkkeSak -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                KunneIkkeStanseYtelse.SakHarÅpenBehandling -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                is KunneIkkeStanseYtelse.SimuleringAvStansFeilet -> {
                    StansAvYtelseTransactionException(this.feil::class.java.toString(), this)
                }
                is KunneIkkeStanseYtelse.UgyldigTypeForOppdatering -> {
                    StansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                is KunneIkkeStanseYtelse.UkjentFeil -> {
                    StansAvYtelseTransactionException(this.msg, this)
                }
            }
        }
    }
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
    data class UkjentFeil(val msg: String) : KunneIkkeIverksetteStansYtelse()
}

data class IverksettStansAvYtelseITransaksjonResponse(
    val revurdering: StansAvYtelseRevurdering.IverksattStansAvYtelse,
    val vedtak: VedtakSomKanRevurderes.EndringIYtelse.StansAvYtelse,
    val sendUtbetalingCallback: () -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>,
    val sendStatistikkCallback: () -> Unit,
)

data class IverksettStansAvYtelseTransactionException(
    override val message: String,
    val feil: KunneIkkeIverksetteStansYtelse,
) : RuntimeException(message) {
    companion object {
        fun KunneIkkeIverksetteStansYtelse.exception(): IverksettStansAvYtelseTransactionException {
            return when (this) {
                KunneIkkeIverksetteStansYtelse.FantIkkeRevurdering -> {
                    IverksettStansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                is KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale -> {
                    IverksettStansAvYtelseTransactionException(this.feil::class.java.toString(), this)
                }
                KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling -> {
                    IverksettStansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                is KunneIkkeIverksetteStansYtelse.UgyldigTilstand -> {
                    IverksettStansAvYtelseTransactionException(this::class.java.toString(), this)
                }
                is KunneIkkeIverksetteStansYtelse.UkjentFeil -> {
                    IverksettStansAvYtelseTransactionException(this.msg, this)
                }
            }
        }
    }
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
    object SakHarÅpenBehandling : KunneIkkeGjenopptaYtelse()
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
    object LagringFeilet : KunneIkkeIverksetteGjenopptakAvYtelse()
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
            } else {
                eps.getAlder(LocalDate.now(clock))!!
            }

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
        IKKE_TILBAKEKREV,
    }
}
