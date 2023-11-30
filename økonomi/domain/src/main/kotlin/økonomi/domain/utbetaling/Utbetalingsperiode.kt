package økonomi.domain.utbetaling

import vilkår.uføre.domain.Uføregrad
import java.time.LocalDate

data class Utbetalingsperiode(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Int,
    val uføregrad: Uføregrad,
)
