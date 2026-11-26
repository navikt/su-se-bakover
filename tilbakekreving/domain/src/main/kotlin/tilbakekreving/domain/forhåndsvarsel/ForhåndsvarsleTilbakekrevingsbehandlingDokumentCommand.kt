package tilbakekreving.domain.forhåndsvarsel

import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.util.UUID

data class ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val correlationId: CorrelationId?,
    override val sakId: UUID,
    override val sakstype: Sakstype,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekst: String?,
    val kravgrunnlag: Kravgrunnlag?,
    val dødsbo: Boolean,
) : BrevmalCommand,
    SakshendelseCommand {
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<ForhåndsvarsleTilbakekrevingsbehandlingDokumentCommand> get() = forhåndsvarselTilbakekrevingBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    override val utførtAv: NavIdentBruker? = null
    override val brukerroller: List<Brukerrolle> = emptyList()
}
