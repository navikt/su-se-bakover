package no.nav.su.se.bakover.domain.vedtak

import arrow.core.Either
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.KunneIkkeJournalføreOgDistribuereBrev
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.time.Clock
import java.util.UUID

enum class VedtakType {
    SØKNAD, // Innvilget Søknadsbehandling                  -> EndringIYtelse
    AVSLAG, // Avslått Søknadsbehandling                    -> Avslag
    ENDRING, // Revurdering innvilget                       -> EndringIYtelse
    INGEN_ENDRING, // Revurdering mellom 2% og 10% endring  -> IngenEndringIYtelse
    OPPHØR, // Revurdering ført til opphør                  -> EndringIYtelse
}

interface VedtakFelles {
    val id: UUID
    val opprettet: Tidspunkt
    val behandling: Behandling
    val behandlingsinformasjon: Behandlingsinformasjon
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon
    val vedtakType: VedtakType
    val periode: Periode
}

sealed class Vedtak : VedtakFelles, Visitable<VedtakVisitor>, KanPlasseresPåTidslinje<Vedtak> {

    abstract fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Vedtak>
    abstract fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Vedtak>
    fun skalSendeBrev() =
        (this.behandling as? IverksattRevurdering.Innvilget)?.revurderingsårsak?.årsak != Revurderingsårsak.Årsak.REGULER_GRUNNBELØP

    companion object {
        fun fromSøknadsbehandling(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget, utbetalingId: UUID30) =
            EndringIYtelse(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                periode = søknadsbehandling.periode,
                behandling = søknadsbehandling,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                beregning = søknadsbehandling.beregning,
                simulering = søknadsbehandling.simulering,
                saksbehandler = søknadsbehandling.saksbehandler,
                attestant = søknadsbehandling.attestering.attestant,
                utbetalingId = utbetalingId,
                journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                vedtakType = VedtakType.SØKNAD,
            )

        fun from(revurdering: IverksattRevurdering.IngenEndring, clock: Clock) = IngenEndringIYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(clock),
            behandling = revurdering,
            behandlingsinformasjon = revurdering.behandlingsinformasjon,
            periode = revurdering.periode,
            beregning = revurdering.beregning,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.attestering.attestant,
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
        )

        fun from(revurdering: IverksattRevurdering.Innvilget, utbetalingId: UUID30) = EndringIYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            behandling = revurdering,
            behandlingsinformasjon = revurdering.behandlingsinformasjon,
            periode = revurdering.periode,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.attestering.attestant,
            utbetalingId = utbetalingId,
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
            vedtakType = VedtakType.ENDRING,
        )

        fun from(revurdering: IverksattRevurdering.Opphørt, utbetalingId: UUID30) = EndringIYtelse(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            behandling = revurdering,
            behandlingsinformasjon = revurdering.behandlingsinformasjon,
            periode = revurdering.periode,
            beregning = revurdering.beregning,
            simulering = revurdering.simulering,
            saksbehandler = revurdering.saksbehandler,
            attestant = revurdering.attestering.attestant,
            utbetalingId = utbetalingId,
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
            vedtakType = VedtakType.OPPHØR,
        )
    }

    data class EndringIYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val behandling: Behandling,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant,
        override val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
        override val periode: Periode,
        val beregning: Beregning,
        val simulering: Simulering,
        val utbetalingId: UUID30,
        override val vedtakType: VedtakType,
    ) : Vedtak() {

        override fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, EndringIYtelse> {
            return if (vedtakType == VedtakType.SØKNAD || vedtakType == VedtakType.ENDRING || vedtakType == VedtakType.OPPHØR) {
                journalføringOgBrevdistribusjon.journalfør(journalfør)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            } else {
                this.right()
            }
        }

        override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, EndringIYtelse> {
            return if (vedtakType == VedtakType.SØKNAD || vedtakType == VedtakType.ENDRING || vedtakType == VedtakType.OPPHØR) {
                journalføringOgBrevdistribusjon.distribuerBrev(distribuerBrev)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            } else {
                this.right()
            }
        }

        override fun accept(visitor: VedtakVisitor) {
            visitor.visit(this)
        }

        override fun copy(args: CopyArgs.Tidslinje): Vedtak = when (args) {
            CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(
                periode = args.periode,
            )
        }
    }

    data class IngenEndringIYtelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val behandling: Behandling,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant,
        override val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
        override val periode: Periode,
        val beregning: Beregning,
    ) : Vedtak() {
        override val vedtakType: VedtakType = VedtakType.INGEN_ENDRING

        override fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, IngenEndringIYtelse> {
            return if (behandling is IverksattRevurdering.IngenEndring && !behandling.skalFøreTilBrevutsending) {
                this.right()
            } else {
                journalføringOgBrevdistribusjon.journalfør(journalfør)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            }
        }

        override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, IngenEndringIYtelse> {
            return if (behandling is IverksattRevurdering.IngenEndring && !behandling.skalFøreTilBrevutsending) {
                this.right()
            } else {
                journalføringOgBrevdistribusjon.distribuerBrev(distribuerBrev)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            }
        }

        override fun accept(visitor: VedtakVisitor) {
            visitor.visit(this)
        }

        override fun copy(args: CopyArgs.Tidslinje): Vedtak = when (args) {
            CopyArgs.Tidslinje.Full -> this.copy()
            is CopyArgs.Tidslinje.NyPeriode -> this.copy(periode = args.periode)
        }
    }

    sealed class Avslag : Vedtak(), ErAvslag {
        override val vedtakType = VedtakType.AVSLAG

        companion object {
            fun fromSøknadsbehandlingMedBeregning(avslag: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) =
                AvslagBeregning(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    behandling = avslag,
                    behandlingsinformasjon = avslag.behandlingsinformasjon,
                    beregning = avslag.beregning,
                    saksbehandler = avslag.saksbehandler,
                    attestant = avslag.attestering.attestant,
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                    periode = avslag.periode,
                )

            fun fromSøknadsbehandlingUtenBeregning(avslag: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) =
                AvslagVilkår(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(),
                    behandling = avslag,
                    behandlingsinformasjon = avslag.behandlingsinformasjon,
                    saksbehandler = avslag.saksbehandler,
                    attestant = avslag.attestering.attestant,
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                    periode = avslag.periode,
                )
        }

        data class AvslagVilkår(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: Behandling,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
            override val periode: Periode,
        ) : Avslag() {
            override val avslagsgrunner: List<Avslagsgrunn> = behandlingsinformasjon.utledAvslagsgrunner()

            override fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Avslag> {
                return journalføringOgBrevdistribusjon.journalfør(journalfør)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            }

            override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Avslag> {
                return journalføringOgBrevdistribusjon.distribuerBrev(distribuerBrev)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            }

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }

            override fun copy(args: CopyArgs.Tidslinje): Vedtak = when (args) {
                CopyArgs.Tidslinje.Full -> this.copy()
                is CopyArgs.Tidslinje.NyPeriode -> this.copy(periode = args.periode)
            }
        }

        data class AvslagBeregning(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val behandling: Behandling,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
            override val periode: Periode,
            val beregning: Beregning,
        ) : Avslag() {
            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            // TODO jm: disse bør sannsynligvis peristeres.
            override val avslagsgrunner: List<Avslagsgrunn> =
                behandlingsinformasjon.utledAvslagsgrunner() + avslagsgrunnForBeregning

            override fun journalfør(journalfør: () -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre.FeilVedJournalføring, JournalpostId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeJournalføre, Avslag> {
                return journalføringOgBrevdistribusjon.journalfør(journalfør)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            }

            override fun distribuerBrev(distribuerBrev: (journalpostId: JournalpostId) -> Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev.FeilVedDistribueringAvBrev, BrevbestillingId>): Either<KunneIkkeJournalføreOgDistribuereBrev.KunneIkkeDistribuereBrev, Avslag> {
                return journalføringOgBrevdistribusjon.distribuerBrev(distribuerBrev)
                    .map { copy(journalføringOgBrevdistribusjon = it) }
            }

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }

            override fun copy(args: CopyArgs.Tidslinje): Vedtak = when (args) {
                CopyArgs.Tidslinje.Full -> this.copy()
                is CopyArgs.Tidslinje.NyPeriode -> this.copy(periode = args.periode)
            }
        }
    }
}
