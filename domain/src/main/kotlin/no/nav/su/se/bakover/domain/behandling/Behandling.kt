package no.nav.su.se.bakover.domain.behandling

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

interface Behandling {
    val id: UUID
    val opprettet: Tidspunkt
    val sakId: UUID
    val saksnummer: Saksnummer
    val fnr: Fnr
    val oppgaveId: OppgaveId
    val periode: Periode
    val grunnlagsdata: Grunnlagsdata
    val vilkårsvurderinger: Vilkårsvurderinger
}

data class ÅpenBehandling(
    val åpenBehandlingType: ÅpenBehandlingType,
    val status: ÅpenBehandlingStatus,
    val opprettet: Tidspunkt,
)

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
                is Søknadsbehandling.Beregnet.Avslag,
                is Søknadsbehandling.Beregnet.Innvilget,
                is Søknadsbehandling.Simulert,
                is Søknadsbehandling.Vilkårsvurdert.Avslag,
                is Søknadsbehandling.Vilkårsvurdert.Innvilget,
                is Søknadsbehandling.Vilkårsvurdert.Uavklart,
                -> UNDER_BEHANDLING

                is Søknadsbehandling.TilAttestering.Avslag.MedBeregning,
                is Søknadsbehandling.TilAttestering.Avslag.UtenBeregning,
                is Søknadsbehandling.TilAttestering.Innvilget,
                -> TIL_ATTESTERING

                is Søknadsbehandling.Underkjent.Avslag.MedBeregning,
                is Søknadsbehandling.Underkjent.Avslag.UtenBeregning,
                is Søknadsbehandling.Underkjent.Innvilget,
                -> UNDERKJENT

                is Søknadsbehandling.Iverksatt.Avslag.MedBeregning,
                is Søknadsbehandling.Iverksatt.Avslag.UtenBeregning,
                is Søknadsbehandling.Iverksatt.Innvilget,
                -> throw IllegalStateException("Vi skal ikke ha noen iverksatte søknadsbehandlinger her. se på hentÅpneSøknadsbehandlinger()")
            }
        }

        fun revurderingTilStatus(revurdering: Revurdering): ÅpenBehandlingStatus {
            return when (revurdering) {
                is OpprettetRevurdering,
                is BeregnetRevurdering.IngenEndring,
                is BeregnetRevurdering.Innvilget,
                is BeregnetRevurdering.Opphørt,
                is SimulertRevurdering.Innvilget,
                is SimulertRevurdering.Opphørt,
                -> UNDER_BEHANDLING

                is RevurderingTilAttestering.IngenEndring,
                is RevurderingTilAttestering.Innvilget,
                is RevurderingTilAttestering.Opphørt,
                -> TIL_ATTESTERING

                is UnderkjentRevurdering.IngenEndring,
                is UnderkjentRevurdering.Innvilget,
                is UnderkjentRevurdering.Opphørt,
                -> UNDERKJENT

                is IverksattRevurdering.IngenEndring,
                is IverksattRevurdering.Innvilget,
                is IverksattRevurdering.Opphørt,
                -> throw IllegalStateException("Vi skal ikke ha noen iverksatte revurderinger her. se på hentÅpneRevurderinger()")
            }
        }
    }
}
