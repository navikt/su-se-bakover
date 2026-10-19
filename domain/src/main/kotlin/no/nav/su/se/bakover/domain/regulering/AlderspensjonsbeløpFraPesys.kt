package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Måned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.math.BigDecimal

data class AlderspensjonOppslagsgrunnlag(
    val brukerFnr: Fnr,
    val personer: List<AlderspensjonOppslagsperson>,
)

data class AlderspensjonOppslagsperson(
    val fnr: Fnr,
    val tilhører: FradragTilhører,
)

data class AlderspensjonsbeløpFraPesys(
    val tilhører: FradragTilhører,
    val måned: Måned,
    val beløp: BigDecimal,
)

data class AlderspensjonFraPesys(
    val brukerFnr: Fnr,
    val epsFnr: Fnr?,
    val beløp: List<AlderspensjonsbeløpFraPesys>,
)
