package no.nav.su.se.bakover.web.services.klage.klageinstans

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.klage.KunneIkkeTolkeKlageinstanshendelse
import java.util.UUID

data class BehandlingFeilregistrertDto(
    override val kildeReferanse: String,
) : KlageinstanshendelseDto {
    override fun toDomain(
        id: UUID,
        opprettet: Tidspunkt,
    ): Either<KunneIkkeTolkeKlageinstanshendelse.BehandlingFeilregistrertStøttesIkke, Nothing> {
        return KunneIkkeTolkeKlageinstanshendelse.BehandlingFeilregistrertStøttesIkke.left()
    }

    data class DetaljerWrapper(
        val behandlingFeilregistrert: Detaljer,
    )

    /**
     * @param feilregistrert Når behandlingen ble merket som feilregistrert.
     * @param navIdent Identen til den som merket behandlingen som feilregistrert.
     * @param reason Årsaken til at behandlingen endte opp som feilregistrert.
     * @param type KLAGE, ANKE, ANKE_I_TRYGDERETTEN, BEHANDLING_ETTER_TRYGDERETTEN_OPPHEVET
     */
    data class Detaljer(
        val feilregistrert: String,
        val navIdent: String,
        val reason: String,
        val type: String,
    )
}
