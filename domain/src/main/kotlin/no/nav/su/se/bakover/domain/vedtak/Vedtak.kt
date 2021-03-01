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

interface IVedtakSomIkkeGirUtbetaling : IVedtak

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
            fun fromSøknadsbehandling(b: Søknadsbehandling.Iverksatt.Innvilget) = InnvilgetStønad(
                periode = b.beregning.getPeriode(),
                behandling = b,
                behandlingsinformasjon = b.behandlingsinformasjon,
                beregning = b.beregning,
                simulering = b.simulering,
                saksbehandler = b.saksbehandler,
                attestant = b.attestering.attestant,
                utbetalingId = b.utbetalingId,
                eksterneIverksettingsteg = b.eksterneIverksettingsteg,
            )

            fun fromRevurdering(r: IverksattRevurdering) = InnvilgetStønad(
                behandling = r,
                behandlingsinformasjon = r.tilRevurdering.behandlingsinformasjon,
                periode = r.beregning.getPeriode(),
                beregning = r.beregning,
                simulering = r.simulering,
                saksbehandler = r.saksbehandler,
                attestant = r.attestant,
                utbetalingId = r.utbetalingId,
                eksterneIverksettingsteg = r.eksterneIverksettingsteg
            )
        }
    }
}
