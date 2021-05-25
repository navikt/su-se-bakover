package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.extensions.either.applicative.applicative
import arrow.core.extensions.list.traverse.traverse
import arrow.core.fix
import arrow.core.identity
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Copyable
import no.nav.su.se.bakover.domain.Ektefelle
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
                }.traverse(Either.applicative(), ::identity).fix().map {
                    it.fix()
                }
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

    /** Json body used in Route */
    data class BoforholdOgSivilstatusBody(
        val ektefelle: Ektefelle?,
        val delerBoligMedBarnOver18EllerAndreVoksne: Bosituasjon,
    ) {
        data class Ektefelle(
            val fnr: String,
            val erUførFlykning: Boolean,
        )

        enum class Bosituasjon {
            ENSLIG,
            DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN,

            /** Service-laget/modellen finner ut dette baser på Ektefelle->fnr */
            DELER_BOLIG_MED_EKTEMAKE_SAMBOER
        }
    }

    /**
     * Domain model (create a flat model in addition to this in database-layer)
     */
    sealed class BoforholdOgSivilstatus : Grunnlag(), Copyable<CopyArgs.Snitt, BoforholdOgSivilstatus?> {
        abstract override val id: UUID
        abstract val opprettet: Tidspunkt
        abstract val periode: Periode

        sealed class EktefellePartnerSamboer : BoforholdOgSivilstatus() {

            abstract val fnr: String

            sealed class Under67(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
                override val fnr: String,
            ) : EktefellePartnerSamboer() {

                data class UførFlyktning(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val periode: Periode,
                    override val fnr: String,
                ) : EktefellePartnerSamboer() {
                    override fun copy(args: CopyArgs.Snitt): UførFlyktning? {
                        return args.snittFor(periode)?.let { copy(periode = it) }
                    }
                }

                data class IkkeUførFlyktning(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val periode: Periode,
                    override val fnr: String,
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
                override val fnr: String,
            ) : EktefellePartnerSamboer() {
                override fun copy(args: CopyArgs.Snitt): SektiSyvEllerEldre? {
                    return args.snittFor(periode)?.let { copy(periode = it) }
                }
            }
        }

        /** Denne er kun for å støtte at man har valgt ikkeEktefelle, før man har valgt enslig/bor med andre voksne */
        data class IkkeValgtEktefelle(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val periode: Periode,
        ) : BoforholdOgSivilstatus() {
            override fun copy(args: CopyArgs.Snitt): IkkeValgtEktefelle? {
                return args.snittFor(periode)?.let { copy(periode = it) }
            }
        }

        /** Bor ikke med noen over 18 år */
        data class Enslig(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val periode: Periode,
        ) : BoforholdOgSivilstatus() {
            override fun copy(args: CopyArgs.Snitt): Enslig? {
                return args.snittFor(periode)?.let { copy(periode = it) }
            }
        }

        data class DelerBoligMedVoksneBarnEllerAnnenVoksen(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val periode: Periode,
        ) : BoforholdOgSivilstatus() {
            override fun copy(args: CopyArgs.Snitt): DelerBoligMedVoksneBarnEllerAnnenVoksen? {
                return args.snittFor(periode)?.let { copy(periode = it) }
            }
        }
    }
}
