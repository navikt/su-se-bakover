package no.nav.su.se.bakover.domain.brev.command

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.jsonRequest.påminnelseNyStønadsperiodeBrev
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
    // Er en bruker med uføre som fyller 67 år i stønadsperiode sin siste måned
    val uføreSomFyller67: Boolean,
) : BrevmalCommand {
    override val saksbehandler: NavIdentBruker? get() = null
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<PåminnelseNyStønadsperiodeDokumentCommand> get() = påminnelseNyStønadsperiodeBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)

    companion object {
        fun ny(sak: Sak, person: Person, utløpsdato: LocalDate): Either<KunneIkkeSendePåminnelse, PåminnelseNyStønadsperiodeDokumentCommand> {
            val uføreSomFyller67 = when (sak.type) {
                Sakstype.ALDER -> false
                Sakstype.UFØRE -> {
                    person.er67EllerEldre(utløpsdato.endOfMonth())
                        ?: return KunneIkkeSendePåminnelse.PersonManglerFødselsdato.left()
                }
            }
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
