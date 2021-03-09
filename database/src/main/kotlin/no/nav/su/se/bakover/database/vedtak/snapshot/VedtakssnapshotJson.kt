package no.nav.su.se.bakover.database.vedtak.snapshot

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.beregning.PersistertBeregning
import no.nav.su.se.bakover.database.beregning.toSnapshot
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.Avslag.Companion.toAvslagsJson
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.BehandlingSnapshotJson.Companion.toJson
import no.nav.su.se.bakover.database.vedtak.snapshot.VedtakssnapshotJson.Innvilgelse.Companion.toInnvilgelseJson
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot

internal sealed class VedtakssnapshotJson {
    abstract val id: String
    abstract val opprettet: String
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
                    behandling = avslag.søknadsbehandling.toJson(),
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
                    behandling = innvilgelse.søknadsbehandling.toJson(),
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
        val saksbehandler: String,
        val attestering: Attestering,
        val oppgaveId: String,
        val iverksattJournalpostId: String?, // kan være null siden den kan feile og så gjøres async
        val iverksattBrevbestillingId: String?, // kan være null siden den kan feile og så gjøres async
        val beregning: PersistertBeregning?, // Avslag.UtenBeregning har ikke beregning
        val behandlingsinformasjon: Behandlingsinformasjon,
        val behandlingsresultat: BehandlingsresultatJson,
        val søknad: Søknad.Journalført.MedOppgave,
        val simulering: Simulering?, // Kun Innvilget har simulering
    ) {
        companion object {
            fun Søknadsbehandling.Iverksatt.Innvilget.toJson(): BehandlingSnapshotJson {
                return BehandlingSnapshotJson(
                    id = id.toString(),
                    opprettet = opprettet,
                    sakId = sakId.toString(),
                    saksnummer = saksnummer.nummer,
                    fnr = fnr.toString(),
                    status = status.toString(),
                    saksbehandler = saksbehandler.toString(),
                    attestering = attestering,
                    oppgaveId = oppgaveId.toString(),
                    // TODO jah: Denne vil alltid være null for innvilgelse siden vi ikke gjør dette før vi får kvitteringen
                    iverksattJournalpostId = eksterneIverksettingsteg.journalpostId()?.toString(),
                    // TODO jah: Denne vil alltid være null for innvilgelse siden vi ikke gjør dette før vi får kvitteringen
                    iverksattBrevbestillingId = (eksterneIverksettingsteg as? JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev)?.brevbestillingId?.toString(),
                    beregning = beregning.toSnapshot(),
                    behandlingsinformasjon = behandlingsinformasjon,
                    behandlingsresultat = BehandlingsresultatJson(
                        sats = behandlingsinformasjon.utledSats().orNull()?.toString(),
                        satsgrunn = behandlingsinformasjon.getSatsgrunn().orNull()?.toString()
                    ),
                    søknad = søknad,
                    simulering = simulering,
                )
            }

            fun Søknadsbehandling.Iverksatt.Avslag.toJson(): BehandlingSnapshotJson {
                return BehandlingSnapshotJson(
                    id = id.toString(),
                    opprettet = opprettet,
                    sakId = sakId.toString(),
                    saksnummer = saksnummer.nummer,
                    fnr = fnr.toString(),
                    status = status.toString(),
                    saksbehandler = saksbehandler.toString(),
                    attestering = attestering,
                    oppgaveId = oppgaveId.toString(),
                    iverksattJournalpostId = eksterneIverksettingsteg.journalpostId()?.toString(),
                    iverksattBrevbestillingId = (eksterneIverksettingsteg as? JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev)?.brevbestillingId?.toString(),
                    beregning = if (this is Søknadsbehandling.Iverksatt.Avslag.MedBeregning) beregning.toSnapshot() else null,
                    behandlingsinformasjon = behandlingsinformasjon,
                    behandlingsresultat = BehandlingsresultatJson(
                        sats = behandlingsinformasjon.utledSats().orNull()?.toString(),
                        satsgrunn = behandlingsinformasjon.getSatsgrunn().orNull()?.toString()
                    ),
                    søknad = søknad,
                    simulering = null,
                )
            }
        }

        data class BehandlingsresultatJson(
            val sats: String?,
            val satsgrunn: String?
        )
    }
}
