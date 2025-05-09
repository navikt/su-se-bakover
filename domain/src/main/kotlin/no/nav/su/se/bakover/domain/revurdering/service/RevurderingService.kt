package no.nav.su.se.bakover.domain.revurdering.service

import arrow.core.Either
import behandling.domain.fradrag.LeggTilFradragsgrunnlagRequest
import behandling.revurdering.domain.bosituasjon.KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering
import behandling.revurdering.domain.bosituasjon.LeggTilBosituasjonerForRevurderingCommand
import dokument.domain.brev.Brevvalg
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.revurdering.AbstraktRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeAvslutteRevurdering
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeLeggeTilVedtaksbrevvalg
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.revurdering.attestering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.attestering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeForhåndsvarsle
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForAvsluttingAvRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.LeggTilBrevvalgRequest
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.underkjenn.KunneIkkeUnderkjenneRevurdering
import no.nav.su.se.bakover.domain.revurdering.varsel.Varselmelding
import no.nav.su.se.bakover.domain.revurdering.vilkår.formue.KunneIkkeLeggeTilFormuegrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.fradag.KunneIkkeLeggeTilFradragsgrunnlag
import no.nav.su.se.bakover.domain.revurdering.vilkår.uføre.KunneIkkeLeggeTilUføreVilkår
import no.nav.su.se.bakover.domain.revurdering.vilkår.utenlandsopphold.KunneIkkeLeggeTilUtenlandsopphold
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.domain.vilkår.fastopphold.KunneIkkeLeggeFastOppholdINorgeVilkår
import no.nav.su.se.bakover.domain.vilkår.fastopphold.LeggTilFastOppholdINorgeRequest
import no.nav.su.se.bakover.domain.vilkår.flyktning.KunneIkkeLeggeTilFlyktningVilkår
import no.nav.su.se.bakover.domain.vilkår.flyktning.LeggTilFlyktningVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.formue.LeggTilFormuevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.KunneIkkeLeggeTilInstitusjonsoppholdVilkår
import no.nav.su.se.bakover.domain.vilkår.institusjonsopphold.LeggTilInstitusjonsoppholdVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.KunneIkkeLeggetilLovligOppholdVilkårForRevurdering
import no.nav.su.se.bakover.domain.vilkår.lovligopphold.LeggTilLovligOppholdRequest
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.KunneIkkeLeggeTilOpplysningsplikt
import no.nav.su.se.bakover.domain.vilkår.opplysningsplikt.LeggTilOpplysningspliktRequest
import no.nav.su.se.bakover.domain.vilkår.oppmøte.KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering
import no.nav.su.se.bakover.domain.vilkår.oppmøte.LeggTilPersonligOppmøteVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.pensjon.KunneIkkeLeggeTilPensjonsVilkår
import no.nav.su.se.bakover.domain.vilkår.pensjon.LeggTilPensjonsVilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.utenlandsopphold.LeggTilFlereUtenlandsoppholdRequest

interface RevurderingService {
    fun hentRevurdering(revurderingId: RevurderingId): AbstraktRevurdering?

    fun opprettRevurdering(
        command: OpprettRevurderingCommand,
    ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering>

    fun oppdaterRevurdering(
        command: OppdaterRevurderingCommand,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering>

    fun beregnOgSimuler(
        revurderingId: RevurderingId,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse>

    fun lagreOgSendForhåndsvarsel(
        revurderingId: RevurderingId,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering>

    fun lagBrevutkastForForhåndsvarsling(
        revurderingId: RevurderingId,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, PdfA>

    fun sendTilAttestering(
        request: SendTilAttesteringRequest,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering>

    fun leggTilBrevvalg(
        request: LeggTilBrevvalgRequest,
    ): Either<KunneIkkeLeggeTilVedtaksbrevvalg, Revurdering>

    fun lagBrevutkastForRevurdering(
        revurderingId: RevurderingId,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, PdfA>

    fun iverksett(
        revurderingId: RevurderingId,
        attestant: NavIdentBruker.Attestant,
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering>

    fun underkjenn(
        revurderingId: RevurderingId,
        attestering: Attestering.Underkjent,
    ): Either<KunneIkkeUnderkjenneRevurdering, UnderkjentRevurdering>

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
        request: LeggTilBosituasjonerForRevurderingCommand,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlagForRevurdering, RevurderingOgFeilmeldingerResponse>

    fun leggTilFormuegrunnlag(
        request: LeggTilFormuevilkårRequest,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, RevurderingOgFeilmeldingerResponse>

    fun lagBrevutkastForAvslutting(
        revurderingId: RevurderingId,
        fritekst: String,
        avsluttetAv: NavIdentBruker,
    ): Either<KunneIkkeLageBrevutkastForAvsluttingAvRevurdering, Pair<Fnr, PdfA>>

    fun avsluttRevurdering(
        revurderingId: RevurderingId,
        begrunnelse: String,
        brevvalg: Brevvalg.SaksbehandlersValg?,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeAvslutteRevurdering, AbstraktRevurdering>

    fun leggTilOpplysningspliktVilkår(
        request: LeggTilOpplysningspliktRequest.Revurdering,
    ): Either<KunneIkkeLeggeTilOpplysningsplikt, RevurderingOgFeilmeldingerResponse>

    fun leggTilFamiliegjenforeningvilkår(
        request: LeggTilFamiliegjenforeningRequest,
    ): Either<Revurdering.KunneIkkeLeggeTilFamiliegjenforeningVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilPensjonsVilkår(
        request: LeggTilPensjonsVilkårRequest,
    ): Either<KunneIkkeLeggeTilPensjonsVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilLovligOppholdVilkår(
        request: LeggTilLovligOppholdRequest,
    ): Either<KunneIkkeLeggetilLovligOppholdVilkårForRevurdering, RevurderingOgFeilmeldingerResponse>

    fun leggTilFlyktningVilkår(
        request: LeggTilFlyktningVilkårRequest,
    ): Either<KunneIkkeLeggeTilFlyktningVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilFastOppholdINorgeVilkår(
        request: LeggTilFastOppholdINorgeRequest,
    ): Either<KunneIkkeLeggeFastOppholdINorgeVilkår, RevurderingOgFeilmeldingerResponse>

    fun leggTilPersonligOppmøteVilkår(
        request: LeggTilPersonligOppmøteVilkårRequest,
    ): Either<KunneIkkeLeggeTilPersonligOppmøteVilkårForRevurdering, RevurderingOgFeilmeldingerResponse>

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

sealed interface KunneIkkeHentePersonEllerSaksbehandlerNavn {
    data object FantIkkePerson : KunneIkkeHentePersonEllerSaksbehandlerNavn
    // data object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeHentePersonEllerSaksbehandlerNavn
}

sealed interface KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger {
    data object FantIkkeBehandling : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger
    data object FantIkkeSak : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger
}
