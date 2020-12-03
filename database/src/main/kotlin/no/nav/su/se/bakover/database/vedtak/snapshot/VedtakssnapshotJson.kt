package no.nav.su.se.bakover.database.vedtak.snapshot

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.Avslag.Companion.toAvslagsJson
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.BehandlingSnapshotJson.Companion.toJson
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.Innvilgelse.Companion.toInnvilgelseJson
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot

/**
 * Visse felter er implisitt nullable for å støtte Behandling i nåværende tilstand.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = VedtakssnapshotJson.Avslag::class, name = "Avslag"),
    JsonSubTypes.Type(value = VedtakssnapshotJson.Innvilgelse::class, name = "Innvilgelse"),
)
internal sealed class VedtakssnapshotJson {
    abstract val id: String
    abstract val opprettet: String // Vi har latt Jackson formatere denne i andre tilfeller.
    abstract val behandling: BehandlingSnapshotJson
    abstract val type: String

    companion object {
        fun Vedtakssnapshot.toJson(): VedtakssnapshotJson {
            return when (val v = this) {
                is Vedtakssnapshot.Avslag -> toAvslagsJson(v)
                is Vedtakssnapshot.Innvilgelse -> toInnvilgelseJson(v)
            }
        }
    }

    data class Avslag(
        override val id: String,
        override val opprettet: String,
        val avslagsgrunner: List<String>,
        override val behandling: BehandlingSnapshotJson,
    ) : VedtakssnapshotJson() {
        override val type = "avslag"
        companion object {
            internal fun toAvslagsJson(avslag: Vedtakssnapshot.Avslag): Avslag {
                return Avslag(
                    id = avslag.id.toString(),
                    opprettet = avslag.opprettet.toString(),
                    behandling = avslag.behandling.toJson(),
                    avslagsgrunner = avslag.avslagsgrunner.map { it.toString() }
                )
            }
        }
    }

    data class Innvilgelse(
        override val id: String,
        override val opprettet: String,
        override val behandling: BehandlingSnapshotJson,
        val utbetaling: Utbetaling,
    ) : VedtakssnapshotJson() {
        override val type = "innvilgelse"

        companion object {
            internal fun toInnvilgelseJson(innvilgelse: Vedtakssnapshot.Innvilgelse): Innvilgelse {
                return Innvilgelse(
                    id = innvilgelse.id.toString(),
                    opprettet = innvilgelse.opprettet.toString(),
                    behandling = innvilgelse.behandling.toJson(),
                    utbetaling = innvilgelse.utbetaling
                )
            }
        }
    }

    data class BehandlingSnapshotJson(
        val id: String,
        val opprettet: Tidspunkt,
        val sakId: String,
        val saksnummer: Long,
        val fnr: String,
        val status: String,
        val saksbehandler: String?,
        val attestant: String?,
        val oppgaveId: String,
        val iverksattJournalpostId: String?,
        val iverksattBrevbestillingId: String?,
        val beregning: PersistertBeregning?,
        val behandlingsinformasjon: Behandlingsinformasjon,
        val søknad: Søknad.Journalført.MedOppgave,
    ) {
        companion object {
            fun Behandling.toJson(): BehandlingSnapshotJson {
                return BehandlingSnapshotJson(
                    id = id.toString(),
                    opprettet = opprettet,
                    sakId = sakId.toString(),
                    saksnummer = saksnummer.nummer,
                    fnr = fnr.toString(),
                    status = status().toString(),
                    saksbehandler = saksbehandler()?.toString(),
                    attestant = attestant()?.toString(),
                    oppgaveId = oppgaveId().toString(),
                    iverksattJournalpostId = iverksattJournalpostId()?.toString(),
                    iverksattBrevbestillingId = iverksattBrevbestillingId()?.toString(),
                    beregning = beregning()?.toSnapshot(),
                    behandlingsinformasjon = behandlingsinformasjon(),
                    søknad = søknad
                )
            }
        }
    }
}
