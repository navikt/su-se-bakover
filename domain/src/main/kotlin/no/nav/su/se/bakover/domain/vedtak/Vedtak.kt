package no.nav.su.se.bakover.domain.vedtak

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

interface IBehandling {
    val id: UUID
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
}

interface IVedtak {
    val id: UUID
    val opprettet: Tidspunkt
    val periode: Periode
    val behandling: IBehandling
    val behandlingsinformasjon: Behandlingsinformasjon
}

interface IVedtakSomGirUtbetaling : IVedtak {
    val beregning: Beregning
    val simulering: Simulering
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val utbetalingId: UUID30
    val eksterneIverksettingsteg: EksterneIverksettingsstegEtterUtbetaling
}

interface IVedtakSomIkkeGirUtbetaling : IVedtak {
    val saksbehandler: NavIdentBruker.Saksbehandler
    val attestant: NavIdentBruker.Attestant
    val eksterneIverksettingsteg: EksterneIverksettingsstegForAvslag
}

sealed class Vedtak : IVedtak {
    data class InnvilgetStønad(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val periode: Periode,
        override val behandling: IBehandling,
        override val behandlingsinformasjon: Behandlingsinformasjon,
        override val beregning: Beregning,
        override val simulering: Simulering,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant,
        override val utbetalingId: UUID30,
        override val eksterneIverksettingsteg: EksterneIverksettingsstegEtterUtbetaling,
    ) : Vedtak(), IVedtakSomGirUtbetaling {
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

    sealed class AvslåttStønad : Vedtak(), IVedtakSomIkkeGirUtbetaling {
        companion object {
            fun fromSøknadsbehandlingMedBeregning(avslag: Søknadsbehandling.Iverksatt.Avslag.MedBeregning) =
                MedBeregning(
                    periode = avslag.beregning.getPeriode(),
                    behandling = avslag,
                    behandlingsinformasjon = avslag.behandlingsinformasjon,
                    beregning = avslag.beregning,
                    saksbehandler = avslag.saksbehandler,
                    attestant = avslag.attestering.attestant,
                    eksterneIverksettingsteg = avslag.eksterneIverksettingsteg,
                )

            // TODO fix periode for avslag uten beregning
            // fun fromSøknadsbehandlingUtenBeregning(avslag: Søknadsbehandling.Iverksatt.Avslag.UtenBeregning) = UtenBeregning(
            //     periode = avslag.beregning.getPeriode(),
            //     behandling = avslag,
            //     behandlingsinformasjon = avslag.behandlingsinformasjon,
            //     saksbehandler = avslag.saksbehandler,
            //     attestant = avslag.attestering.attestant,
            //     eksterneIverksettingsteg = avslag.eksterneIverksettingsteg,
            // )
        }

        data class UtenBeregning(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt = Tidspunkt.now(),
            override val periode: Periode,
            override val behandling: IBehandling,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val eksterneIverksettingsteg: EksterneIverksettingsstegForAvslag,
        ) : AvslåttStønad()

        data class MedBeregning(
            override val id: UUID = UUID.randomUUID(),
            override val opprettet: Tidspunkt = Tidspunkt.now(),
            override val periode: Periode,
            override val behandling: IBehandling,
            override val behandlingsinformasjon: Behandlingsinformasjon,
            val beregning: Beregning,
            override val saksbehandler: NavIdentBruker.Saksbehandler,
            override val attestant: NavIdentBruker.Attestant,
            override val eksterneIverksettingsteg: EksterneIverksettingsstegForAvslag,
        ) : AvslåttStønad()
    }
}
