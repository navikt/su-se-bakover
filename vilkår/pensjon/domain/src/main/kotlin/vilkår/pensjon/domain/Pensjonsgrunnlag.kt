package vilkår.pensjon.domain

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

data class Pensjonsgrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
    val pensjonsopplysninger: Pensjonsopplysninger,
) : Grunnlag, KanPlasseresPåTidslinje<Pensjonsgrunnlag> {

    fun oppdaterPeriode(periode: Periode): Pensjonsgrunnlag {
        return copy(periode = periode)
    }

    override fun copy(args: CopyArgs.Tidslinje): Pensjonsgrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is Pensjonsgrunnlag &&
            other.pensjonsopplysninger == pensjonsopplysninger
    }

    override fun copyWithNewId(): Pensjonsgrunnlag = this.copy(id = UUID.randomUUID())
}
