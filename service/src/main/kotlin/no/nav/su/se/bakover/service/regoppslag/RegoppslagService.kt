package no.nav.su.se.bakover.service.regoppslag

import arrow.core.Either
import no.nav.su.se.bakover.client.regoppslag.RegoppslagFeil
import no.nav.su.se.bakover.client.regoppslag.RegoppslagKlient
import no.nav.su.se.bakover.client.regoppslag.RegoppslagResponseDTO
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr

class RegoppslagService(
    private val regoppslagKlient: RegoppslagKlient,
) {
    suspend fun hentMottakerAdresse(
        sakType: Sakstype,
        ident: Fnr,
    ): Either<RegoppslagFeil, RegoppslagResponseDTO> {
        return regoppslagKlient.hentMottakerAdresse(sakType, ident)
    }
}
