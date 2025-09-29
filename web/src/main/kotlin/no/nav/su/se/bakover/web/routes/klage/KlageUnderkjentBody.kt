package no.nav.su.se.bakover.web.routes.klage

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import behandling.klage.domain.KlageId
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.service.klage.UnderkjennKlageRequest
import java.util.UUID

private enum class Grunn {
    INNGANGSVILKÅRENE_ER_FEILVURDERT,
    BEREGNINGEN_ER_FEIL,
    DOKUMENTASJON_MANGLER,
    VEDTAKSBREVET_ER_FEIL,
    ANDRE_FORHOLD,
}

data class KlageUnderkjentBody(val grunn: String, val kommentar: String) {
    fun toRequest(
        klageId: UUID,
        attestant: NavIdentBruker.Attestant,
    ): Either<Resultat, UnderkjennKlageRequest> {
        return UnderkjennKlageRequest(
            klageId = KlageId(klageId),
            attestant = attestant,
            grunn = Either.catch { Grunn.valueOf(grunn) }.map {
                when (it) {
                    Grunn.INNGANGSVILKÅRENE_ER_FEILVURDERT -> UnderkjennAttesteringsgrunnBehandling.INNGANGSVILKÅRENE_ER_FEILVURDERT
                    Grunn.BEREGNINGEN_ER_FEIL -> UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL
                    Grunn.DOKUMENTASJON_MANGLER -> UnderkjennAttesteringsgrunnBehandling.DOKUMENTASJON_MANGLER
                    Grunn.VEDTAKSBREVET_ER_FEIL -> UnderkjennAttesteringsgrunnBehandling.VEDTAKSBREVET_ER_FEIL
                    Grunn.ANDRE_FORHOLD -> UnderkjennAttesteringsgrunnBehandling.ANDRE_FORHOLD
                }
            }.getOrElse {
                return BadRequest.errorJson(
                    "Ugyldig underkjennelsesgrunn",
                    "ugyldig_grunn_for_underkjenning",
                ).left()
            },
            kommentar = kommentar,
        ).right()
    }
}
