package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.jsonRequest.klageAvvistBrev
import no.nav.su.se.bakover.domain.brev.jsonRequest.klageOversendtBrev
import java.time.LocalDate

sealed interface KlageDokumentCommand : BrevmalCommand {

    /**
     * Førsteinstansen opprettholder et enkeltvedtak. Da sendes det en innstilling til klageinstansen. Det samme brevet sendes som informasjon til bruker (siden de ikke kan agere på dette er det ikke annotert som viktig).
     */
    data class OpprettholdEllerDelvisOmgjøring(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        val fritekst: String,
        val klageDato: LocalDate,
        val vedtaksbrevDato: LocalDate?,
        val dødsbo: Boolean,
    ) : KlageDokumentCommand {
        override val mal: Brevmal<OpprettholdEllerDelvisOmgjøring> get() = klageOversendtBrev
        override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    }

    /**
     * Når vi avviser en klage sendes det et enkeltvedtak til bruker.
     */
    data class Avvist(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        override val saksbehandler: NavIdentBruker.Saksbehandler,
        override val attestant: NavIdentBruker.Attestant?,
        val fritekst: String,
        val dødsbo: Boolean,
    ) : KlageDokumentCommand {
        override val mal: Brevmal<Avvist> get() = klageAvvistBrev
        override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
    }
}
