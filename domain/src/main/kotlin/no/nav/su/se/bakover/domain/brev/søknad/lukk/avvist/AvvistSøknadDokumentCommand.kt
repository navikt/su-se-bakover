@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som GenererDokumentCommand (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr

data class AvvistSøknadDokumentCommand(
    override val sakstype: Sakstype,
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    val fritekst: String,
    val saksbehandler: NavIdentBruker.Saksbehandler,
) : GenererDokumentCommand
