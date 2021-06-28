package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.vilkår.LeggTilUførevurderingerRequest
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

interface RevurderingService {
    fun hentRevurdering(revurderingId: UUID): Revurdering?

    fun opprettRevurdering(
        opprettRevurderingRequest: OpprettRevurderingRequest,
    ): Either<KunneIkkeOppretteRevurdering, OpprettetRevurdering>

    fun oppdaterRevurdering(
        oppdaterRevurderingRequest: OppdaterRevurderingRequest,
    ): Either<KunneIkkeOppdatereRevurdering, OpprettetRevurdering>

    fun beregnOgSimuler(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
    ): Either<KunneIkkeBeregneOgSimulereRevurdering, BeregnOgSimulerResponse>

    fun forhåndsvarsleEllerSendTilAttestering(
        revurderingId: UUID,
        saksbehandler: NavIdentBruker.Saksbehandler,
        revurderingshandling: Revurderingshandling,
        fritekst: String,
    ): Either<KunneIkkeForhåndsvarsle, Revurdering>

    fun lagBrevutkastForForhåndsvarsling(
        revurderingId: UUID,
        fritekst: String,
    ): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>

    fun sendTilAttestering(
        request: SendTilAttesteringRequest,
    ): Either<KunneIkkeSendeRevurderingTilAttestering, Revurdering>

    fun lagBrevutkast(revurderingId: UUID, fritekst: String): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>
    fun hentBrevutkast(revurderingId: UUID): Either<KunneIkkeLageBrevutkastForRevurdering, ByteArray>
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

    fun leggTilUføregrunnlag(
        request: LeggTilUførevurderingerRequest,
    ): Either<KunneIkkeLeggeTilGrunnlag, LeggTilUføregrunnlagResponse>

    fun leggTilFradragsgrunnlag(
        request: LeggTilFradragsgrunnlagRequest,
    ): Either<KunneIkkeLeggeTilFradragsgrunnlag, LeggTilFradragsgrunnlagResponse>

    fun leggTilBosituasjongrunnlag(
        request: LeggTilBosituasjongrunnlagRequest,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, LeggTilBosituasjongrunnlagResponse>

    fun leggTilFormuegrunnlag(
        request: LeggTilFormuegrunnlagRequest,
    ): Either<KunneIkkeLeggeTilFormuegrunnlag, Revurdering>

    fun hentGjeldendeGrunnlagsdataOgVilkårsvurderinger(
        revurderingId: UUID,
    ): Either<KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger, HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse>
}

data class BeregnOgSimulerResponse(
    val revurdering: Revurdering,
    val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes> = emptyList(),
)

sealed class FortsettEtterForhåndsvarslingRequest {
    abstract val revurderingId: UUID
    abstract val saksbehandler: NavIdentBruker.Saksbehandler
    abstract val begrunnelse: String

    data class FortsettMedSammeOpplysninger(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
        val fritekstTilBrev: String,
    ) : FortsettEtterForhåndsvarslingRequest()

    data class FortsettMedAndreOpplysninger(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
    ) : FortsettEtterForhåndsvarslingRequest()

    data class AvsluttUtenEndringer(
        override val revurderingId: UUID,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val begrunnelse: String,
        val fritekstTilBrev: String,
    ) : FortsettEtterForhåndsvarslingRequest()
}

sealed class FortsettEtterForhåndsvarselFeil {
    object FantIkkeRevurdering : FortsettEtterForhåndsvarselFeil()
    object RevurderingErIkkeIRiktigTilstand : FortsettEtterForhåndsvarselFeil()
    object RevurderingErIkkeForhåndsvarslet : FortsettEtterForhåndsvarselFeil()
    object AlleredeBesluttet : FortsettEtterForhåndsvarselFeil()
    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : FortsettEtterForhåndsvarselFeil()
}

object FantIkkeRevurdering

data class SendTilAttesteringRequest(
    val revurderingId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String,
    val skalFøreTilBrevutsending: Boolean,
)

enum class Revurderingshandling {
    SEND_TIL_ATTESTERING,
    FORHÅNDSVARSLE,
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
    object BosituasjonMedFlerePerioderMåRevurderes : KunneIkkeOppretteRevurdering()
    object EpsInntektMedFlereBosituasjonsperioderMåRevurderes : KunneIkkeOppretteRevurdering()
    object FormueSomFørerTilOpphørMåRevurderes : KunneIkkeOppretteRevurdering()
    object EpsFormueMedFlereBosituasjonsperioderMåRevurderes : KunneIkkeOppretteRevurdering()
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
    object BosituasjonMedFlerePerioderMåRevurderes : KunneIkkeOppdatereRevurdering()
    object EpsInntektMedFlereBosituasjonsperioderMåRevurderes : KunneIkkeOppdatereRevurdering()
    object FormueSomFørerTilOpphørMåRevurderes : KunneIkkeOppdatereRevurdering()
}

sealed class KunneIkkeBeregneOgSimulereRevurdering {
    object MåSendeGrunnbeløpReguleringSomÅrsakSammenMedForventetInntekt : KunneIkkeBeregneOgSimulereRevurdering()
    object FantIkkeRevurdering : KunneIkkeBeregneOgSimulereRevurdering()
    object SimuleringFeilet : KunneIkkeBeregneOgSimulereRevurdering()
    object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeBeregneOgSimulereRevurdering()

    object UfullstendigBehandlingsinformasjon : KunneIkkeBeregneOgSimulereRevurdering()
    data class UgyldigBeregningsgrunnlag(
        val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
    ) : KunneIkkeBeregneOgSimulereRevurdering()

    object UfullstendigVilkårsvurdering : KunneIkkeBeregneOgSimulereRevurdering()
    object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneOgSimulereRevurdering()
}

sealed class KunneIkkeForhåndsvarsle {
    object AlleredeForhåndsvarslet : KunneIkkeForhåndsvarsle()
    object FantIkkeRevurdering : KunneIkkeForhåndsvarsle()
    object FantIkkeAktørId : KunneIkkeForhåndsvarsle()
    object FantIkkePerson : KunneIkkeForhåndsvarsle()
    object KunneIkkeJournalføre : KunneIkkeForhåndsvarsle()
    object KunneIkkeDistribuere : KunneIkkeForhåndsvarsle()
    object KunneIkkeOppretteOppgave : KunneIkkeForhåndsvarsle()
    object KunneIkkeHenteNavnForSaksbehandler : KunneIkkeForhåndsvarsle()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeForhåndsvarsle()

    data class Attestering(val subError: KunneIkkeSendeRevurderingTilAttestering) : KunneIkkeForhåndsvarsle()
}

sealed class KunneIkkeSendeRevurderingTilAttestering {
    object FantIkkeRevurdering : KunneIkkeSendeRevurderingTilAttestering()
    object FantIkkeAktørId : KunneIkkeSendeRevurderingTilAttestering()
    object KunneIkkeOppretteOppgave : KunneIkkeSendeRevurderingTilAttestering()
    object KanIkkeRegulereGrunnbeløpTilOpphør : KunneIkkeSendeRevurderingTilAttestering()
    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
        KunneIkkeSendeRevurderingTilAttestering()

    object ManglerBeslutningPåForhåndsvarsel : KunneIkkeSendeRevurderingTilAttestering()
    object FeilutbetalingStøttesIkke : KunneIkkeSendeRevurderingTilAttestering()
    data class RevurderingsutfallStøttesIkke(val feilmeldinger: List<RevurderingsutfallSomIkkeStøttes>) : KunneIkkeSendeRevurderingTilAttestering()
}

sealed class KunneIkkeIverksetteRevurdering {
    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering()
    object KunneIkkeUtbetale : KunneIkkeIverksetteRevurdering()
    object KunneIkkeKontrollsimulere : KunneIkkeIverksetteRevurdering()
    object KunneIkkeJournaleføreBrev : KunneIkkeIverksetteRevurdering()
    object KunneIkkeDistribuereBrev : KunneIkkeIverksetteRevurdering()
    object FantIkkeRevurdering : KunneIkkeIverksetteRevurdering()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeIverksetteRevurdering()
}

sealed class KunneIkkeLageBrevutkastForRevurdering {
    object FantIkkeRevurdering : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeLageBrevutkast : KunneIkkeLageBrevutkastForRevurdering()
    object FantIkkePerson : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeLageBrevutkastForRevurdering()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeLageBrevutkastForRevurdering()
}

sealed class KunneIkkeUnderkjenneRevurdering {
    object FantIkkeRevurdering : KunneIkkeUnderkjenneRevurdering()
    object FantIkkeAktørId : KunneIkkeUnderkjenneRevurdering()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeUnderkjenneRevurdering()

    object KunneIkkeOppretteOppgave : KunneIkkeUnderkjenneRevurdering()
}

sealed class KunneIkkeLeggeTilGrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilGrunnlag()
    object UgyldigStatus : KunneIkkeLeggeTilGrunnlag()
    object UføregradOgForventetInntektMangler : KunneIkkeLeggeTilGrunnlag()
    object PeriodeForGrunnlagOgVurderingErForskjellig : KunneIkkeLeggeTilGrunnlag()
    object OverlappendeVurderingsperioder : KunneIkkeLeggeTilGrunnlag()
    object VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden : KunneIkkeLeggeTilGrunnlag()
    object AlleVurderingeneMåHaSammeResultat : KunneIkkeLeggeTilGrunnlag()
    object HeleBehandlingsperiodenMåHaVurderinger : KunneIkkeLeggeTilGrunnlag()
}

sealed class KunneIkkeLeggeTilFradragsgrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilFradragsgrunnlag()
    object UgyldigStatus : KunneIkkeLeggeTilFradragsgrunnlag()
    object FradragsgrunnlagUtenforRevurderingsperiode : KunneIkkeLeggeTilFradragsgrunnlag()
    object UgyldigFradragstypeForGrunnlag : KunneIkkeLeggeTilFradragsgrunnlag()
    object HarIkkeEktelle : KunneIkkeLeggeTilFradragsgrunnlag()
}

sealed class KunneIkkeLeggeTilBosituasjongrunnlag {
    object FantIkkeBehandling : KunneIkkeLeggeTilBosituasjongrunnlag()
    object UgyldigData : KunneIkkeLeggeTilBosituasjongrunnlag()
    object KunneIkkeSlåOppEPS : KunneIkkeLeggeTilBosituasjongrunnlag()
    object EpsAlderErNull : KunneIkkeLeggeTilBosituasjongrunnlag()
}

sealed class KunneIkkeLeggeTilFormuegrunnlag {
    object FantIkkeRevurdering : KunneIkkeLeggeTilFormuegrunnlag()
    object IkkeLovMedOverlappendePerioder : KunneIkkeLeggeTilFormuegrunnlag()
    object EpsFormueperiodeErUtenforBosituasjonPeriode : KunneIkkeLeggeTilFormuegrunnlag()
    object MåHaEpsHvisManHarSattEpsFormue : KunneIkkeLeggeTilFormuegrunnlag()
    object FormuePeriodeErUtenforBehandlingsperioden : KunneIkkeLeggeTilFormuegrunnlag()
    data class UgyldigTilstand(
        val fra: KClass<out Revurdering>,
        val til: KClass<out Revurdering>,
    ) : KunneIkkeLeggeTilFormuegrunnlag()
}

sealed class KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger {
    object FantIkkeBehandling : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
    object FantIkkeSak : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
    object FantIngentingSomKanRevurderes : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
    data class UgyldigPeriode(val subError: Periode.UgyldigPeriode) : KunneIkkeHenteGjeldendeGrunnlagsdataOgVilkårsvurderinger()
}

data class HentGjeldendeGrunnlagsdataOgVilkårsvurderingerResponse(
    val grunnlagsdata: Grunnlagsdata,
    val vilkårsvurderinger: Vilkårsvurderinger,
)

data class LeggTilUføregrunnlagResponse(
    val revurdering: Revurdering,
)

data class LeggTilFradragsgrunnlagResponse(
    val revurdering: Revurdering,
)

data class LeggTilBosituasjongrunnlagResponse(
    val revurdering: Revurdering,
)

data class LeggTilFradragsgrunnlagRequest(
    val behandlingId: UUID,
    val fradragsrunnlag: List<Grunnlag.Fradragsgrunnlag>,
)

data class LeggTilBosituasjongrunnlagRequest(
    val revurderingId: UUID,
    val epsFnr: String?,
    val delerBolig: Boolean?,
    val ektemakeEllerSamboerUførFlyktning: Boolean?,
    val begrunnelse: String?,
) {
    fun toDomain(
        periode: Periode,
        clock: Clock,
        hentPerson: (fnr: Fnr) -> Either<KunneIkkeHentePerson, Person>,
    ): Either<KunneIkkeLeggeTilBosituasjongrunnlag, Grunnlag.Bosituasjon.Fullstendig> {
        val log = LoggerFactory.getLogger(this::class.java)

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
                    begrunnelse = begrunnelse,
                ).right()
                else -> when (ektemakeEllerSamboerUførFlyktning) {
                    true -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = eps.ident.fnr,
                        begrunnelse = begrunnelse,
                    ).right()
                    false -> Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(clock),
                        periode = periode,
                        fnr = eps.ident.fnr,
                        begrunnelse = begrunnelse,
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
                    begrunnelse = begrunnelse,
                ).right()
                false -> Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(clock),
                    periode = periode,
                    begrunnelse = begrunnelse,
                ).right()
            }
        }

        return KunneIkkeLeggeTilBosituasjongrunnlag.UgyldigData.left()
    }
}
