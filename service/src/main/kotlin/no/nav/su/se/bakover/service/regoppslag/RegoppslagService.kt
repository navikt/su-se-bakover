package no.nav.su.se.bakover.service.regoppslag

import arrow.core.Either
import arrow.core.getOrElse
import no.nav.su.se.bakover.client.regoppslag.RegoppslagFeil
import no.nav.su.se.bakover.client.regoppslag.RegoppslagKlient
import no.nav.su.se.bakover.client.regoppslag.RegoppslagResponseDTO
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.sak.SakService
import java.util.UUID

interface RegoppslagServiceInterface {
    suspend fun hentMottakerAdresse(
        sakId: UUID,
        ident: Fnr,
    ): Either<RegoppslagFeil, RegoppslagResponseDTO>
}

class RegoppslagService(
    private val regoppslagKlient: RegoppslagKlient,
    private val sakService: SakService,
) : RegoppslagServiceInterface {
    override suspend fun hentMottakerAdresse(
        sakId: UUID,
        ident: Fnr,
    ): Either<RegoppslagFeil, RegoppslagResponseDTO> {
        val sak = sakService.hentSakInfo(sakId = sakId).getOrElse {
            return Either.Left(RegoppslagFeil.IkkeFunnet)
        }
        return regoppslagKlient.hentMottakerAdresse(sak.type, ident)
    }
}
