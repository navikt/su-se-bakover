package no.nav.su.se.bakover.domain.brev.command

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.Saksnummer

/**
 * Viktig informasjonsbrev
 */
data class InnkallingTilKontrollsamtaleDokumentCommand(
    override val fødselsnummer: Fnr,
    override val saksnummer: Saksnummer,
) : GenererDokumentCommand
