package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.zoneIdOslo
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadinnhold.ForNav
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import java.time.LocalDate
import java.util.UUID

enum class Sakstype(val value: String) {
    ALDER("alder"), UFØRE("uføre");

    companion object {
        fun from(value: String): Sakstype =
            when (value) {
                UFØRE.value -> UFØRE
                ALDER.value -> ALDER
                else -> {
                    throw IllegalArgumentException("Ukjent type angitt")
                }
            }
    }
}

sealed class Søknad {
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val sakId: UUID
    abstract val søknadInnhold: SøknadInnhold
    abstract val innsendtAv: NavIdentBruker

    val type: Sakstype by lazy {
        when (søknadInnhold) {
            is SøknadsinnholdAlder -> Sakstype.ALDER
            is SøknadsinnholdUføre -> Sakstype.UFØRE
        }
    }

    /**
     * Når Nav mottok søknaden:
     * * _opprettet_ dersom søknaden er digital.
     * * _søknadInnhold.forNav.mottaksdatoForSøknad_ dersom det er en papirsøknad.
     */
    @get:JsonIgnore // TODO jah: La de som serialiserer Søknad ha sin egen DTO.
    val mottaksdato: LocalDate by lazy {
        when (val søknadstype = søknadInnhold.forNav) {
            is ForNav.DigitalSøknad -> opprettet.toLocalDate(zoneIdOslo)
            is ForNav.Papirsøknad -> søknadstype.mottaksdatoForSøknad
        }
    }

    data class Ny(
        override val id: UUID,
        override val opprettet: Tidspunkt,
        override val sakId: UUID,
        override val søknadInnhold: SøknadInnhold,
        override val innsendtAv: NavIdentBruker,
    ) : Søknad() {

        fun journalfør(
            journalpostId: JournalpostId,
        ): Journalført.UtenOppgave {
            return Journalført.UtenOppgave(
                id = id,
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = søknadInnhold,
                innsendtAv = innsendtAv,
                journalpostId = journalpostId,
            )
        }
    }

    sealed class Journalført : Søknad() {
        abstract val journalpostId: JournalpostId

        data class UtenOppgave(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val sakId: UUID,
            override val søknadInnhold: SøknadInnhold,
            override val innsendtAv: NavIdentBruker,
            override val journalpostId: JournalpostId,
        ) : Journalført() {

            fun medOppgave(
                oppgaveId: OppgaveId,
            ): MedOppgave.IkkeLukket {
                return MedOppgave.IkkeLukket(
                    id = id,
                    opprettet = opprettet,
                    sakId = sakId,
                    søknadInnhold = søknadInnhold,
                    innsendtAv = innsendtAv,
                    journalpostId = journalpostId,
                    oppgaveId = oppgaveId,
                )
            }
        }

        sealed class MedOppgave : Journalført() {
            abstract val oppgaveId: OppgaveId

            data class IkkeLukket(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknadInnhold: SøknadInnhold,
                override val innsendtAv: NavIdentBruker,
                override val journalpostId: JournalpostId,
                override val oppgaveId: OppgaveId,
            ) : MedOppgave() {

                fun lukk(
                    lukketAv: Saksbehandler,
                    type: Lukket.LukketType,
                    lukketTidspunkt: Tidspunkt,
                ): Lukket {
                    return Lukket(
                        id = id,
                        opprettet = opprettet,
                        sakId = sakId,
                        søknadInnhold = søknadInnhold,
                        innsendtAv = innsendtAv,
                        journalpostId = journalpostId,
                        oppgaveId = oppgaveId,
                        lukketTidspunkt = lukketTidspunkt,
                        lukketAv = lukketAv,
                        lukketType = type,
                    )
                }
            }

            data class Lukket(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val sakId: UUID,
                override val søknadInnhold: SøknadInnhold,
                override val innsendtAv: NavIdentBruker,
                override val journalpostId: JournalpostId,
                override val oppgaveId: OppgaveId,
                val lukketTidspunkt: Tidspunkt,
                val lukketAv: Saksbehandler,
                val lukketType: LukketType,
            ) : MedOppgave() {

                enum class LukketType(val value: String) {
                    TRUKKET("TRUKKET"),
                    BORTFALT("BORTFALT"),
                    AVVIST("AVVIST");

                    override fun toString() = value
                }
            }
        }
    }
}
