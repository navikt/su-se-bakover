package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad.Lukket.LukketType
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import java.util.UUID

sealed class Søknad {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val sakId: UUID
    abstract val søknadInnhold: SøknadInnhold

    abstract fun lukk(
        lukketAv: Saksbehandler,
        type: LukketType,
        lukketTidspunkt: Tidspunkt,
    ): Lukket

    data class Ny(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val søknadInnhold: SøknadInnhold,
    ) : Søknad() {

        override fun lukk(
            lukketAv: Saksbehandler,
            type: LukketType,
            lukketTidspunkt: Tidspunkt,
        ): Lukket {
            return Lukket(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
                journalpostId = null,
                oppgaveId = null,
                lukketAv = lukketAv,
                lukketType = type,
                lukketTidspunkt = lukketTidspunkt,
            )
        }

        fun journalfør(
            journalpostId: JournalpostId,
        ): Journalført.UtenOppgave {
            return Journalført.UtenOppgave(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
                journalpostId = journalpostId,
            )
        }
    }

    data class Lukket(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val søknadInnhold: SøknadInnhold,
        // Det er mulig å lukke søknader uten at de er journalført og/eller laget oppgave.
        val journalpostId: JournalpostId?,
        val oppgaveId: OppgaveId?,
        val lukketTidspunkt: Tidspunkt,
        val lukketAv: Saksbehandler,
        val lukketType: LukketType,
    ) : Søknad() {

        enum class LukketType(val value: String) {
            TRUKKET("TRUKKET"),
            BORTFALT("BORTFALT"),
            AVVIST("AVVIST");

            override fun toString() = value
        }

        /**
         * Returnerer seg selv.
         */
        override fun lukk(
            lukketAv: Saksbehandler,
            type: LukketType,
            lukketTidspunkt: Tidspunkt,
        ) = this
    }

    sealed class Journalført : Søknad() {
        abstract val journalpostId: JournalpostId

        data class UtenOppgave(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val søknadInnhold: SøknadInnhold,
            override val journalpostId: JournalpostId,
        ) : Journalført() {

            override fun lukk(
                lukketAv: Saksbehandler,
                type: LukketType,
                lukketTidspunkt: Tidspunkt,
            ): Lukket {
                return Lukket(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    søknadInnhold = søknadInnhold,
                    journalpostId = journalpostId,
                    oppgaveId = null,
                    lukketTidspunkt = lukketTidspunkt,
                    lukketAv = lukketAv,
                    lukketType = type,
                )
            }

            fun medOppgave(
                oppgaveId: OppgaveId,
            ): MedOppgave {
                return MedOppgave(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    søknadInnhold = søknadInnhold,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                )
            }
        }

        data class MedOppgave(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val søknadInnhold: SøknadInnhold,
            override val journalpostId: JournalpostId,
            val oppgaveId: OppgaveId,
        ) : Journalført() {
            override fun lukk(
                lukketAv: Saksbehandler,
                type: LukketType,
                lukketTidspunkt: Tidspunkt,
            ): Lukket {
                return Lukket(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    søknadInnhold = søknadInnhold,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                    lukketTidspunkt = lukketTidspunkt,
                    lukketAv = lukketAv,
                    lukketType = type,
                )
            }
        }
    }
}
