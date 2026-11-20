@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som GenererDokumentCommand (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
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
    val saksbehandler: NavIdentBruker.Saksbehandler,
) : GenererDokumentCommand
