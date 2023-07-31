package no.nav.su.se.bakover.domain.brev.command

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Saksnummer
import java.time.LocalDate

/**
 * Viktig informasjonsbrev
 */
data class PåminnelseNyStønadsperiodeDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val utløpsdato: LocalDate,
    val halvtGrunnbeløp: Int,
) : GenererDokumentCommand
