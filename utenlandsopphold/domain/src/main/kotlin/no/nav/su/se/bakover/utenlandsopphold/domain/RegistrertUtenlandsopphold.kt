package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.application.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import java.util.UUID

/**
 *  @param utenlandsoppholdId Identifiserer et utenlandsopphold (følger registreringen og endringer).
 */
// TODO: Mulig å lage en DatoIntervallisert interface for å få tilgang på DatoIntervall sine funksjoner
data class RegistrertUtenlandsopphold private constructor(
    val utenlandsoppholdId: UUID,
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val opprettetAv: NavIdentBruker.Saksbehandler,
    val opprettetTidspunkt: Tidspunkt,
    val endretAv: NavIdentBruker.Saksbehandler,
    val endretTidspunkt: Tidspunkt,
    val versjon: Hendelsesversjon,
    val erAnnullert: Boolean,
) {

    /**
     * Trekker fra utreise- og innreisedag.
     */
    val antallDager
        get() = (periode.antallDager() - 2).coerceAtLeast(0)

    companion object {
        fun fraHendelse(
            utenlandsoppholdId: UUID,
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            opprettetAv: NavIdentBruker.Saksbehandler,
            opprettetTidspunkt: Tidspunkt,
            endretAv: NavIdentBruker.Saksbehandler,
            endretTidspunkt: Tidspunkt,
            erAnnullert: Boolean,
            versjon: Hendelsesversjon,
        ): RegistrertUtenlandsopphold {
            return RegistrertUtenlandsopphold(
                utenlandsoppholdId = utenlandsoppholdId,
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                opprettetAv = opprettetAv,
                opprettetTidspunkt = opprettetTidspunkt,
                endretAv = endretAv,
                endretTidspunkt = endretTidspunkt,
                erAnnullert = erAnnullert,
                versjon = versjon,
            )
        }
    }
}

val List<RegistrertUtenlandsopphold>.antallDager
    get() = this.sumOf { it.antallDager }
