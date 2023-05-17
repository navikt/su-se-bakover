package no.nav.su.se.bakover.utenlandsopphold.domain

import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon

// TODO: Mulig å lage en DatoIntervallisert interface for å få tilgang på DatoIntervall sine funksjoner
data class RegistrertUtenlandsopphold private constructor(
    val periode: DatoIntervall,
    val dokumentasjon: UtenlandsoppholdDokumentasjon,
    val journalposter: List<JournalpostId>,
    val begrunnelse: String?,
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
        fun create(
            periode: DatoIntervall,
            dokumentasjon: UtenlandsoppholdDokumentasjon,
            journalposter: List<JournalpostId>,
            begrunnelse: String?,
            opprettetAv: NavIdentBruker.Saksbehandler,
            opprettetTidspunkt: Tidspunkt,
            endretAv: NavIdentBruker.Saksbehandler,
            endretTidspunkt: Tidspunkt,
            erAnnullert: Boolean,
            versjon: Hendelsesversjon,
        ): RegistrertUtenlandsopphold {
            return RegistrertUtenlandsopphold(
                periode = periode,
                dokumentasjon = dokumentasjon,
                journalposter = journalposter,
                opprettetAv = opprettetAv,
                begrunnelse = begrunnelse,
                opprettetTidspunkt = opprettetTidspunkt,
                endretAv = endretAv,
                endretTidspunkt = endretTidspunkt,
                erAnnullert = erAnnullert,
                versjon = versjon,
            )
        }
    }
}
