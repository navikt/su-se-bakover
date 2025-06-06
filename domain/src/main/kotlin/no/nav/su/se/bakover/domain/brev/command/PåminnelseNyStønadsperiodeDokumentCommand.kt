package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import java.time.LocalDate

/**
 * Viktig informasjonsbrev
 */
data class PåminnelseNyStønadsperiodeDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    val utløpsdato: LocalDate,
    val halvtGrunnbeløp: Int,
) : GenererDokumentCommand
