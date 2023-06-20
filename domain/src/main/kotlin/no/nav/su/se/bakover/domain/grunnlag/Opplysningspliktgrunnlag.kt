package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

sealed interface OpplysningspliktBeskrivelse {
    object TilstrekkeligDokumentasjon : OpplysningspliktBeskrivelse
    object UtilstrekkeligDokumentasjon : OpplysningspliktBeskrivelse
}

data class Opplysningspliktgrunnlag(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val periode: Periode,
    val beskrivelse: OpplysningspliktBeskrivelse,
) : Grunnlag, KanPlasseresPåTidslinje<Opplysningspliktgrunnlag> {

    fun oppdaterPeriode(periode: Periode): Opplysningspliktgrunnlag {
        return tryCreate(
            id = id,
            opprettet = opprettet,
            periode = periode,
            beskrivelse = beskrivelse,
        ).getOrElse { throw IllegalArgumentException(it.toString()) }
    }

    override fun copy(args: CopyArgs.Tidslinje): Opplysningspliktgrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }
        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(id = UUID.randomUUID(), periode = args.periode)
        }
    }

    override fun erLik(other: Grunnlag): Boolean {
        return other is Opplysningspliktgrunnlag
    }

    companion object {
        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            beskrivelse: OpplysningspliktBeskrivelse,
        ): Either<UgyldigOpplysningspliktgrunnlag, Opplysningspliktgrunnlag> {
            return Opplysningspliktgrunnlag(
                id = id,
                opprettet = opprettet,
                periode = periode,
                beskrivelse = beskrivelse,
            ).right()
        }
    }
}

sealed interface UgyldigOpplysningspliktgrunnlag
