package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Konsistensproblem
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.fradrag.LeggTilFradragsgrunnlagRequest
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.revurdering.oppdater.KunneIkkeOppdatereRevurdering
import no.nav.su.se.bakover.domain.revurdering.oppdater.OppdaterRevurderingCommand
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.opprett.KunneIkkeOppretteRevurdering
import no.nav.su.se.bakover.domain.revurdering.opprett.OpprettRevurderingCommand
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
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

    fun opprettRevurdering(
        command: OpprettRevurderingCommand,
    ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering>

    fun oppdaterRevurdering(
        command: OppdaterRevurderingCommand,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, RevurderingOgFeilmeldingerResponse>

    fun lagreOgSendForhåndsvarsel(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
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
    ): Either<KunneIkkeOppdatereTilbakekrevingsbehandling, Revurdering>

    fun leggTilBrevvalg(
        request: LeggTilBrevvalgRequest,
    ): Either<KunneIkkeLeggeTilBrevvalg, Revurdering>

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
)

sealed class KunneIkkeBeregneOgSimulereRevurdering {
    object FantIkkeRevurdering : KunneIkkeBeregneOgSimulereRevurdering()
    object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeBeregneOgSimulereRevurdering()

    data class UgyldigBeregningsgrunnlag(
        val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
    ) : KunneIkkeBeregneOgSimulereRevurdering()

    object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneOgSimulereRevurdering()

    data class KunneIkkeSimulere(val simuleringFeilet: SimulerUtbetalingFeilet) :
        KunneIkkeBeregneOgSimulereRevurdering()

    object AvkortingErUfullstendig : KunneIkkeBeregneOgSimulereRevurdering()
    object OpphørAvYtelseSomSkalAvkortes : KunneIkkeBeregneOgSimulereRevurdering()
}

sealed class KunneIkkeForhåndsvarsle {
    object FantIkkeRevurdering : KunneIkkeForhåndsvarsle()
    object FantIkkePerson : KunneIkkeForhåndsvarsle()
    object KunneIkkeOppdatereOppgave : KunneIkkeForhåndsvarsle()
    object KunneIkkeHenteNavnForSaksbehandler : KunneIkkeForhåndsvarsle()
    object UgyldigTilstand : KunneIkkeForhåndsvarsle()
    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : KunneIkkeForhåndsvarsle()
    object KunneIkkeGenerereDokument : KunneIkkeForhåndsvarsle()
}

sealed class KunneIkkeSendeRevurderingTilAttestering {
    data class FeilInnvilget(val feil: SimulertRevurdering.KunneIkkeSendeInnvilgetRevurderingTilAttestering) :
        KunneIkkeSendeRevurderingTilAttestering()

    data class FeilOpphørt(val feil: SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering) :
        KunneIkkeSendeRevurderingTilAttestering()

    object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering()
    object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering()
    object KanIkkeRegulereGrunnbeløpTilOpphør : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeSendeRevurderingTilAttestering()

    object FeilutbetalingStøttesIkke : KunneIkkeSendeRevurderingTilAttestering()
    data class RevurderingsutfallStøttesIkke(val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes>) :
        KunneIkkeSendeRevurderingTilAttestering()

    data class SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
        val revurderingId: UUID,
    ) : KunneIkkeSendeRevurderingTilAttestering()
}

sealed interface KunneIkkeIverksetteRevurdering {
    data class IverksettelsestransaksjonFeilet(val feil: KunneIkkeFerdigstilleIverksettelsestransaksjon) :
        KunneIkkeIverksetteRevurdering

    data class FeilVedIverksettelse(val feil: no.nav.su.se.bakover.domain.sak.iverksett.KunneIkkeIverksetteRevurdering) :
        KunneIkkeIverksetteRevurdering
}

sealed class KunneIkkeLageBrevutkastForRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForRevurdering()
    object DetSkalIkkeSendesBrev : KunneIkkeLageBrevutkastForRevurdering()
    object UgyldigTilstand : KunneIkkeLageBrevutkastForRevurdering()
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
            val eps = hentPerson(Fnr(epsFnr)).getOrElse {
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

sealed interface KunneIkkeLeggeTilBrevvalg {

    object FantIkkeRevurdering : KunneIkkeLeggeTilBrevvalg
    data class Feil(val feil: Revurdering.KunneIkkeLeggeTilBrevvalg) : KunneIkkeLeggeTilBrevvalg
}

data class LeggTilBrevvalgRequest(
    val revurderingId: UUID,
    val valg: Valg,
    val fritekst: String?,
    val begrunnelse: String?,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) {
    enum class Valg {
        SEND,
        IKKE_SEND,
        ;
    }

    fun toDomain(): BrevvalgRevurdering {
        return when (valg) {
            Valg.SEND -> {
                BrevvalgRevurdering.Valgt.SendBrev(
                    fritekst = fritekst,
                    begrunnelse = begrunnelse,
                    bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }

            Valg.IKKE_SEND -> {
                BrevvalgRevurdering.Valgt.IkkeSendBrev(
                    begrunnelse = begrunnelse,
                    bestemtAv = BrevvalgRevurdering.BestemtAv.Behandler(saksbehandler.navIdent),
                )
            }
        }
    }
}
