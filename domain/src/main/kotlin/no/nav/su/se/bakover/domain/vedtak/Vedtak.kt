package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.ErAvslag
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.visitor.Visitable
import java.util.UUID

sealed class Vedtak : Visitable<VedtakVisitor> {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val behandling: Behandling
    abstract val behandlingsinformasjon: Behandlingsinformasjon

    data class InnvilgetStønad(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        val periode: Periode,
        override val behandling: Behandling,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        val beregning: Beregning,
        val simulering: Simulering,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val attestant: NavIdentBruker.Attestant,
        val utbetalingId: UUID30,
        val eksterneIverksettingsteg: EksterneIverksettingsstegEtterUtbetaling,
    ) : Vedtak() {
        companion object {
            fun fromSøknadsbehandling(søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget) = InnvilgetStønad(
                periode = søknadsbehandling.beregning.getPeriode(),
                behandling = søknadsbehandling,
                behandlingsinformasjon = søknadsbehandling.behandlingsinformasjon,
                beregning = søknadsbehandling.beregning,
                simulering = søknadsbehandling.simulering,
                saksbehandler = søknadsbehandling.saksbehandler,
                attestant = søknadsbehandling.attestering.attestant,
                utbetalingId = søknadsbehandling.utbetalingId,
                eksterneIverksettingsteg = søknadsbehandling.eksterneIverksettingsteg,
            )

            fun fromRevurdering(revurdering: IverksattRevurdering) = InnvilgetStønad(
                behandling = revurdering,
                behandlingsinformasjon = revurdering.tilRevurdering.behandlingsinformasjon,
                periode = revurdering.beregning.getPeriode(),
                beregning = revurdering.beregning,
                simulering = revurdering.simulering,
                saksbehandler = revurdering.saksbehandler,
                attestant = revurdering.attestant,
                utbetalingId = revurdering.utbetalingId,
                eksterneIverksettingsteg = revurdering.eksterneIverksettingsteg
            )
        }

        override fun accept(visitor: VedtakVisitor) {
            visitor.visit(this)
        }
    }

    sealed class AvslåttStønad : Vedtak(), ErAvslag {
        abstract val saksbehandler: NavIdentBruker.Saksbehandler
        abstract val attestant: NavIdentBruker.Attestant
        abstract val eksterneIverksettingsteg: EksterneIverksettingsstegForAvslag

        companion object {
            fun fromSøknadsbehandlingMedBeregning(avslag: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) =
                MedBeregning(
                    behandling = avslag,
                    behandlingsinformasjon = avslag.behandlingsinformasjon,
                    beregning = avslag.beregning,
                    saksbehandler = avslag.saksbehandler,
                    attestant = avslag.attestering.attestant,
                    eksterneIverksettingsteg = avslag.eksterneIverksettingsteg,
                )

            fun fromSøknadsbehandlingUtenBeregning(avslag: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) =
                UtenBeregning(
                    behandling = avslag,
                    behandlingsinformasjon = avslag.behandlingsinformasjon,
                    saksbehandler = avslag.saksbehandler,
                    attestant = avslag.attestering.attestant,
                    eksterneIverksettingsteg = avslag.eksterneIverksettingsteg,
                )
        }

        data class UtenBeregning(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt = Tidspunkt.now(),
            override val behandling: Behandling,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val eksterneIverksettingsteg: EksterneIverksettingsstegForAvslag,
        ) : AvslåttStønad() {
            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }

            // TODO jm: disse bør sannsynligvis peristeres.
            override val avslagsgrunner: List<Avslagsgrunn> = behandlingsinformasjon.utledAvslagsgrunner()
        }

        data class MedBeregning(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt = Tidspunkt.now(),
            override val behandling: Behandling,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            val beregning: Beregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val eksterneIverksettingsteg: EksterneIverksettingsstegForAvslag,
        ) : AvslåttStønad() {
            private val avslagsgrunnForBeregning: List<Avslagsgrunn> =
                when (val vurdering = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning)) {
                    is AvslagGrunnetBeregning.Ja -> listOf(vurdering.avslagsgrunn)
                    is AvslagGrunnetBeregning.Nei -> emptyList()
                }

            override fun accept(visitor: VedtakVisitor) {
                visitor.visit(this)
            }

            // TODO jm: disse bør sannsynligvis peristeres.
            override val avslagsgrunner: List<Avslagsgrunn> = behandlingsinformasjon.utledAvslagsgrunner() + avslagsgrunnForBeregning
        }
    }
}
