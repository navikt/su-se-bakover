package no.nav.su.se.bakover.web.routes.behandling.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.beregning.NyBeregningForSøknadsbehandling
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson.Companion.toFradrag
import java.util.UUID

internal data class NyBeregningForSøknadsbehandlingJson(
    val stønadsperiode: StønadsperiodeJson?,
    // deprecated, change to stønadsperiode in su-se-framover
    val fraOgMed: String?,
    // deprecated, change to stønadsperiode in su-se-framover
    val tilOgMed: String?,
    val fradrag: List<FradragJson>
) {
    fun toDomain(behandlingId: UUID, saksbehandler: Saksbehandler): Either<Resultat, NyBeregningForSøknadsbehandling> {
        val stønadsperiode =
            (stønadsperiode ?: StønadsperiodeJson(PeriodeJson(fraOgMed!!, tilOgMed!!))).toStønadsperiode().getOrHandle {
                return it.left()
            }
        val fradrag = fradrag.toFradrag(stønadsperiode.periode).getOrHandle {
            return it.left()
        }
        return NyBeregningForSøknadsbehandling.tryCreate(
            behandlingId = behandlingId,
            saksbehandler = saksbehandler,
            stønadsperiode = stønadsperiode,
            fradrag = fradrag,
        ).mapLeft {
            when (it) {
                NyBeregningForSøknadsbehandling.UgyldigBeregning.IkkeLovMedFradragUtenforPerioden -> HttpStatusCode.BadRequest.message(
                    "Fradragsperioden kan ikke være utenfor stønadsperioden"
                )
            }
        }
    }
}
