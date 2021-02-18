package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre.FeilVedJournalføring
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.util.UUID
import kotlin.math.abs

sealed class Revurdering : Visitable<RevurderingVisitor> {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget
    abstract val periode: Periode
    abstract val saksbehandler: Saksbehandler

    val sakId
        get() = this.tilRevurdering.sakId

    val fnr
        get() = this.tilRevurdering.fnr

    open fun beregn(fradrag: List<Fradrag>): BeregnetRevurdering {
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = periode,
            forventetInntektPerÅr = tilRevurdering.behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble()
                ?: 0.0,
            fradragFraSaksbehandler = fradrag
        )
        val revurdertBeregning = tilRevurdering.behandlingsinformasjon.bosituasjon!!.getBeregningStrategy()
            .beregn(beregningsgrunnlag)

        return if (endringerAvUtbetalingerErStørreEllerLik10Prosent(tilRevurdering.beregning, revurdertBeregning)) {
            BeregnetRevurdering.Innvilget(
                tilRevurdering = tilRevurdering,
                id = id,
                periode = periode,
                opprettet = Tidspunkt.now(),
                beregning = revurdertBeregning,
                saksbehandler = saksbehandler
            )
        } else {
            BeregnetRevurdering.Avslag(
                tilRevurdering = tilRevurdering,
                id = id,
                periode = periode,
                opprettet = Tidspunkt.now(),
                beregning = revurdertBeregning,
                saksbehandler = saksbehandler
            )
        }
    }

    object AttestantOgSaksbehandlerKanIkkeVæreSammePerson
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val periode: Periode,
    override val opprettet: Tidspunkt = Tidspunkt.now(),
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
) : Revurdering() {
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }
}

sealed class BeregnetRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget
    abstract override val saksbehandler: Saksbehandler
    abstract val beregning: Beregning

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
    ) : BeregnetRevurdering() {
        fun toSimulert(simulering: Simulering) = SimulertRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            beregning = beregning,
            simulering = simulering,
            saksbehandler = saksbehandler
        )
    }

    data class Avslag(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
    ) : BeregnetRevurdering()
}

data class SimulertRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering
) : Revurdering() {
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun tilAttestering(oppgaveId: OppgaveId, saksbehandler: Saksbehandler): RevurderingTilAttestering =
        RevurderingTilAttestering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
        )
}

data class RevurderingTilAttestering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    val oppgaveId: OppgaveId
) : Revurdering() {

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    override fun beregn(fradrag: List<Fradrag>): BeregnetRevurdering {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }

    fun iverksett(
        attestant: NavIdentBruker.Attestant,
        utbetalingId: UUID30
    ): Either<AttestantOgSaksbehandlerKanIkkeVæreSammePerson, IverksattRevurdering> {
        if (saksbehandler.navIdent == attestant.navIdent) {
            return AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }
        return IverksattRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            attestant = attestant,
            utbetalingId = utbetalingId,
            eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
        ).right()
    }

    fun underkjenn() {
        TODO()
    }
}

data class IverksattRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Søknadsbehandling.Iverksatt.Innvilget,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    val oppgaveId: OppgaveId,
    val attestant: NavIdentBruker.Attestant,
    val utbetalingId: UUID30,
    val eksterneIverksettingsteg: EksterneIverksettingsstegEtterUtbetaling
) : Revurdering() {
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun journalfør(journalfør: () -> Either<FeilVedJournalføring, JournalpostId>): Either<EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeJournalføre, IverksattRevurdering> {
        return eksterneIverksettingsteg.journalfør(journalfør).map { copy(eksterneIverksettingsteg = it) }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<EksterneIverksettingsstegFeil.EksterneIverksettingsstegEtterUtbetalingFeil.KunneIkkeDistribuereBrev, IverksattRevurdering> {
        return eksterneIverksettingsteg.distribuerBrev(distribuerBrev)
            .map { copy(eksterneIverksettingsteg = it) }
    }

    override fun beregn(fradrag: List<Fradrag>): BeregnetRevurdering {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }
}

/**
 * § 10. Endringar
 * Endring av stønaden må utgjøre minst en 10% endring för att det skal gå igenom.
 * Løsningen sjekker om første måneden utgør minst en 10% endring. Dette baserer sig på
 * att man må revurdere hela stønadsperioden frem i tid.
 * AI 16.02.2020
 */
private fun endringerAvUtbetalingerErStørreEllerLik10Prosent(vedtattBeregning: Beregning, revurdertBeregning: Beregning): Boolean {
    val vedtattBeregningsperioder = vedtattBeregning.getMånedsberegninger().map { it.getPeriode() to it }.toMap()

    return revurdertBeregning.getMånedsberegninger().let {
        val førsteUtbetaling = it.first()
        førsteUtbetaling.differanseErStørreEllerLik10Prosent(
            vedtattBeregningsperioder[førsteUtbetaling.getPeriode()]!!
        )
    }
}

private fun Månedsberegning.differanseErStørreEllerLik10Prosent(otherMånedsberegning: Månedsberegning) =
    abs(this.getSumYtelse() - otherMånedsberegning.getSumYtelse()) >= (0.1 * this.getSumYtelse())
