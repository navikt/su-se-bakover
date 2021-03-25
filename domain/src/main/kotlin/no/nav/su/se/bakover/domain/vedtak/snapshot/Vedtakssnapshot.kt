package no.nav.su.se.bakover.domain.vedtak.snapshot

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import java.util.UUID

sealed class Vedtakssnapshot {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val søknadsbehandling: Søknadsbehandling.Iverksatt
    abstract val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon

    data class Avslag(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag,
        override val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
        val avslagsgrunner: List<Avslagsgrunn>
    ) : Vedtakssnapshot() {

        companion object {
            fun createFromBehandling(
                søknadsbehandling: Søknadsbehandling.Iverksatt.Avslag,
                avslagsgrunner: List<Avslagsgrunn>,
                journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
            ): Avslag = Avslag(
                UUID.randomUUID(),
                Tidspunkt.now(),
                søknadsbehandling = søknadsbehandling,
                avslagsgrunner = avslagsgrunner,
                journalføringOgBrevdistribusjon = journalføringOgBrevdistribusjon
            )
        }
    }

    data class Innvilgelse(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
        override val journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
        val utbetaling: Utbetaling
    ) : Vedtakssnapshot() {

        companion object {
            fun createFromBehandling(
                søknadsbehandling: Søknadsbehandling.Iverksatt.Innvilget,
                utbetaling: Utbetaling,
                journalføringOgBrevdistribusjon: JournalføringOgBrevdistribusjon,
            ): Innvilgelse = Innvilgelse(
                UUID.randomUUID(),
                Tidspunkt.now(),
                søknadsbehandling = søknadsbehandling,
                utbetaling = utbetaling,
                journalføringOgBrevdistribusjon = journalføringOgBrevdistribusjon
            )
        }
    }
}
