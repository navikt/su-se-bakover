package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

sealed class Vedtak {
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
    }

    sealed class AvslåttStønad : Vedtak() {
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
        ) : AvslåttStønad()

        data class MedBeregning(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt = Tidspunkt.now(),
            override val behandling: Behandling,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            val beregning: Beregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val eksterneIverksettingsteg: EksterneIverksettingsstegForAvslag,
        ) : AvslåttStønad()
    }
}
