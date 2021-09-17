package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMedAttestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.BeregningStrategyFactory
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.KunneIkkeLageGrunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.singleOrThrow
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlin.reflect.KClass

enum class BeslutningEtterForhåndsvarsling(val beslutning: String) {
    FortsettSammeOpplysninger("FORTSETT_MED_SAMME_OPPLYSNINGER"),
    FortsettMedAndreOpplysninger("FORTSETT_MED_ANDRE_OPPLYSNINGER"),
    AvsluttUtenEndringer("AVSLUTT_UTEN_ENDRINGER")
}

sealed class Forhåndsvarsel {
    object IngenForhåndsvarsel : Forhåndsvarsel()

    sealed class SkalForhåndsvarsles : Forhåndsvarsel() {
        object Sendt : SkalForhåndsvarsles()

        data class Besluttet(
            val valg: BeslutningEtterForhåndsvarsling,
            val begrunnelse: String,
        ) : SkalForhåndsvarsles()
    }
}

fun Forhåndsvarsel?.erKlarForAttestering() =
    when (this) {
        null -> false
        is Forhåndsvarsel.SkalForhåndsvarsles.Sendt -> false
        is Forhåndsvarsel.IngenForhåndsvarsel -> true
        is Forhåndsvarsel.SkalForhåndsvarsles.Besluttet -> true
    }

sealed class AbstraktRevurdering : Behandling {
    abstract val tilRevurdering: VedtakSomKanRevurderes
    override val sakId by lazy { tilRevurdering.behandling.sakId }
    override val saksnummer by lazy { tilRevurdering.behandling.saksnummer }
    override val fnr by lazy { tilRevurdering.behandling.fnr }
}

sealed class StansAvYtelseRevurdering : AbstraktRevurdering() {

    data class SimulertStansAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val tilRevurdering: VedtakSomKanRevurderes,
        val saksbehandler: Saksbehandler,
        val simulering: Simulering,
        val revurderingsårsak: Revurderingsårsak,
    ) : StansAvYtelseRevurdering() {
        val informasjonSomRevurderes =
            InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Ytelse to Vurderingstatus.Vurdert))

        fun iverksett(attestering: Attestering): IverksattStansAvYtelse {
            return IverksattStansAvYtelse(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                simulering = simulering,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(attestering),
                revurderingsårsak = revurderingsårsak,
            )
        }
    }

    data class IverksattStansAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val tilRevurdering: VedtakSomKanRevurderes,
        val saksbehandler: Saksbehandler,
        val simulering: Simulering,
        override val attesteringer: Attesteringshistorikk,
        val revurderingsårsak: Revurderingsårsak,
    ) : StansAvYtelseRevurdering(), BehandlingMedAttestering {
        val informasjonSomRevurderes =
            InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Ytelse to Vurderingstatus.Vurdert))
    }
}

sealed class GjenopptaYtelseRevurdering : AbstraktRevurdering() {

    data class SimulertGjenopptakAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val tilRevurdering: VedtakSomKanRevurderes,
        val saksbehandler: Saksbehandler,
        val simulering: Simulering,
        val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRevurdering() {
        val informasjonSomRevurderes =
            InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Ytelse to Vurderingstatus.Vurdert))

        fun iverksett(attestering: Attestering): IverksattGjenopptakAvYtelse {
            return IverksattGjenopptakAvYtelse(
                id = id,
                opprettet = opprettet,
                periode = periode,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                simulering = simulering,
                attesteringer = Attesteringshistorikk.empty().leggTilNyAttestering(attestering),
                revurderingsårsak = revurderingsårsak,
            )
        }
    }

    data class IverksattGjenopptakAvYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val periode: Periode,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val tilRevurdering: VedtakSomKanRevurderes,
        val saksbehandler: Saksbehandler,
        val simulering: Simulering,
        override val attesteringer: Attesteringshistorikk,
        val revurderingsårsak: Revurderingsårsak,
    ) : GjenopptaYtelseRevurdering(), BehandlingMedAttestering {
        val informasjonSomRevurderes =
            InformasjonSomRevurderes.create(mapOf(Revurderingsteg.Ytelse to Vurderingstatus.Vurdert))
    }
}

sealed class Revurdering :
    AbstraktRevurdering(),
    BehandlingMedOppgave,
    BehandlingMedAttestering,
    Visitable<RevurderingVisitor> {
    abstract val saksbehandler: Saksbehandler

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på søknadsbehandlinger)
    abstract val fritekstTilBrev: String
    abstract val revurderingsårsak: Revurderingsårsak

    abstract val informasjonSomRevurderes: InformasjonSomRevurderes

    abstract val forhåndsvarsel: Forhåndsvarsel?

    data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>)

    sealed class KunneIkkeLeggeTilFradrag {
        data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) : KunneIkkeLeggeTilFradrag()
        data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
            KunneIkkeLeggeTilFradrag()
    }

    sealed class KunneIkkeLeggeTilBosituasjon {
        data class Valideringsfeil(val feil: KunneIkkeLageGrunnlagsdata) :
            KunneIkkeLeggeTilBosituasjon()

        data class UgyldigTilstand(val fra: KClass<out Revurdering>, val til: KClass<out Revurdering>) :
            KunneIkkeLeggeTilBosituasjon()
    }

    open fun oppdaterUføreOgMarkerSomVurdert(uføre: Vilkår.Uførhet.Vurdert): Either<UgyldigTilstand, OpprettetRevurdering> =
        UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert): Either<UgyldigTilstand, OpprettetRevurdering> =
        UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> =
        KunneIkkeLeggeTilFradrag.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    open fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig): Either<KunneIkkeLeggeTilBosituasjon, OpprettetRevurdering> =
        KunneIkkeLeggeTilBosituasjon.UgyldigTilstand(this::class, OpprettetRevurdering::class).left()

    protected fun oppdaterUføreOgMarkerSomVurdertInternal(
        uføre: Vilkår.Uførhet.Vurdert,
    ): Either<UgyldigTilstand, OpprettetRevurdering> {
        return oppdaterVilkårsvurderinger(
            vilkårsvurderinger = vilkårsvurderinger.copy(
                uføre = uføre,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Uførhet),
        ).right()
    }

    protected fun oppdaterFormueOgMarkerSomVurdertInternal(formue: Vilkår.Formue.Vurdert) =
        oppdaterVilkårsvurderinger(
            vilkårsvurderinger = vilkårsvurderinger.copy(
                formue = formue,
            ),
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Formue),
        ).right()

    protected fun oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>): Either<KunneIkkeLeggeTilFradrag, OpprettetRevurdering> {
        return oppdaterGrunnlag(
            grunnlagsdata = Grunnlagsdata.tryCreate(
                bosituasjon = grunnlagsdata.bosituasjon,
                fradragsgrunnlag = fradragsgrunnlag,
            ).getOrHandle { return KunneIkkeLeggeTilFradrag.Valideringsfeil(it).left() },
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Inntekt),
        ).right()
    }

    protected fun oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon: Grunnlag.Bosituasjon.Fullstendig): Either<KunneIkkeLeggeTilBosituasjon, OpprettetRevurdering> {
        val gjeldendeBosituasjon = tilRevurdering.behandling.grunnlagsdata.bosituasjon.singleOrThrow()
        return oppdaterGrunnlag(
            grunnlagsdata = Grunnlagsdata.tryCreate(
                fradragsgrunnlag = grunnlagsdata.fradragsgrunnlag,
                bosituasjon = nonEmptyListOf(bosituasjon),
            ).getOrHandle { return KunneIkkeLeggeTilBosituasjon.Valideringsfeil(it).left() },
            informasjonSomRevurderes = informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Bosituasjon).let {
                if (bosituasjon.harEndretEllerFjernetEktefelle(gjeldendeBosituasjon)) {
                    it.markerSomIkkeVurdert(Revurderingsteg.Inntekt).markerSomIkkeVurdert(Revurderingsteg.Formue)
                } else {
                    it
                }
            },
        ).right()
    }

    protected fun oppdaterVilkårsvurderinger(
        vilkårsvurderinger: Vilkårsvurderinger,
        informasjonSomRevurderes: InformasjonSomRevurderes,
    ): OpprettetRevurdering {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )
    }

    protected fun oppdaterGrunnlag(
        grunnlagsdata: Grunnlagsdata,
        informasjonSomRevurderes: InformasjonSomRevurderes,
    ): OpprettetRevurdering {
        return OpprettetRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )
    }

    open fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        val revurdertBeregning: Beregning = BeregningStrategyFactory().beregn(
            GrunnlagsdataOgVilkårsvurderinger(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
            ),
            periode,
            // kan ikke legge til begrunnelse for inntekt/fradrag
            null,
        )

        fun opphør(revurdertBeregning: Beregning): BeregnetRevurdering.Opphørt = BeregnetRevurdering.Opphørt(
            tilRevurdering = tilRevurdering,
            id = id,
            periode = periode,
            opprettet = opprettet,
            beregning = revurdertBeregning,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )

        fun innvilget(revurdertBeregning: Beregning): BeregnetRevurdering.Innvilget = BeregnetRevurdering.Innvilget(
            tilRevurdering = tilRevurdering,
            id = id,
            periode = periode,
            opprettet = opprettet,
            beregning = revurdertBeregning,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )

        fun ingenEndring(revurdertBeregning: Beregning): BeregnetRevurdering.IngenEndring =
            BeregnetRevurdering.IngenEndring(
                tilRevurdering = tilRevurdering,
                id = id,
                periode = periode,
                opprettet = opprettet,
                beregning = revurdertBeregning,
                saksbehandler = saksbehandler,
                oppgaveId = oppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer,
            )

        // TODO jm: sjekk av vilkår og verifisering av dette bør sannsynligvis legges til et tidspunkt før selve beregningen finner sted. Snarvei inntil videre, da vi mangeler "infrastruktur" for dette pt.  Bør være en tydeligere del av modellen for revurdering.
        if (VurderOmVilkårGirOpphørVedRevurdering(vilkårsvurderinger).resultat is OpphørVedRevurdering.Ja) {
            return opphør(revurdertBeregning).right()
        }

        return when (revurderingsårsak.årsak) {
            Revurderingsårsak.Årsak.REGULER_GRUNNBELØP -> {
                when (
                    VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
                        eksisterendeUtbetalinger = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer },
                        nyBeregning = revurdertBeregning,
                    ).resultat
                ) {
                    true -> {
                        when (
                            VurderOmBeregningGirOpphørVedRevurdering(
                                beregning = revurdertBeregning,
                                clock = Clock.systemUTC(),
                            ).resultat
                        ) {
                            is OpphørVedRevurdering.Ja -> {
                                opphør(revurdertBeregning)
                            }
                            is OpphørVedRevurdering.Nei -> {
                                innvilget(revurdertBeregning)
                            }
                        }
                    }
                    false -> ingenEndring(revurdertBeregning)
                }
            }
            else -> {
                when (
                    VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
                        eksisterendeUtbetalinger = eksisterendeUtbetalinger.flatMap { it.utbetalingslinjer },
                        nyBeregning = revurdertBeregning,
                    ).resultat
                ) {
                    true -> {
                        when (
                            VurderOmBeregningGirOpphørVedRevurdering(
                                beregning = revurdertBeregning,
                                clock = Clock.systemUTC(),
                            ).resultat
                        ) {
                            is OpphørVedRevurdering.Ja -> {
                                opphør(revurdertBeregning)
                            }
                            is OpphørVedRevurdering.Nei -> {
                                innvilget(revurdertBeregning)
                            }
                        }
                    }
                    false -> ingenEndring(revurdertBeregning)
                }
            }
        }.right()
    }

    sealed class KunneIkkeBeregneRevurdering {
        object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering()

        data class UgyldigBeregningsgrunnlag(
            val reason: no.nav.su.se.bakover.domain.beregning.UgyldigBeregningsgrunnlag,
        ) : KunneIkkeBeregneRevurdering()

        object KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps : KunneIkkeBeregneRevurdering()
    }
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: VedtakSomKanRevurderes,
    override val saksbehandler: Saksbehandler,
    override val oppgaveId: OppgaveId,
    override val fritekstTilBrev: String,
    override val revurderingsårsak: Revurderingsårsak,
    override val forhåndsvarsel: Forhåndsvarsel?,
    override val grunnlagsdata: Grunnlagsdata,
    override val vilkårsvurderinger: Vilkårsvurderinger,
    override val informasjonSomRevurderes: InformasjonSomRevurderes,
    override val attesteringer: Attesteringshistorikk = Attesteringshistorikk.empty(),
) : Revurdering() {

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
    ): OpprettetRevurdering = this.copy(
        periode = periode,
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.IngenForhåndsvarsel else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        tilRevurdering = tilRevurdering,
    )
}

sealed class BeregnetRevurdering : Revurdering() {
    abstract val beregning: Beregning

    abstract fun toSimulert(simulering: Simulering): SimulertRevurdering

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.IngenForhåndsvarsel else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer,
    )

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : BeregnetRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun toSimulert(simulering: Simulering) = SimulertRevurdering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            beregning = beregning,
            simulering = simulering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : BeregnetRevurdering() {

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            skalFøreTilBrevutsending: Boolean,
        ) = RevurderingTilAttestering.IngenEndring(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            oppgaveId = attesteringsoppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            skalFøreTilBrevutsending = skalFøreTilBrevutsending,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )

        override fun toSimulert(simulering: Simulering): SimulertRevurdering {
            throw RuntimeException("Skal ikke kunne simulere en beregning som ikke har en endring")
        }

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : BeregnetRevurdering() {
        override fun toSimulert(simulering: Simulering) = SimulertRevurdering.Opphørt(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            beregning = beregning,
            simulering = simulering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )

        fun utledOpphørsgrunner(): List<Opphørsgrunn> {
            return when (val opphør = VurderOpphørVedRevurdering(vilkårsvurderinger, beregning).resultat) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }
}

object KanIkkeSendeEnOpphørtGReguleringTilAttestering

sealed class SimulertRevurdering : Revurdering() {
    abstract val beregning: Beregning
    abstract val simulering: Simulering
    abstract override var forhåndsvarsel: Forhåndsvarsel?
    abstract override val grunnlagsdata: Grunnlagsdata

    abstract override fun accept(visitor: RevurderingVisitor)
    fun harSimuleringFeilutbetaling() = simulering.harFeilutbetalinger()

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override var forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : SimulertRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            forhåndsvarsel: Forhåndsvarsel,
        ) = RevurderingTilAttestering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = attesteringsoppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override var forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : SimulertRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(): List<Opphørsgrunn> {
            return when (val opphør = VurderOpphørVedRevurdering(vilkårsvurderinger, beregning).resultat) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            forhåndsvarsel: Forhåndsvarsel,
            fritekstTilBrev: String,
        ): Either<KanIkkeSendeEnOpphørtGReguleringTilAttestering, RevurderingTilAttestering.Opphørt> {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                return KanIkkeSendeEnOpphørtGReguleringTilAttestering.left()
            } else {
                return RevurderingTilAttestering.Opphørt(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    simulering = simulering,
                    oppgaveId = attesteringsoppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    forhåndsvarsel = forhåndsvarsel,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                ).right()
            }
        }
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.IngenForhåndsvarsel else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer,
    )
}

sealed class RevurderingTilAttestering : Revurdering() {
    abstract val beregning: Beregning
    abstract override val grunnlagsdata: Grunnlagsdata

    abstract override fun accept(visitor: RevurderingVisitor)

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : RevurderingTilAttestering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            clock: Clock = Clock.systemUTC(),
            utbetal: () -> Either<KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>,
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Innvilget> {

            if (saksbehandler.navIdent == attestant.navIdent) {
                return KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return utbetal().map {
                IverksattRevurdering.Innvilget(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    simulering = simulering,
                    oppgaveId = oppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    forhåndsvarsel = forhåndsvarsel,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer.leggTilNyAttestering(
                        Attestering.Iverksatt(
                            attestant,
                            Tidspunkt.now(clock),
                        ),
                    ),
                )
            }
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : RevurderingTilAttestering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(): List<Opphørsgrunn> {
            return when (val opphør = VurderOpphørVedRevurdering(vilkårsvurderinger, beregning).resultat) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        // TODO: sjekk om vi skal utbetale 0 utbetalinger, eller ny status
        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            clock: Clock = Clock.systemUTC(),
            utbetal: () -> Either<KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>,
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.Opphørt> {

            if (saksbehandler.navIdent == attestant.navIdent) {
                return KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return utbetal().map {
                IverksattRevurdering.Opphørt(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    simulering = simulering,
                    oppgaveId = oppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    forhåndsvarsel = forhåndsvarsel,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer.leggTilNyAttestering(
                        Attestering.Iverksatt(
                            attestant,
                            Tidspunkt.now(clock),
                        ),
                    ),
                )
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        val skalFøreTilBrevutsending: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : RevurderingTilAttestering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            clock: Clock = Clock.systemUTC(),
        ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering.IngenEndring> {

            if (saksbehandler.navIdent == attestant.navIdent) {
                return KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
            }
            return IverksattRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                oppgaveId = oppgaveId,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
                attesteringer = attesteringer.leggTilNyAttestering(
                    Attestering.Iverksatt(
                        attestant,
                        Tidspunkt.now(clock),
                    ),
                ),
            ).right()
        }
    }

    override fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }

    sealed class KunneIkkeIverksetteRevurdering {
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering()
        data class KunneIkkeUtbetale(val utbetalingFeilet: UtbetalingFeilet) : KunneIkkeIverksetteRevurdering()
    }

    fun underkjenn(
        attestering: Attestering.Underkjent,
        oppgaveId: OppgaveId,
    ): UnderkjentRevurdering {
        return when (this) {
            is Innvilget -> UnderkjentRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            is Opphørt -> UnderkjentRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
            is IngenEndring -> UnderkjentRevurdering.IngenEndring(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                oppgaveId = oppgaveId,
                attesteringer = attesteringer.leggTilNyAttestering(attestering),
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                forhåndsvarsel = forhåndsvarsel,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            )
        }
    }
}

sealed class IverksattRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: VedtakSomKanRevurderes
    abstract override val saksbehandler: Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract override val fritekstTilBrev: String
    abstract override val revurderingsårsak: Revurderingsårsak
    abstract val beregning: Beregning
    val attestering: Attestering
        get() = attesteringer.hentSisteAttestering()

    abstract override fun accept(visitor: RevurderingVisitor)

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val forhåndsvarsel: Forhåndsvarsel,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : IverksattRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(): List<Opphørsgrunn> {
            val opphør = VurderOpphørVedRevurdering(vilkårsvurderinger, beregning).resultat
            return when (opphør) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        fun utledOpphørsdato(): LocalDate? {
            val opphør = VurderOpphørVedRevurdering(vilkårsvurderinger, beregning).resultat
            return when (opphør) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsdato
                OpphørVedRevurdering.Nei -> null
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        val skalFøreTilBrevutsending: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    override fun beregn(
        eksisterendeUtbetalinger: List<Utbetaling>,
    ) =
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er iverksatt")
}

sealed class UnderkjentRevurdering : Revurdering() {
    abstract val beregning: Beregning
    abstract override val attesteringer: Attesteringshistorikk
    abstract override val grunnlagsdata: Grunnlagsdata
    val attestering: Attestering.Underkjent
        get() = attesteringer.hentSisteAttestering() as Attestering.Underkjent

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun oppdaterUføreOgMarkerSomVurdert(
        uføre: Vilkår.Uførhet.Vurdert,
    ) = oppdaterUføreOgMarkerSomVurdertInternal(uføre)

    override fun oppdaterFormueOgMarkerSomVurdert(formue: Vilkår.Formue.Vurdert) =
        oppdaterFormueOgMarkerSomVurdertInternal(formue)

    override fun oppdaterFradragOgMarkerSomVurdert(fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) =
        oppdaterFradragOgMarkerSomVurdertInternal(fradragsgrunnlag)

    override fun oppdaterBosituasjonOgMarkerSomVurdert(bosituasjon: Grunnlag.Bosituasjon.Fullstendig) =
        oppdaterBosituasjonOgMarkerSomVurdertInternal(bosituasjon)

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val attesteringer: Attesteringshistorikk,
        override val forhåndsvarsel: Forhåndsvarsel,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
    ) : UnderkjentRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun harSimuleringFeilutbetaling() = simulering.harFeilutbetalinger()

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ) = RevurderingTilAttestering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val forhåndsvarsel: Forhåndsvarsel,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : UnderkjentRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(): List<Opphørsgrunn> {
            return when (val opphør = VurderOpphørVedRevurdering(vilkårsvurderinger, beregning).resultat) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
            }
        }

        fun harSimuleringFeilutbetaling() = simulering.harFeilutbetalinger()

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): Either<KanIkkeSendeEnOpphørtGReguleringTilAttestering, RevurderingTilAttestering.Opphørt> {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) {
                return KanIkkeSendeEnOpphørtGReguleringTilAttestering.left()
            } else {
                return RevurderingTilAttestering.Opphørt(
                    id = id,
                    periode = periode,
                    opprettet = opprettet,
                    tilRevurdering = tilRevurdering,
                    saksbehandler = saksbehandler,
                    beregning = beregning,
                    simulering = simulering,
                    oppgaveId = oppgaveId,
                    fritekstTilBrev = fritekstTilBrev,
                    revurderingsårsak = revurderingsårsak,
                    forhåndsvarsel = forhåndsvarsel,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
                    attesteringer = attesteringer,
                ).right()
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: VedtakSomKanRevurderes,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        val skalFøreTilBrevutsending: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
        override val attesteringer: Attesteringshistorikk,
    ) : UnderkjentRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            skalFøreTilBrevutsending: Boolean,
        ) = RevurderingTilAttestering.IngenEndring(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            skalFøreTilBrevutsending = skalFøreTilBrevutsending,
            forhåndsvarsel = forhåndsvarsel,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
            attesteringer = attesteringer,
        )
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
        grunnlagsdata: Grunnlagsdata,
        vilkårsvurderinger: Vilkårsvurderinger,
        informasjonSomRevurderes: InformasjonSomRevurderes,
        tilRevurdering: VedtakSomKanRevurderes,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) Forhåndsvarsel.IngenForhåndsvarsel else null,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
        attesteringer = attesteringer,
    )
}

fun Revurdering.medFritekst(fritekstTilBrev: String) =
    when (this) {
        is BeregnetRevurdering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
        is BeregnetRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is OpprettetRevurdering -> copy(fritekstTilBrev = fritekstTilBrev)
        is RevurderingTilAttestering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is RevurderingTilAttestering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is SimulertRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is SimulertRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentRevurdering.Innvilget -> copy(fritekstTilBrev = fritekstTilBrev)
        is BeregnetRevurdering.Opphørt -> copy(fritekstTilBrev = fritekstTilBrev)
        is IverksattRevurdering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
        is RevurderingTilAttestering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
        is UnderkjentRevurdering.IngenEndring -> copy(fritekstTilBrev = fritekstTilBrev)
    }

enum class Vurderingstatus(val status: String) {
    Vurdert("Vurdert"),
    IkkeVurdert("IkkeVurdert")
}

enum class Revurderingsteg(val vilkår: String) {
    // BorOgOppholderSegINorge("BorOgOppholderSegINorge"),
    // Flyktning("Flyktning"),
    Formue("Formue"),

    // Oppholdstillatelse("Oppholdstillatelse"),
    // PersonligOppmøte("PersonligOppmøte"),
    Uførhet("Uførhet"),
    Bosituasjon("Bosituasjon"),

    // InnlagtPåInstitusjon("InnlagtPåInstitusjon"),
    // UtenlandsoppholdOver90Dager("UtenlandsoppholdOver90Dager"),
    Inntekt("Inntekt"),
    Ytelse("Ytelse"),
}
