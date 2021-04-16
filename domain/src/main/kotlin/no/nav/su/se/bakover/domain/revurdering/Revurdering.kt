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
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.RevurdertBeregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.util.UUID
import kotlin.math.abs

sealed class Revurdering : Behandling, Visitable<RevurderingVisitor> {
    abstract val tilRevurdering: Vedtak.EndringIYtelse
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
    abstract val behandlingsinformasjon: Behandlingsinformasjon

    open fun oppdaterBehandlingsinformasjon(behandlingsinformasjon: Behandlingsinformasjon) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = behandlingsinformasjon,
    )

    open fun beregn(
        fradrag: List<Fradrag>,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        val revurdertBeregning: Beregning = beregnInternt(
            fradrag = fradrag,
            behandlingsinformasjon = behandlingsinformasjon,
            periode = periode,
            vedtattBeregning = tilRevurdering.beregning,
        ).getOrHandle { return it.left() }

        val erAvslagGrunnetBeregning = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(revurdertBeregning)

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
            behandlingsinformasjon = behandlingsinformasjon,
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
            behandlingsinformasjon = behandlingsinformasjon,
        )

        fun ingenEndring(revurdertBeregning: Beregning): BeregnetRevurdering.IngenEndring = BeregnetRevurdering.IngenEndring(
            tilRevurdering = tilRevurdering,
            id = id,
            periode = periode,
            opprettet = opprettet,
            beregning = revurdertBeregning,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = behandlingsinformasjon,
        )

        return when (this.revurderingsårsak.årsak) {
            Revurderingsårsak.Årsak.MIGRERT,
            Revurderingsårsak.Årsak.MELDING_FRA_BRUKER,
            Revurderingsårsak.Årsak.INFORMASJON_FRA_KONTROLLSAMTALE,
            Revurderingsårsak.Årsak.DØDSFALL,
            Revurderingsårsak.Årsak.ANDRE_KILDER -> {
                when (endringerAvUtbetalingerErStørreEllerLik10Prosent(tilRevurdering.beregning, revurdertBeregning)) {
                    true -> {
                        when (erAvslagGrunnetBeregning) {
                            is AvslagGrunnetBeregning.Ja -> {
                                opphør(revurdertBeregning)
                            }
                            AvslagGrunnetBeregning.Nei -> {
                                innvilget(revurdertBeregning)
                            }
                        }
                    }
                    false -> {
                        ingenEndring(revurdertBeregning)
                    }
                }.right()
            }
            Revurderingsårsak.Årsak.REGULER_GRUNNBELØP -> {
                when (erAvslagGrunnetBeregning) {
                    is AvslagGrunnetBeregning.Ja -> {
                        opphør(revurdertBeregning)
                    }
                    AvslagGrunnetBeregning.Nei -> {
                        if (revurdertBeregning.equals(tilRevurdering.beregning)) {
                            ingenEndring(revurdertBeregning)
                        } else {
                            innvilget(revurdertBeregning)
                        }
                    }
                }.right()
            }
        }
    }

    companion object {
        private fun beregnInternt(
            fradrag: List<Fradrag>,
            behandlingsinformasjon: Behandlingsinformasjon,
            periode: Periode,
            vedtattBeregning: Beregning,
        ): Either<KunneIkkeBeregneRevurdering, Beregning> {
            val beregningsgrunnlag = Beregningsgrunnlag.create(
                beregningsperiode = periode,
                forventetInntektPerÅr = behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble() ?: 0.0,
                fradragFraSaksbehandler = fradrag,
            )
            // TODO jah: Også mulig å ta inn beregningsstrategi slik at man kan validere dette på service-nivå
            val beregningStrategy = behandlingsinformasjon.getBeregningStrategy().getOrHandle {
                return KunneIkkeBeregneRevurdering.UfullstendigBehandlingsinformasjon(it).left()
            }
            return RevurdertBeregning.fraSøknadsbehandling(
                vedtattBeregning = vedtattBeregning,
                beregningsgrunnlag = beregningsgrunnlag,
                beregningsstrategi = beregningStrategy,
            ).mapLeft {
                KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden
            }
        }
    }

    sealed class KunneIkkeBeregneRevurdering {
        object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering()
        data class UfullstendigBehandlingsinformasjon(
            val bakenforliggendeGrunn: Behandlingsinformasjon.UfullstendigBehandlingsinformasjon,
        ) : KunneIkkeBeregneRevurdering()
    }
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Vedtak.EndringIYtelse,
    override val saksbehandler: Saksbehandler,
    override val oppgaveId: OppgaveId,
    override val fritekstTilBrev: String,
    override val revurderingsårsak: Revurderingsårsak,
    override val behandlingsinformasjon: Behandlingsinformasjon,
) : Revurdering() {

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
    ): OpprettetRevurdering = this.copy(
        periode = periode,
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
    )
}

sealed class BeregnetRevurdering : Revurdering() {
    abstract val beregning: Beregning

    abstract fun toSimulert(simulering: Simulering): SimulertRevurdering

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
    )

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val behandlingsinformasjon: Behandlingsinformasjon,
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
            behandlingsinformasjon = behandlingsinformasjon,
        )
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val behandlingsinformasjon: Behandlingsinformasjon,
    ) : BeregnetRevurdering() {

        fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
            skalFøreTilBrevutsending: Boolean
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
            behandlingsinformasjon = behandlingsinformasjon,
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
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val behandlingsinformasjon: Behandlingsinformasjon,
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
            behandlingsinformasjon = behandlingsinformasjon,
        )

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }
}

sealed class SimulertRevurdering : Revurdering() {
    abstract val beregning: Beregning
    abstract val simulering: Simulering

    abstract override fun accept(visitor: RevurderingVisitor)

    abstract fun tilAttestering(
        attesteringsoppgaveId: OppgaveId,
        saksbehandler: Saksbehandler,
        fritekstTilBrev: String,
    ): RevurderingTilAttestering

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val behandlingsinformasjon: Behandlingsinformasjon,
    ) : SimulertRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
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
            oppgaveId = attesteringsoppgaveId,
            fritekstTilBrev = fritekstTilBrev,
            revurderingsårsak = revurderingsårsak,
            behandlingsinformasjon = behandlingsinformasjon,
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val revurderingsårsak: Revurderingsårsak,
        override val fritekstTilBrev: String,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val behandlingsinformasjon: Behandlingsinformasjon,
    ) : SimulertRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): RevurderingTilAttestering.Opphørt {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) throw IllegalStateException("Kan ikke sende en opphørt g-regulering til attestering")
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
                behandlingsinformasjon = behandlingsinformasjon,
            )
        }
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
    )
}

sealed class RevurderingTilAttestering : Revurdering() {
    abstract val beregning: Beregning

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun oppdaterBehandlingsinformasjon(behandlingsinformasjon: Behandlingsinformasjon) = throw IllegalStateException("Ikke lov å oppdatere behandlingsinformasjon i attestert status")

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
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
                    behandlingsinformasjon = behandlingsinformasjon,
                )
            }
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
    ) : RevurderingTilAttestering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
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
                    behandlingsinformasjon = behandlingsinformasjon,
                )
            }
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val skalFøreTilBrevutsending: Boolean,
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
                behandlingsinformasjon = behandlingsinformasjon,
            ).right()
        }
    }

    override fun beregn(
        fradrag: List<Fradrag>,
    ): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
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
                behandlingsinformasjon = behandlingsinformasjon,
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
                behandlingsinformasjon = behandlingsinformasjon,
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
                behandlingsinformasjon = behandlingsinformasjon,
            )
        }
    }
}

sealed class IverksattRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: Vedtak.EndringIYtelse
    abstract override val saksbehandler: Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract override val fritekstTilBrev: String
    abstract override val revurderingsårsak: Revurderingsårsak
    abstract val beregning: Beregning
    abstract val attestering: Attestering.Iverksatt

    abstract override fun accept(visitor: RevurderingVisitor)

    override fun oppdaterBehandlingsinformasjon(behandlingsinformasjon: Behandlingsinformasjon) = throw IllegalStateException("Ikke lov å oppdatere behandlingsinformasjon i status Iverksatt")

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val attestering: Attestering.Iverksatt,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val attestering: Attestering.Iverksatt,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val attestering: Attestering.Iverksatt,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val skalFøreTilBrevutsending: Boolean,
    ) : IverksattRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }
    }

    override fun beregn(
        fradrag: List<Fradrag>,
    ) = throw RuntimeException("Skal ikke kunne beregne når revurderingen er iverksatt")
}

sealed class UnderkjentRevurdering : Revurdering() {
    abstract val beregning: Beregning
    abstract val attestering: Attestering.Underkjent

    abstract override fun accept(visitor: RevurderingVisitor)

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val attestering: Attestering.Underkjent,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
    ) : UnderkjentRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

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
            behandlingsinformasjon = behandlingsinformasjon,
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val beregning: Beregning,
        override val attestering: Attestering.Underkjent,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val simulering: Simulering,
    ) : UnderkjentRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String,
        ): RevurderingTilAttestering.Opphørt {
            if (revurderingsårsak.årsak == Revurderingsårsak.Årsak.REGULER_GRUNNBELØP) throw IllegalStateException("Kan ikke sende en opphørt g-regulering til attestering")
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
                behandlingsinformasjon = behandlingsinformasjon,
            )
        }
    }

    data class IngenEndring(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.EndringIYtelse,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
        override val revurderingsårsak: Revurderingsårsak,
        override val attestering: Attestering.Underkjent,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val skalFøreTilBrevutsending: Boolean,
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
            behandlingsinformasjon = behandlingsinformasjon,
        )
    }

    fun oppdater(
        periode: Periode,
        revurderingsårsak: Revurderingsårsak,
    ) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev,
        revurderingsårsak = revurderingsårsak,
        behandlingsinformasjon = tilRevurdering.behandlingsinformasjon,
    )
}

/**
 * § 10. Endringar
 * Endring av stønaden må utgjøre minst en 10% endring för att det skal gå igenom.
 * Løsningen sjekker om første måneden utgør minst en 10% endring. Dette baserer sig på
 * att man må revurdere hela stønadsperioden frem i tid.
 * AI 16.02.2020
 */
private fun endringerAvUtbetalingerErStørreEllerLik10Prosent(
    vedtattBeregning: Beregning,
    revurdertBeregning: Beregning,
): Boolean {
    val vedtattBeregningsperioder = vedtattBeregning.getMånedsberegninger().associateBy { it.getPeriode() }

    return revurdertBeregning.getMånedsberegninger().let {
        val førsteUtbetaling = it.first()
        førsteUtbetaling.differanseErStørreEllerLik10Prosent(
            vedtattBeregningsperioder[førsteUtbetaling.getPeriode()]!!,
        )
    }
}

private fun Månedsberegning.differanseErStørreEllerLik10Prosent(otherMånedsberegning: Månedsberegning) =
    abs(this.getSumYtelse() - otherMånedsberegning.getSumYtelse()) >= (0.1 * this.getSumYtelse())

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
