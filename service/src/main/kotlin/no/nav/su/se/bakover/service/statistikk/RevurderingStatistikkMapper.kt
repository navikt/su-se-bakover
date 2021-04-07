package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import java.time.Clock

internal class RevurderingStatistikkMapper(private val clock: Clock) {
    // Behandling er en avgjørelse i en Sak, knyttet til en konkret behandlingstype (eks. søknad, revurdering, endring, klage)."
    fun map(revurdering: Revurdering): Statistikk.Behandling {
        Statistikk.Behandling(
            behandlingType = Statistikk.Behandling.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.Behandling.BehandlingType.REVURDERING.beskrivelse,
            funksjonellTid = Tidspunkt.now(clock),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = revurdering.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = revurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = revurdering.id,
            sakId = revurdering.sakId,
            saksnummer = revurdering.saksnummer.nummer,
            behandlingStatus = BehandlingStatusMapper.map(revurdering),
            behandlingStatusBeskrivelse = BehandlingStatusBeskrivelseMapper.map(revurdering),
            versjon = clock.millis(),
            saksbehandler = revurdering.saksbehandler.navIdent,
            relatertBehandlingId = revurdering.tilRevurdering.id,
            avsluttet = false
        ).apply {
            return when (revurdering) {
                is OpprettetRevurdering -> this
                is RevurderingTilAttestering -> this
                is IverksattRevurdering -> {
                    copy(
                        resultat = when (revurdering) {
                            is IverksattRevurdering.Innvilget -> "Innvilget"
                            is IverksattRevurdering.Opphørt -> "Opphørt"
                            is IverksattRevurdering.IngenEndring -> "Uendret"
                        },
                        resultatBegrunnelse = "Endring i søkers inntekt", // TODO ai: Må støtte flere grunner for revurdering senare
                        beslutter = revurdering.attestering.attestant.navIdent,
                        avsluttet = true
                    )
                }
                is UnderkjentRevurdering -> {
                    copy(
                        beslutter = revurdering.attestering.attestant.navIdent
                    )
                }
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
        }
    }

    internal object BehandlingStatusMapper {
        fun map(revurdering: Revurdering): String =
            when (revurdering) {
                is OpprettetRevurdering -> "OPPRETTET"
                is IverksattRevurdering.Innvilget -> "IVERKSATT_INNVILGET"
                is IverksattRevurdering.Opphørt -> "IVERKSATT_OPPHØRT"
                is IverksattRevurdering.IngenEndring -> "IVERKSATT_INGEN_ENDRING"
                is RevurderingTilAttestering.Innvilget -> "TIL_ATTESTERING_INNVILGET"
                is RevurderingTilAttestering.Opphørt -> "TIL_ATTESTERING_OPPHØRT"
                is RevurderingTilAttestering.IngenEndring -> "TIL_ATTESTERING_INGEN_ENDRING"
                is UnderkjentRevurdering.Innvilget -> "UNDERKJENT_INNVILGET"
                is UnderkjentRevurdering.Opphørt -> "UNDERKJENT_OPPHØRT"
                is UnderkjentRevurdering.IngenEndring -> "UNDERKJENT_INGEN_ENDRING"
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
    }

    internal object BehandlingStatusBeskrivelseMapper {
        fun map(revurdering: Revurdering): String =
            when (revurdering) {
                is OpprettetRevurdering -> "Ny revurdering opprettet"
                is RevurderingTilAttestering.Innvilget -> "Innvilget revurdering sendt til attestering"
                is RevurderingTilAttestering.Opphørt -> "Opphørt revurdering sendt til attestering"
                is RevurderingTilAttestering.IngenEndring -> "Revurdering uten endring i ytelse sendt til attestering"
                is IverksattRevurdering.Innvilget -> "Innvilget revurdering iverksatt"
                is IverksattRevurdering.Opphørt -> "Opphørt revurdering iverksatt"
                is IverksattRevurdering.IngenEndring -> "Revurdering uten endring i ytelse iverksatt"
                is UnderkjentRevurdering.Innvilget -> "Innvilget revurdering underkjent"
                is UnderkjentRevurdering.Opphørt -> "Opphørt revurdering underkjent"
                is UnderkjentRevurdering.IngenEndring -> "Revurdering uten endring i ytelse underkjent"
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
    }
}
