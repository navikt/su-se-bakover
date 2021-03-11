package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import java.time.Clock

internal class RevurderingStatistikkMapper(private val clock: Clock) {
    // Behandling er en avgjørelse i en Sak, knyttet til en konkret behandlingstype (eks. søknad, revurdering, endring, klage)."
    fun map(revurdering: Revurdering): Statistikk.Behandling {
        Statistikk.Behandling(
            behandlingType = Statistikk.BehandlingType.REVURDERING,
            behandlingTypeBeskrivelse = Statistikk.BehandlingType.REVURDERING.beskrivelse,
            funksjonellTid = FunksjonellTidMapper.map(revurdering),
            tekniskTid = Tidspunkt.now(clock),
            registrertDato = revurdering.opprettet.toLocalDate(zoneIdOslo),
            mottattDato = revurdering.opprettet.toLocalDate(zoneIdOslo),
            behandlingId = revurdering.id,
            sakId = revurdering.sakId,
            saksnummer = revurdering.saksnummer.nummer,
            behandlingStatus = revurdering::class.simpleName!!,
            behandlingStatusBeskrivelse = BehandlingStatusBeskrivelseMapper.map(revurdering),
            versjon = clock.millis(),
            saksbehandler = revurdering.saksbehandler.navIdent,
            relatertBehandlingId = revurdering.tilRevurdering.id
        ).apply {
            return when (revurdering) {
                is OpprettetRevurdering -> this
                is RevurderingTilAttestering -> this
                is IverksattRevurdering -> {
                    copy(
                        resultat = "Innvilget",
                        resultatBegrunnelse = "Endring i søkers inntekt", // TODO ai: Må støtte flere grunner for revurdering senare
                        beslutter = revurdering.attestering.attestant.navIdent
                    )
                }
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
        }
    }

    internal object FunksjonellTidMapper {
        fun map(revurdering: Revurdering) = when (revurdering) {
            is OpprettetRevurdering -> revurdering.opprettet
            is RevurderingTilAttestering -> revurdering.beregning.startOfFirstDay()
            is IverksattRevurdering -> revurdering.beregning.startOfFirstDay()
            else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
        }

        private fun Beregning.startOfFirstDay() = getPeriode().getFraOgMed().startOfDay(zoneIdOslo)
    }

    internal object BehandlingStatusBeskrivelseMapper {
        fun map(revurdering: Revurdering): String =
            when (revurdering) {
                is OpprettetRevurdering -> "Ny revurdering opprettet"
                is RevurderingTilAttestering -> "Revurdering sendt til attestering"
                is IverksattRevurdering -> "Revurdering iverksatt"
                else -> throw ManglendeStatistikkMappingException(this, revurdering::class.java)
            }
    }
}
