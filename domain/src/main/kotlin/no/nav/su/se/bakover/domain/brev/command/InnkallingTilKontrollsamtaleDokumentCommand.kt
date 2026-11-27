package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Dokumenttype
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import dokument.domain.generer.bygger.maler.InnkallingTilKontrollsamtalePdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr

/**
 * Viktig informasjonsbrev
 */
data class InnkallingTilKontrollsamtaleDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
) : BrevmalCommand {
    override val saksbehandler: NavIdentBruker? get() = null
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<InnkallingTilKontrollsamtaleDokumentCommand> get() = innkallingTilKontrollsamtaleBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
}

val innkallingTilKontrollsamtaleBrev = Brevmal<InnkallingTilKontrollsamtaleDokumentCommand>(
    dokumenttype = Dokumenttype.INFORMASJON_VIKTIG,
    brevtype = Brevtype.INNKALLING_KONTROLLSAMTALE,
) { felles, _ ->
    InnkallingTilKontrollsamtalePdfInnhold(personalia = felles.personalia, sakstype = felles.sakstype)
}
