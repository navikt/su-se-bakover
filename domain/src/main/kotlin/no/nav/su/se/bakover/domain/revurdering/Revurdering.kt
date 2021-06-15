package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.harFradragSomTilhørerEps
import no.nav.su.se.bakover.domain.beregning.utledBeregningsstrategi
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.util.UUID

enum class BeslutningEtterForhåndsvarsling(val beslutning: String) {
    FortsettSammeOpplysninger("FORTSETT_MED_SAMME_OPPLYSNINGER"),
    FortsettMedAndreOpplysninger("FORTSETT_MED_ANDRE_OPPLYSNINGER"),
    AvsluttUtenEndringer("AVSLUTT_UTEN_ENDRINGER")
}

sealed class Forhåndsvarsel {
    object IngenForhåndsvarsel : Forhåndsvarsel()

    sealed class SkalForhåndsvarsles : Forhåndsvarsel() {
        data class Sendt(
            val journalpostId: JournalpostId,
            val brevbestillingId: BrevbestillingId?,
        ) : SkalForhåndsvarsles()

        data class Besluttet(
            val journalpostId: JournalpostId,
            val brevbestillingId: BrevbestillingId?,
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

sealed class Revurdering : Behandling, Visitable<RevurderingVisitor> {
    abstract val tilRevurdering: VedtakSomKanRevurderes
    abstract val saksbehandler: Saksbehandler
    override val sakId: UUID
        get() = tilRevurdering.behandling.sakId
    override val saksnummer: Saksnummer
        get() = tilRevurdering.behandling.saksnummer
    override val fnr: Fnr
        get() = tilRevurdering.behandling.fnr

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på søknadsbehandlinger)
    abstract val fritekstTilBrev: String
    abstract val revurderingsårsak: Revurderingsårsak

    // TODO jah: Denne bør kunne slettes etter Grunnlagsdata innholder Bosituasjon, men tenker den fortjener egen PR?
    abstract val behandlingsinformasjon: Behandlingsinformasjon
    abstract val informasjonSomRevurderes: InformasjonSomRevurderes

    abstract val forhåndsvarsel: Forhåndsvarsel?

    open fun oppdaterBehandlingsinformasjon(behandlingsinformasjon: Behandlingsinformasjon) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        forhåndsvarsel = forhåndsvarsel,
        behandlingsinformasjon = behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
    )

    open fun beregn(eksisterendeUtbetalinger: List<Utbetaling>): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        val revurdertBeregning: Beregning = beregnInternt(
            fradrag = grunnlagsdata.fradragsgrunnlag.map { it.fradrag },
            bosituasjon = grunnlagsdata.bosituasjon.singleFullstendigOrThrow(),
            uføregrunnlag = grunnlagsdata.uføregrunnlag,
            periode = periode,
        ).getOrHandle { return it.left() }

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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = if (informasjonSomRevurderes.containsKey(Revurderingsteg.Inntekt)) {
                informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Inntekt)
            } else informasjonSomRevurderes,
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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = if (informasjonSomRevurderes.containsKey(Revurderingsteg.Inntekt)) {
                informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Inntekt)
            } else informasjonSomRevurderes,
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
                behandlingsinformasjon = behandlingsinformasjon,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = if (informasjonSomRevurderes.containsKey(Revurderingsteg.Inntekt)) {
                    informasjonSomRevurderes.markerSomVurdert(Revurderingsteg.Inntekt)
                } else informasjonSomRevurderes,
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

    companion object {
        private fun beregnInternt(
            fradrag: List<Fradrag>,
            bosituasjon: Grunnlag.Bosituasjon.Fullstendig,
            uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
            periode: Periode,
        ): Either<KunneIkkeBeregneRevurdering, Beregning> {
            if (!bosituasjon.harEktefelle() && (fradrag.harFradragSomTilhørerEps())) {
                return KunneIkkeBeregneRevurdering.KanIkkeHaFradragSomTilhørerEpsHvisBrukerIkkeHarEps.left()
            }
            val beregningsgrunnlag = Beregningsgrunnlag.tryCreate(
                beregningsperiode = periode,
                uføregrunnlag = uføregrunnlag,
                fradragFraSaksbehandler = fradrag,
            ).getOrHandle {
                return KunneIkkeBeregneRevurdering.UgyldigBeregningsgrunnlag(it).left()
            }

            val beregningsstrategi = bosituasjon.utledBeregningsstrategi()
            return beregningsstrategi.beregn(beregningsgrunnlag).right()
        }
    }

    sealed class KunneIkkeBeregneRevurdering {
        object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering()
        data class UfullstendigBehandlingsinformasjon(
            val bakenforliggendeGrunn: Behandlingsinformasjon.UfullstendigBehandlingsinformasjon,
        ) : KunneIkkeBeregneRevurdering()

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
    override val behandlingsinformasjon: Behandlingsinformasjon,
    override val grunnlagsdata: Grunnlagsdata,
    override val vilkårsvurderinger: Vilkårsvurderinger,
    override val informasjonSomRevurderes: InformasjonSomRevurderes,
) : Revurdering() {

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

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
        behandlingsinformasjon = this.tilRevurdering.behandlingsinformasjon,
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
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
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
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
    )
}

sealed class RevurderingTilAttestering : Revurdering() {
    abstract val beregning: Beregning
    abstract override val grunnlagsdata: Grunnlagsdata

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun oppdaterBehandlingsinformasjon(behandlingsinformasjon: Behandlingsinformasjon) =
        throw IllegalStateException("Ikke lov å oppdatere behandlingsinformasjon i attestert status")

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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
    ) : RevurderingTilAttestering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
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
                    attestering = Attestering.Iverksatt(attestant),
                    forhåndsvarsel = forhåndsvarsel,
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
                    attestering = Attestering.Iverksatt(attestant),
                    forhåndsvarsel = forhåndsvarsel,
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val skalFøreTilBrevutsending: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
    ) : RevurderingTilAttestering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
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
                attestering = Attestering.Iverksatt(attestant),
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                informasjonSomRevurderes = informasjonSomRevurderes,
            ).right()
        }
    }

    override fun beregn(eksisterendeUtbetalinger: List<Utbetaling>): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }

    sealed class KunneIkkeIverksetteRevurdering {
        object AttestantOgSaksbehandlerKanIkkeVæreSammePerson : KunneIkkeIverksetteRevurdering()
        sealed class KunneIkkeUtbetale : KunneIkkeIverksetteRevurdering() {
            object SimuleringHarBlittEndretSidenSaksbehandlerSimulerte : KunneIkkeUtbetale()
            object Protokollfeil : KunneIkkeUtbetale()
            object KunneIkkeSimulere : KunneIkkeUtbetale()
        }
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
                attestering = attestering,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
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
                attestering = attestering,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
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
                attestering = attestering,
                fritekstTilBrev = fritekstTilBrev,
                revurderingsårsak = revurderingsårsak,
                skalFøreTilBrevutsending = skalFøreTilBrevutsending,
                forhåndsvarsel = forhåndsvarsel,
                behandlingsinformasjon = behandlingsinformasjon,
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
    abstract val attestering: Attestering.Iverksatt

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun oppdaterBehandlingsinformasjon(behandlingsinformasjon: Behandlingsinformasjon) =
        throw IllegalStateException("Ikke lov å oppdatere behandlingsinformasjon i status Iverksatt")

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
        override val attestering: Attestering.Iverksatt,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
        override val attestering: Attestering.Iverksatt,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun utledOpphørsgrunner(): List<Opphørsgrunn> {
            return when (val opphør = VurderOpphørVedRevurdering(vilkårsvurderinger, beregning).resultat) {
                is OpphørVedRevurdering.Ja -> opphør.opphørsgrunner
                OpphørVedRevurdering.Nei -> emptyList()
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
        override val attestering: Attestering.Iverksatt,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val skalFøreTilBrevutsending: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    override fun beregn(eksisterendeUtbetalinger: List<Utbetaling>) = throw RuntimeException("Skal ikke kunne beregne når revurderingen er iverksatt")
}

sealed class UnderkjentRevurdering : Revurdering() {
    abstract val beregning: Beregning
    abstract val attestering: Attestering.Underkjent
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
        override val attestering: Attestering.Underkjent,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val behandlingsinformasjon: Behandlingsinformasjon,
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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val attestering: Attestering.Underkjent,
        override val forhåndsvarsel: Forhåndsvarsel,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
                    behandlingsinformasjon = behandlingsinformasjon,
                    grunnlagsdata = grunnlagsdata,
                    vilkårsvurderinger = vilkårsvurderinger,
                    informasjonSomRevurderes = informasjonSomRevurderes,
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
        override val attestering: Attestering.Underkjent,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val skalFøreTilBrevutsending: Boolean,
        override val forhåndsvarsel: Forhåndsvarsel?,
        override val grunnlagsdata: Grunnlagsdata,
        override val vilkårsvurderinger: Vilkårsvurderinger,
        override val informasjonSomRevurderes: InformasjonSomRevurderes,
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
            behandlingsinformasjon = behandlingsinformasjon,
            grunnlagsdata = grunnlagsdata,
            vilkårsvurderinger = vilkårsvurderinger,
            informasjonSomRevurderes = informasjonSomRevurderes,
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
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
        grunnlagsdata = grunnlagsdata,
        vilkårsvurderinger = vilkårsvurderinger,
        informasjonSomRevurderes = informasjonSomRevurderes,
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
    // Formue("Formue"),
    // Oppholdstillatelse("Oppholdstillatelse"),
    // PersonligOppmøte("PersonligOppmøte"),
    Uførhet("Uførhet"),
    Bosituasjon("Bosituasjon"),

    // InnlagtPåInstitusjon("InnlagtPåInstitusjon"),
    // UtenlandsoppholdOver90Dager("UtenlandsoppholdOver90Dager"),
    Inntekt("Inntekt"),
}
