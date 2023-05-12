package no.nav.su.se.bakover.domain.dokument

import no.nav.su.se.bakover.domain.skatt.Skattegrunnlag
import java.util.UUID

data class EksterneGrunnlag(
    val skattegrunnlagSøker: SamletExternSkatteGrunnlag,
    val skattegrunnlagEps: SamletExternSkatteGrunnlag?
)


data class SamletExternSkatteGrunnlag(
    val id: UUID,
    val skattegrunnlag: Skattegrunnlag
)
