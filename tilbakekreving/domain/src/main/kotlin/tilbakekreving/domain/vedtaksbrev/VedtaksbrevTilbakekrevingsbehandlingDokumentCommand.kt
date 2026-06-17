package tilbakekreving.domain.vedtaksbrev

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.hendelse.domain.SakshendelseCommand
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.LocalDate
import java.util.UUID

/**
 * @property attestant er null når saksbehandler skal forhåndsvise brevet.
 */
sealed interface VedtaksbrevTilbakekrevingsbehandlingDokumentCommand :
    GenererDokumentCommand,
    SakshendelseCommand {
    val saksbehandler: NavIdentBruker
    val attestant: NavIdentBruker?
    val fritekst: String?
    val vurderingerMedKrav: VurderingerMedKrav
    val skalTilbakekreve: Boolean

    data class Vanlig(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val correlationId: CorrelationId?,
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker,
        override val attestant: NavIdentBruker?,
        override val fritekst: String?,
        override val vurderingerMedKrav: VurderingerMedKrav,
        override val skalTilbakekreve: Boolean,
    ) : VedtaksbrevTilbakekrevingsbehandlingDokumentCommand {
        override val utførtAv: NavIdentBruker = saksbehandler
        override val brukerroller: List<Brukerrolle> = emptyList()
    }

    data class Dødsbo(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val correlationId: CorrelationId?,
        override val sakId: UUID,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker,
        override val attestant: NavIdentBruker?,
        override val fritekst: String?,
        override val vurderingerMedKrav: VurderingerMedKrav,
        override val skalTilbakekreve: Boolean,
        val periode: DatoIntervall,
        val forhåndsvarselsDato: LocalDate,
    ) : VedtaksbrevTilbakekrevingsbehandlingDokumentCommand {
        override val utførtAv: NavIdentBruker = saksbehandler
        override val brukerroller: List<Brukerrolle> = emptyList()
    }
}
