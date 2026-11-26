@file:Suppress("PackageDirectoryMismatch")
// Ligger i samme pakke som de andre brevkommandoene, men i mappen for lukking av søknad.

package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.generer.Brevmal
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.Felles
import dokument.domain.generer.PdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.søknad.lukk.trukket.trukketSøknadBrev
import java.time.LocalDate

/**
 * @param søknadMottatt Datoen Nav mottok søknaden, se [no.nav.su.se.bakover.domain.søknad.Søknad.mottaksdato].
 * For papirsøknad er dette datoen saksbehandler registrerte som mottatt, ikke når søknaden ble registrert i systemet.
 */
data class TrukketSøknadDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
    val søknadMottatt: LocalDate,
    val trukketDato: LocalDate,
    override val saksbehandler: NavIdentBruker.Saksbehandler,
) : BrevmalCommand {
    override val attestant: NavIdentBruker? get() = null
    override val mal: Brevmal<TrukketSøknadDokumentCommand> get() = trukketSøknadBrev
    override fun lagInnhold(felles: Felles): PdfInnhold = mal.lagInnhold(felles, this)
}
