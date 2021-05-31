package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.sequenceEither
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Copyable
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

sealed class Grunnlag {
    abstract val id: UUID

    /**
     * @throws IllegalArgumentException hvis forventetInntekt er negativ
     */
    data class Uføregrunnlag(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val periode: Periode,
        val uføregrad: Uføregrad,
        /** Kan ikke være negativ. */
        val forventetInntekt: Int,
    ) : Grunnlag(), KanPlasseresPåTidslinje<Uføregrunnlag> {
        init {
            if (forventetInntekt < 0) throw IllegalArgumentException("forventetInntekt kan ikke være mindre enn 0")
        }

        fun oppdaterPeriode(periode: Periode): Uføregrunnlag {
            return this.copy(periode = periode)
        }

        override fun copy(args: CopyArgs.Tidslinje): Uføregrunnlag = when (args) {
            CopyArgs.Tidslinje.Full -> {
                this.copy(id = UUID.randomUUID())
            }
            is CopyArgs.Tidslinje.NyPeriode -> {
                this.copy(id = UUID.randomUUID(), periode = args.periode)
            }
        }
    }

    data class Flyktninggrunnlag(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val periode: Periode,
    ) : Grunnlag(), KanPlasseresPåTidslinje<Flyktninggrunnlag> {

        fun oppdaterPeriode(periode: Periode): Flyktninggrunnlag {
            return this.copy(periode = periode)
        }

        override fun copy(args: CopyArgs.Tidslinje): Flyktninggrunnlag = when (args) {
            CopyArgs.Tidslinje.Full -> {
                this.copy(id = UUID.randomUUID())
            }
            is CopyArgs.Tidslinje.NyPeriode -> {
                this.copy(id = UUID.randomUUID(), periode = args.periode)
            }
        }
    }

    data class Fradragsgrunnlag(
        override val id: UUID = UUID.randomUUID(),
        val opprettet: Tidspunkt = Tidspunkt.now(),
        val fradrag: Fradrag,
    ) : Grunnlag() {

        companion object Validator {
            fun List<Fradragsgrunnlag>.valider(behandlingsperiode: Periode): Either<UgyldigFradragsgrunnlag, List<Fradragsgrunnlag>> {
                return map {
                    it.valider(behandlingsperiode)
                }.sequenceEither()
            }

            fun Fradragsgrunnlag.valider(behandlingsperiode: Periode): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag> {
                if (!(behandlingsperiode inneholder fradrag.periode))
                    return UgyldigFradragsgrunnlag.UtenforBehandlingsperiode.left()
                if (setOf(Fradragstype.ForventetInntekt, Fradragstype.BeregnetFradragEPS, Fradragstype.UnderMinstenivå).contains(fradrag.fradragstype))
                    return UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()
                return this.right()
            }

            sealed class UgyldigFradragsgrunnlag {
                object UtenforBehandlingsperiode : UgyldigFradragsgrunnlag()
                object UgyldigFradragstypeForGrunnlag : UgyldigFradragsgrunnlag()
            }
        }
    }

    /**
     * Domain model (create a flat model in addition to this in database-layer)
     */
    sealed class Bosituasjon : Grunnlag() {
        abstract override val id: UUID
        abstract val opprettet: Tidspunkt
        abstract val periode: Periode

        sealed class Fullstendig : Bosituasjon(), Copyable<CopyArgs.Snitt, Fullstendig?> {
            abstract val begrunnelse: String?

            sealed class EktefellePartnerSamboer : Fullstendig() {
                abstract val fnr: Fnr

                sealed class Under67 : EktefellePartnerSamboer() {
                    data class UførFlyktning(
                        override val id: UUID,
                        override val opprettet: Tidspunkt,
                        override val periode: Periode,
                        override val fnr: Fnr,
                        override val begrunnelse: String?,
                    ) : EktefellePartnerSamboer() {
                        override fun copy(args: CopyArgs.Snitt): UførFlyktning? {
                            return args.snittFor(periode)?.let { copy(periode = it) }
                        }
                    }

                    data class IkkeUførFlyktning(
                        override val id: UUID,
                        override val opprettet: Tidspunkt,
                        override val periode: Periode,
                        override val fnr: Fnr,
                        override val begrunnelse: String?,
                    ) : EktefellePartnerSamboer() {
                        override fun copy(args: CopyArgs.Snitt): IkkeUførFlyktning? {
                            return args.snittFor(periode)?.let { copy(periode = it) }
                        }
                    }
                }

                data class SektiSyvEllerEldre(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val periode: Periode,
                    override val fnr: Fnr,
                    override val begrunnelse: String?,
                ) : EktefellePartnerSamboer() {
                    override fun copy(args: CopyArgs.Snitt): SektiSyvEllerEldre? {
                        return args.snittFor(periode)?.let { copy(periode = it) }
                    }
                }
            }

            /** Bor ikke med noen over 18 år */
            data class Enslig(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
                override val begrunnelse: String?,
            ) : Fullstendig() {
                override fun copy(args: CopyArgs.Snitt): Enslig? {
                    return args.snittFor(periode)?.let { copy(periode = it) }
                }
            }

            data class DelerBoligMedVoksneBarnEllerAnnenVoksen(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
                override val begrunnelse: String?,
            ) : Fullstendig() {
                override fun copy(args: CopyArgs.Snitt): DelerBoligMedVoksneBarnEllerAnnenVoksen? {
                    return args.snittFor(periode)?.let { copy(periode = it) }
                }
            }
        }

        sealed class Ufullstendig : Bosituasjon() {
            /** Dette er en midlertid tilstand hvor det er valgt Ikke Eps, men ikke tatt stilling til bosituasjon Enslig eller med voksne
             Data klassen kan godt få et bedre navn... */
            data class HarValgtEPSIkkeValgtEnsligVoksne(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Ufullstendig()

            /** Dette er en midlertid tilstand hvor det er valgt Eps, men ikke tatt stilling til om eps er ufør flyktning eller ikke
             Data klassen kan godt få et bedre navn... */
            data class HarEpsIkkeValgtUførFlyktning(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
                val fnr: Fnr,
            ) : Ufullstendig()
        }
    }
}
