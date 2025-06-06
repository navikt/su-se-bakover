package no.nav.su.se.bakover.domain.brev.command

import dokument.domain.GenererDokumentCommand
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr

/**
 * Viktig informasjonsbrev
 */
data class InnkallingTilKontrollsamtaleDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
    override val sakstype: Sakstype,
) : GenererDokumentCommand
