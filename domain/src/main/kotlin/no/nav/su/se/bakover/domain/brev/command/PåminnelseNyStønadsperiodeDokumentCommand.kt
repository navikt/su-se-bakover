package no.nav.su.se.bakover.domain.brev.command

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.jobcontext.SendPåminnelseNyStønadsperiodeContext.KunneIkkeSendePåminnelse
import person.domain.Person
import java.time.LocalDate

/**
 * Viktig informasjonsbrev
 */
data class PåminnelseNyStønadsperiodeDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    val utløpsdato: LocalDate,
    val uføreSomFyller67: Boolean,
) : GenererDokumentCommand {
    companion object {
        fun ny(sak: Sak, person: Person, utløpsdato: LocalDate): Either<KunneIkkeSendePåminnelse, PåminnelseNyStønadsperiodeDokumentCommand> {
            val uføreSomFyller67 = person.er67EllerEldre(utløpsdato.endOfMonth())
                ?: return KunneIkkeSendePåminnelse.PersonManglerFødselsdato.left()

            return PåminnelseNyStønadsperiodeDokumentCommand(
                fødselsnummer = sak.fnr,
                saksnummer = sak.saksnummer,
                sakstype = sak.type,
                utløpsdato = utløpsdato,
                uføreSomFyller67 = uføreSomFyller67,
            ).right()
        }
    }
}
