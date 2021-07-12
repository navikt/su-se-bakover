package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

data class ÅpenBehandling(
    val saksnummer: Saksnummer,
    val behandlingsId: UUID,
    val åpenBehandlingType: ÅpenBehandlingType,
    val status: ÅpenBehandlingStatus,
    val opprettet: Tidspunkt,
) {
    companion object {
        fun Søknad.tilÅpenBehandling(saksnummer: Saksnummer): ÅpenBehandling = ÅpenBehandling(
            saksnummer = saksnummer,
            behandlingsId = id,
            åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
            status = ÅpenBehandlingStatus.NY_SØKNAD,
            opprettet = opprettet,
        )

        fun Søknadsbehandling.tilÅpenBehandling(): ÅpenBehandling =
            ÅpenBehandling(
                saksnummer = saksnummer,
                behandlingsId = id,
                åpenBehandlingType = ÅpenBehandlingType.SØKNADSBEHANDLING,
                status = ÅpenBehandlingStatus.søknadsbehandlingTilStatus(this),
                opprettet = opprettet,
            )

        fun Revurdering.tilÅpenBehandling(): ÅpenBehandling = ÅpenBehandling(
            saksnummer = saksnummer,
            behandlingsId = id,
            åpenBehandlingType = ÅpenBehandlingType.REVURDERING,
            status = ÅpenBehandlingStatus.revurderingTilStatus(this),
            opprettet = opprettet,
        )
    }
}

enum class ÅpenBehandlingType {
    SØKNADSBEHANDLING,
    REVURDERING
}

enum class ÅpenBehandlingStatus {
    UNDER_BEHANDLING,
    NY_SØKNAD,
    UNDERKJENT,
    TIL_ATTESTERING;

    companion object {
        fun søknadsbehandlingTilStatus(søknadsbehandling: Søknadsbehandling): ÅpenBehandlingStatus {
            return when (søknadsbehandling) {
                is Søknadsbehandling.Beregnet,
                is Søknadsbehandling.Simulert,
                is Søknadsbehandling.Vilkårsvurdert,
                -> UNDER_BEHANDLING

                is Søknadsbehandling.TilAttestering -> TIL_ATTESTERING
                is Søknadsbehandling.Underkjent -> UNDERKJENT
                is Søknadsbehandling.Iverksatt -> throw IllegalStateException("Vi skal ikke ha noen iverksatte søknadsbehandlinger her. se på hentÅpneSøknadsbehandlinger()")
            }
        }

        fun revurderingTilStatus(revurdering: Revurdering): ÅpenBehandlingStatus {
            return when (revurdering) {
                is OpprettetRevurdering,
                is BeregnetRevurdering,
                is SimulertRevurdering,
                -> UNDER_BEHANDLING

                is RevurderingTilAttestering -> TIL_ATTESTERING
                is UnderkjentRevurdering -> UNDERKJENT
                is IverksattRevurdering -> throw IllegalStateException("Vi skal ikke ha noen iverksatte revurderinger her. se på hentÅpneRevurderinger()")
            }
        }
    }
}
