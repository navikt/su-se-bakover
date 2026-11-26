package dokument.domain.generer

import dokument.domain.Brevtype
import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr

data class Felles(
    val sakstype: Sakstype,
    val personalia: PersonaliaPdfInnhold,
    val saksbehandlerNavn: String,
    val attestantNavn: String?,
)

enum class Dokumenttype { VEDTAK, INFORMASJON_VIKTIG, INFORMASJON_ANNET }

class Brevmal<F>(
    val dokumenttype: Dokumenttype,
    val brevtype: Brevtype,
    val lagInnhold: (Felles, F) -> PdfInnhold,
)

data class BrevmalCommand<F>(
    val mal: Brevmal<F>,
    val fakta: F,
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    val saksbehandler: NavIdentBruker?,
    val attestant: NavIdentBruker?,
) : GenererDokumentCommand {
    fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, fakta)
}
