package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import java.time.Clock
import java.util.UUID

data class RegistrertUtenlandsopphold private constructor(
    val id: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val opprettetTidspunkt: Tidspunkt,
    val endretAv: NavIdentBruker.Saksbehandler,
    val endretTidspunkt: Tidspunkt,
    // TODO jah: Bytt denne til versjonstypen som ligger i hendelse (krever oppsplitting av den modulen).
    val versjon: Long,
    // TODO jah: Tenk litt mer over variabelnavnet. Burde det heller vært en sealed class/interface?
    val erGyldig: Boolean,
) {
    /**
     * Trekker fra utreise- og innreisedag.
     */
    val antallDager
        get() = (periode.antallDager() - 2).coerceAtLeast(0)

    companion object {
        fun registrer(
            id: UUID = UUID.randomUUID(),
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            opprettetAv: NavIdentBruker.Saksbehandler,
            clock: Clock,
        ): RegistrertUtenlandsopphold {
            val now = Tidspunkt.now(clock)
            return RegistrertUtenlandsopphold(
                id = id,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                opprettetAv = opprettetAv,
                opprettetTidspunkt = now,
                endretAv = opprettetAv,
                endretTidspunkt = now,
                erGyldig = true,
                versjon = 1,
            )
        }
    }
}

val List<RegistrertUtenlandsopphold>.antallDager
    get() = this.sumOf { it.antallDager }
