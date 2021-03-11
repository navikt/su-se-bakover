package no.nav.su.se.bakover.domain.revurdering

import arrow.core.Either
import arrow.core.getOrElse
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
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.RevurdertBeregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.util.UUID
import kotlin.math.abs

sealed class Revurdering : Behandling, Visitable<RevurderingVisitor> {
    abstract val tilRevurdering: Vedtak.InnvilgetStønad
    abstract val periode: Periode
    abstract val saksbehandler: Saksbehandler
    override val sakId: UUID
        get() = tilRevurdering.behandling.sakId
    override val saksnummer: Saksnummer
        get() = tilRevurdering.behandling.saksnummer
    override val fnr: Fnr
        get() = tilRevurdering.behandling.fnr

    open fun beregn(fradrag: List<Fradrag>): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        val beregningsgrunnlag = Beregningsgrunnlag.create(
            beregningsperiode = periode,
            forventetInntektPerÅr = tilRevurdering.behandlingsinformasjon.uførhet?.forventetInntekt?.toDouble()
                ?: 0.0,
            fradragFraSaksbehandler = fradrag
        )
        // TODO jah: Også mulig å ta inn beregningsstrategi slik at man kan validere dette på service-nivå
        val beregningStrategy = tilRevurdering.behandlingsinformasjon.getBeregningStrategy().getOrHandle {
            return KunneIkkeBeregneRevurdering.UfullstendigBehandlingsinformasjon(it).left()
        }
        val revurdertBeregning: Beregning = RevurdertBeregning.fraSøknadsbehandling(
            vedtattBeregning = tilRevurdering.beregning,
            beregningsgrunnlag = beregningsgrunnlag,
            beregningsstrategi = beregningStrategy,
        ).getOrElse {
            return KunneIkkeBeregneRevurdering.KanIkkeVelgeSisteMånedVedNedgangIStønaden.left()
        }

        return if (endringerAvUtbetalingerErStørreEllerLik10Prosent(tilRevurdering.beregning, revurdertBeregning)) {
            BeregnetRevurdering.Innvilget(
                tilRevurdering = tilRevurdering,
                id = id,
                periode = periode,
                opprettet = Tidspunkt.now(),
                beregning = revurdertBeregning,
                saksbehandler = saksbehandler,
                oppgaveId = oppgaveId
            )
        } else {
            BeregnetRevurdering.Avslag(
                tilRevurdering = tilRevurdering,
                id = id,
                periode = periode,
                opprettet = Tidspunkt.now(),
                beregning = revurdertBeregning,
                saksbehandler = saksbehandler,
                oppgaveId = oppgaveId
            )
        }.right()
    }

    sealed class KunneIkkeBeregneRevurdering {
        object KanIkkeVelgeSisteMånedVedNedgangIStønaden : KunneIkkeBeregneRevurdering()
        data class UfullstendigBehandlingsinformasjon(
            val bakenforliggendeGrunn: Behandlingsinformasjon.UfullstendigBehandlingsinformasjon
        ) : KunneIkkeBeregneRevurdering()
    }
}

data class OpprettetRevurdering(
    override val id: UUID = UUID.randomUUID(),
    override val periode: Periode,
    override val opprettet: Tidspunkt = Tidspunkt.now(),
    override val tilRevurdering: Vedtak.InnvilgetStønad,
    override val saksbehandler: Saksbehandler,
    override val oppgaveId: OppgaveId,
) : Revurdering() {
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun oppdaterPeriode(periode: Periode) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId
    )
}

sealed class BeregnetRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: Vedtak.InnvilgetStønad
    abstract override val saksbehandler: Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract val beregning: Beregning

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun oppdaterPeriode(periode: Periode) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId
    )

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,

    ) : BeregnetRevurdering() {
        fun toSimulert(simulering: Simulering) = SimulertRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            beregning = beregning,
            simulering = simulering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId
        )
    }

    data class Avslag(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
    ) : BeregnetRevurdering()
}

data class SimulertRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Vedtak.InnvilgetStønad,
    override val saksbehandler: Saksbehandler,
    override val oppgaveId: OppgaveId,
    val beregning: Beregning,
    val simulering: Simulering
) : Revurdering() {
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun tilAttestering(attesteringsoppgaveId: OppgaveId, saksbehandler: Saksbehandler) = RevurderingTilAttestering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        beregning = beregning,
        simulering = simulering,
        oppgaveId = attesteringsoppgaveId,
    )

    fun oppdaterPeriode(periode: Periode) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId
    )
}

data class RevurderingTilAttestering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Vedtak.InnvilgetStønad,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    override val oppgaveId: OppgaveId
) : Revurdering() {

    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    override fun beregn(fradrag: List<Fradrag>): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
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

    fun iverksett(
        attestant: NavIdentBruker.Attestant,
        utbetal: () -> Either<KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering> {
        if (saksbehandler.navIdent == attestant.navIdent) {
            return KunneIkkeIverksetteRevurdering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }
        return utbetal().map {
            IverksattRevurdering(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attestant = attestant,
                utbetalingId = it,
                eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert
            )
        }
    }

    fun underkjenn(
        attestering: Attestering
    ): UnderkjentRevurdering {
        return UnderkjentRevurdering(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            attestering = attestering
        )
    }
}

data class IverksattRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Vedtak.InnvilgetStønad,
    override val saksbehandler: Saksbehandler,
    override val oppgaveId: OppgaveId,
    val beregning: Beregning,
    val simulering: Simulering,
    val attestant: NavIdentBruker.Attestant,
    val utbetalingId: UUID30,
    val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon
) : Revurdering() {
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, IverksattRevurdering> {
        return eksterneIverksettingsteg.journalfør(journalfør).map { copy(eksterneIverksettingsteg = it) }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, IverksattRevurdering> {
        return eksterneIverksettingsteg.distribuerBrev(distribuerBrev)
            .map { copy(eksterneIverksettingsteg = it) }
    }

    override fun beregn(fradrag: List<Fradrag>): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er til attestering")
    }
}

data class UnderkjentRevurdering(
    override val id: UUID,
    override val periode: Periode,
    override val opprettet: Tidspunkt,
    override val tilRevurdering: Vedtak.InnvilgetStønad,
    override val saksbehandler: Saksbehandler,
    val beregning: Beregning,
    val simulering: Simulering,
    override val oppgaveId: OppgaveId,
    val attestering: Attestering
) : Revurdering() {
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun nyOppgaveId(nyOppgaveId: OppgaveId): UnderkjentRevurdering{
        return this.copy(oppgaveId = nyOppgaveId)
    }
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
    revurdertBeregning: Beregning
): Boolean {
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
