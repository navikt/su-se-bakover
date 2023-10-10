package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import java.time.LocalDate

sealed class KlageDokumentCommand : GenererDokumentCommand {

    /**
     * Førsteinstansen opprettholder et enkeltvedtak. Da sendes det en innstilling til klageinstansen. Det samme brevet sendes som informasjon til bruker (siden de ikke kan agere på dette er det ikke annotert som viktig).
     */
    data class Oppretthold(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val attestant: NavIdentBruker.Attestant?,
        val fritekst: String,
        val klageDato: LocalDate,
        val vedtaksbrevDato: LocalDate,
    ) : KlageDokumentCommand()

    /**
     * Når vi avviser en klage sendes det et enkeltvedtak til bruker.
     */
    data class Avvist(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val attestant: NavIdentBruker.Attestant?,
        val fritekst: String,
    ) : KlageDokumentCommand()
}
