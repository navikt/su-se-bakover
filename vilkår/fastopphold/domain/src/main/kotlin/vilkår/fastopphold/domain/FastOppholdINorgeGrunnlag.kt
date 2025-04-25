package vilkår.fastopphold.domain

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

data class FastOppholdINorgeGrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
) : Grunnlag,
    KanPlasseresPåTidslinje<FastOppholdINorgeGrunnlag> {

    fun oppdaterPeriode(periode: Periode): FastOppholdINorgeGrunnlag = FastOppholdINorgeGrunnlag(
        id = id,
        opprettet = opprettet,
        periode = periode,
    )

    override fun copy(args: CopyArgs.Tidslinje): FastOppholdINorgeGrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is FastOppholdINorgeGrunnlag
    }

    override fun copyWithNewId(): FastOppholdINorgeGrunnlag = this.copy(id = UUID.randomUUID())
}
