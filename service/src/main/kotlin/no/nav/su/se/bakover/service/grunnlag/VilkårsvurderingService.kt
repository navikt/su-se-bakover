package no.nav.su.se.bakover.service.grunnlag

import no.nav.su.se.bakover.database.grunnlag.VilkårsvurderingRepo
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurdering
import no.nav.su.se.bakover.domain.vilkår.Vurdering
import java.util.UUID

interface VilkårsvurderingService {
    fun automatisk(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>)
    fun manuell(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>, vurdering: Vurdering.Manuell)
}

internal class VilkårsvurderingServiceImpl(
    private val vilkårsvurderingRepo: VilkårsvurderingRepo,
) : VilkårsvurderingService {
    override fun automatisk(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>) {
        if (uføregrunnlag.isEmpty()) {
            vilkårsvurderingRepo.lagre(
                behandlingId,
                listOf(
                    Vilkårsvurdering.Uførhet(
                        vurdering = Vurdering.Automatisk(Resultat.Avslag),
                        grunnlag = null,
                    ),
                ),
            )
        } else {
            vilkårsvurderingRepo.lagre(
                behandlingId,
                uføregrunnlag.map {
                    Vilkårsvurdering.Uførhet(
                        vurdering = it.vurder(),
                        grunnlag = it,
                    )
                },
            )
        }
    }

    override fun manuell(behandlingId: UUID, uføregrunnlag: List<Grunnlag.Uføregrunnlag>, vurdering: Vurdering.Manuell) {
        if (uføregrunnlag.isEmpty()) {
            vilkårsvurderingRepo.lagre(
                behandlingId,
                listOf(
                    Vilkårsvurdering.Uførhet(
                        vurdering = vurdering,
                        grunnlag = null,
                    ),
                ),
            )
        } else {
            vilkårsvurderingRepo.lagre(
                behandlingId,
                uføregrunnlag.map {
                    Vilkårsvurdering.Uførhet(
                        vurdering = vurdering,
                        grunnlag = it,
                    )
                },
            )
        }
    }

    private fun Grunnlag.Uføregrunnlag.vurder(): Vurdering.Automatisk {
        return Vurdering.Automatisk(
            resultat = if (uføregrad.value > 0) Resultat.Innvilget else Resultat.Avslag,
        )
    }
}
