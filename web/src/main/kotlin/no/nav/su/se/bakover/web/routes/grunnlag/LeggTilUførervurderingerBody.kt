package no.nav.su.se.bakover.web.routes.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.UførevilkårStatus
import no.nav.su.se.bakover.web.routes.periode.toPeriodeOrResultat
import java.util.UUID

internal data class LeggTilUførervurderingerBody(val vurderinger: List<Uførevurdering>) {
    fun toServiceCommand(behandlingId: UUID): Either<Resultat, LeggTilUførevurderingerRequest> {
        if (vurderinger.isEmpty()) {
            return HttpStatusCode.BadRequest.errorJson(
                "Ingen perioder er vurdert",
                "vurderingsperioder_mangler",
            ).left()
        }

        return LeggTilUførevurderingerRequest(
            behandlingId = behandlingId,
            vurderinger = (
                vurderinger.toNonEmptyList()
                ).map { vurdering ->
                vurdering.toServiceCommand(behandlingId).getOrElse {
                    return it.left()
                }
            },
        ).right()
    }

    data class Uførevurdering(
        val periode: PeriodeJson,
        val uføregrad: Int?,
        val forventetInntekt: Int?,
        // TODO jah: Bruk en egen type for dette
        val resultat: UførevilkårStatus,
        val begrunnelse: String?,
    ) {

        fun toServiceCommand(revurderingId: UUID): Either<Resultat, LeggTilUførevilkårRequest> {
            val periode = periode.toPeriodeOrResultat().getOrElse {
                return it.left()
            }
            val validUføregrad = uføregrad?.let {
                Uføregrad.tryParse(it).getOrElse {
                    return Feilresponser.Uføre.uføregradMåVæreMellomEnOgHundre.left()
                }
            }
            return LeggTilUførevilkårRequest(
                behandlingId = revurderingId,
                periode = periode,
                uføregrad = validUføregrad,
                forventetInntekt = forventetInntekt,
                oppfylt = resultat,
                begrunnelse = begrunnelse,
            ).right()
        }
    }
}
