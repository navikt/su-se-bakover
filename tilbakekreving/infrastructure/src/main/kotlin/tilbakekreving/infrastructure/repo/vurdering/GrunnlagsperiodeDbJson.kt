package tilbakekreving.infrastructure.repo.vurdering

import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import java.math.BigDecimal
import java.time.LocalDate

internal data class GrunnlagsperiodeDbJson(
    val fraOgMed: String,
    val tilOgMed: String,
    val betaltSkattForYtelsesgruppen: Int,
    val bruttoTidligereUtbetalt: Int,
    val bruttoNyUtbetaling: Int,
    val bruttoFeilutbetaling: Int,
    val skatteProsent: String,
) {

    fun toDomain(): Kravgrunnlag.Grunnlagsperiode = Kravgrunnlag.Grunnlagsperiode(
        periode = DatoIntervall(LocalDate.parse(this.fraOgMed), LocalDate.parse(this.tilOgMed)),
        betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen,
        bruttoTidligereUtbetalt = bruttoTidligereUtbetalt,
        bruttoNyUtbetaling = bruttoNyUtbetaling,
        bruttoFeilutbetaling = bruttoFeilutbetaling,
        skatteProsent = BigDecimal(this.skatteProsent),
    )

    companion object {
        fun Kravgrunnlag.Grunnlagsperiode.toDbJson(): GrunnlagsperiodeDbJson {
            return GrunnlagsperiodeDbJson(
                betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen,
                fraOgMed = this.periode.fraOgMed.toString(),
                tilOgMed = this.periode.tilOgMed.toString(),
                bruttoTidligereUtbetalt = bruttoTidligereUtbetalt,
                bruttoNyUtbetaling = bruttoNyUtbetaling,
                bruttoFeilutbetaling = bruttoFeilutbetaling,
                skatteProsent = skatteProsent.toString(),
            )
        }
    }
}
