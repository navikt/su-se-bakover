package no.nav.su.se.bakover.service.vilkår

import java.util.UUID

enum class BosituasjonValg {
    DELER_BOLIG_MED_VOKSNE,
    BOR_ALENE,
    EPS_UFØR_FLYKTNING,
    EPS_IKKE_UFØR_FLYKTNING,
}

data class FullførBosituasjonRequest(
    val behandlingId: UUID,
    val bosituasjon: BosituasjonValg,
    val begrunnelse: String?,
) {}
