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

    // TODO ia: fritekst bør flyttes ut av denne klassen og til et eget konsept (som også omfatter fritekst på søknadsbehandlinger)
    abstract val fritekstTilBrev: String

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
                oppgaveId = oppgaveId,
                fritekstTilBrev = fritekstTilBrev
            )
        } else {
            BeregnetRevurdering.Avslag(
                tilRevurdering = tilRevurdering,
                id = id,
                periode = periode,
                opprettet = Tidspunkt.now(),
                beregning = revurdertBeregning,
                saksbehandler = saksbehandler,
                oppgaveId = oppgaveId,
                fritekstTilBrev = fritekstTilBrev
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
    override val fritekstTilBrev: String,
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
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev
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

    abstract fun toSimulert(simulering: Simulering): SimulertRevurdering

    // TODO: skal de inn på subtyper?
    override fun accept(visitor: RevurderingVisitor) {
        visitor.visit(this)
    }

    fun oppdaterPeriode(periode: Periode) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev
    )

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
    ) : BeregnetRevurdering() {
        override fun toSimulert(simulering: Simulering) = SimulertRevurdering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            beregning = beregning,
            simulering = simulering,
            saksbehandler = saksbehandler,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev
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
        override val fritekstTilBrev: String,
    ) : BeregnetRevurdering() {
        override fun toSimulert(simulering: Simulering): SimulertRevurdering {
            throw RuntimeException("Skal ikke kunne simulere en beregning som er til avslag")
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
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
            fritekstTilBrev = fritekstTilBrev
        )
    }
}

sealed class SimulertRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: Vedtak.InnvilgetStønad
    abstract override val saksbehandler: Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract val beregning: Beregning
    abstract val simulering: Simulering
    abstract override val fritekstTilBrev: String

    abstract override fun accept(visitor: RevurderingVisitor)

    abstract fun tilAttestering(
        attesteringsoppgaveId: OppgaveId,
        saksbehandler: Saksbehandler,
        fritekstTilBrev: String
    ): RevurderingTilAttestering

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val fritekstTilBrev: String,
    ) : SimulertRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String
        ) = RevurderingTilAttestering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = attesteringsoppgaveId,
            fritekstTilBrev = fritekstTilBrev
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val fritekstTilBrev: String,
    ) : SimulertRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilAttestering(
            attesteringsoppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String
        ) = RevurderingTilAttestering.Opphørt(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = attesteringsoppgaveId,
            fritekstTilBrev = fritekstTilBrev
        )
    }

    fun oppdaterPeriode(periode: Periode) = OpprettetRevurdering(
        id = id,
        periode = periode,
        opprettet = opprettet,
        tilRevurdering = tilRevurdering,
        saksbehandler = saksbehandler,
        oppgaveId = oppgaveId,
        fritekstTilBrev = fritekstTilBrev
    )
}

sealed class RevurderingTilAttestering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: Vedtak.InnvilgetStønad
    abstract override val saksbehandler: Saksbehandler
    abstract val beregning: Beregning
    abstract val simulering: Simulering
    abstract override val oppgaveId: OppgaveId
    abstract override val fritekstTilBrev: String

    abstract override fun accept(visitor: RevurderingVisitor)
    abstract fun tilIverksatt(
        attestant: NavIdentBruker.Attestant,
        utbetal: () -> Either<KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>
    ): Either<KunneIkkeIverksetteRevurdering, IverksattRevurdering>

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
    ) : RevurderingTilAttestering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            utbetal: () -> Either<KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>
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
                    attestering = Attestering.Iverksatt(attestant),
                    utbetalingId = it,
                    eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                )
            }
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val oppgaveId: OppgaveId,
        override val fritekstTilBrev: String,
    ) : RevurderingTilAttestering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        // TODO: sjekk om vi skal utbetale 0 utbetalinger, eller ny status
        override fun tilIverksatt(
            attestant: NavIdentBruker.Attestant,
            utbetal: () -> Either<KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale, UUID30>
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
                    attestering = Attestering.Iverksatt(attestant),
                    utbetalingId = it,
                    eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                )
            }
        }
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

    fun underkjenn(
        attestering: Attestering,
        oppgaveId: OppgaveId
    ): UnderkjentRevurdering {
        when (this) {
            is Innvilget -> return UnderkjentRevurdering.Innvilget(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attestering = attestering,
                fritekstTilBrev = fritekstTilBrev
            )
            is Opphørt -> return UnderkjentRevurdering.Opphørt(
                id = id,
                periode = periode,
                opprettet = opprettet,
                tilRevurdering = tilRevurdering,
                saksbehandler = saksbehandler,
                beregning = beregning,
                simulering = simulering,
                oppgaveId = oppgaveId,
                attestering = attestering,
                fritekstTilBrev = fritekstTilBrev
            )
        }
    }
}

sealed class IverksattRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: Vedtak.InnvilgetStønad
    abstract override val saksbehandler: Saksbehandler
    abstract override val oppgaveId: OppgaveId
    abstract override val fritekstTilBrev: String
    abstract val beregning: Beregning
    abstract val simulering: Simulering
    abstract val attestering: Attestering.Iverksatt
    abstract val utbetalingId: UUID30
    abstract val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon

    abstract override fun accept(visitor: RevurderingVisitor)
    abstract fun oppdaterIverksettingsteg(oppdatertIverksettingsteg: JournalføringOgBrevdistribusjon): IverksattRevurdering

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val attestering: Attestering.Iverksatt,
        override val utbetalingId: UUID30,
        override val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon,
        override val fritekstTilBrev: String,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun oppdaterIverksettingsteg(oppdatertIverksettingsteg: JournalføringOgBrevdistribusjon): Innvilget {
            return copy(eksterneIverksettingsteg = oppdatertIverksettingsteg)
        }
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val oppgaveId: OppgaveId,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val attestering: Attestering.Iverksatt,
        override val utbetalingId: UUID30,
        override val eksterneIverksettingsteg: JournalføringOgBrevdistribusjon,
        override val fritekstTilBrev: String,
    ) : IverksattRevurdering() {

        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun oppdaterIverksettingsteg(oppdatertIverksettingsteg: JournalføringOgBrevdistribusjon): Opphørt {
            return copy(eksterneIverksettingsteg = oppdatertIverksettingsteg)
        }
    }

    fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, IverksattRevurdering> {
        return eksterneIverksettingsteg.journalfør(journalfør).map { oppdaterIverksettingsteg(it) }
    }

    fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, IverksattRevurdering> {
        return eksterneIverksettingsteg.distribuerBrev(distribuerBrev).map { oppdaterIverksettingsteg(it) }
    }

    override fun beregn(fradrag: List<Fradrag>): Either<KunneIkkeBeregneRevurdering, BeregnetRevurdering> {
        throw RuntimeException("Skal ikke kunne beregne når revurderingen er iverksatt")
    }
}

sealed class UnderkjentRevurdering : Revurdering() {
    abstract override val id: UUID
    abstract override val periode: Periode
    abstract override val opprettet: Tidspunkt
    abstract override val tilRevurdering: Vedtak.InnvilgetStønad
    abstract override val saksbehandler: Saksbehandler
    abstract val beregning: Beregning
    abstract val simulering: Simulering
    abstract override val oppgaveId: OppgaveId
    abstract val attestering: Attestering
    abstract override val fritekstTilBrev: String

    abstract override fun accept(visitor: RevurderingVisitor)
    abstract fun tilAttestering(
        oppgaveId: OppgaveId,
        saksbehandler: Saksbehandler,
        fritekstTilBrev: String
    ): RevurderingTilAttestering

    data class Innvilget(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val oppgaveId: OppgaveId,
        override val attestering: Attestering,
        override val fritekstTilBrev: String,
    ) : UnderkjentRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String
        ) = RevurderingTilAttestering.Innvilget(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev
        )
    }

    data class Opphørt(
        override val id: UUID,
        override val periode: Periode,
        override val opprettet: Tidspunkt,
        override val tilRevurdering: Vedtak.InnvilgetStønad,
        override val saksbehandler: Saksbehandler,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val oppgaveId: OppgaveId,
        override val attestering: Attestering,
        override val fritekstTilBrev: String,
    ) : UnderkjentRevurdering() {
        override fun accept(visitor: RevurderingVisitor) {
            visitor.visit(this)
        }

        override fun tilAttestering(
            oppgaveId: OppgaveId,
            saksbehandler: Saksbehandler,
            fritekstTilBrev: String
        ) = RevurderingTilAttestering.Opphørt(
            id = id,
            periode = periode,
            opprettet = opprettet,
            tilRevurdering = tilRevurdering,
            saksbehandler = saksbehandler,
            beregning = beregning,
            simulering = simulering,
            oppgaveId = oppgaveId,
            fritekstTilBrev = fritekstTilBrev
        )
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

fun Revurdering.medFritekst(fritekstTilBrev: String) =
    when (this) {
        is BeregnetRevurdering.Avslag -> copy(fritekstTilBrev = fritekstTilBrev)
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
    }
