package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.sequenceEither
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Copyable
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import org.jetbrains.annotations.TestOnly
import java.util.UUID

sealed class Grunnlag {
    abstract val id: UUID

    abstract val periode: Periode

    fun tilstøter(other: Grunnlag) = this.periode.tilstøter(other.periode)

    /**
     * unnlater å sjekke på ID og opprettet
     */
    abstract fun erLik(other: Grunnlag): Boolean

    fun tilstøterOgErLik(other: Grunnlag) = this.tilstøter(other) && this.erLik(other)

    /**
     * @throws IllegalArgumentException hvis forventetInntekt er negativ
     */
    data class Uføregrunnlag(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt,
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

        /**
         * Sjekker at periodene tilstøter, og om uføregrad og forventet inntekt er lik
         */
        override fun erLik(other: Grunnlag): Boolean {
            if (other !is Uføregrunnlag) {
                return false
            }

            return this.uføregrad == other.uføregrad && this.forventetInntekt == other.forventetInntekt
        }

        companion object {
            fun List<Uføregrunnlag>.slåSammenPeriodeOgUføregrad(): List<Pair<Periode, Uføregrad>> {
                return this.sortedBy { it.periode.fraOgMed }
                    .fold(mutableListOf<MutableList<Uføregrunnlag>>()) { acc, uføregrunnlag ->
                        if (acc.isEmpty()) {
                            acc.add(mutableListOf(uføregrunnlag))
                        } else if (acc.last().sisteUføregrunnlagErLikOgTilstøtende(uføregrunnlag)) {
                            acc.last().add(uføregrunnlag)
                        } else {
                            acc.add(mutableListOf(uføregrunnlag))
                        }
                        acc
                    }.map {
                        val periode = it.map { it.periode }.minAndMaxOf()

                        periode to it.first().uføregrad
                    }
            }

            private fun List<Uføregrunnlag>.sisteUføregrunnlagErLikOgTilstøtende(other: Uføregrunnlag) =
                this.last().let { it.tilstøter(other) && it.erLik(other) }
        }
    }

    data class Fradragsgrunnlag private constructor(
        override val id: UUID = UUID.randomUUID(),
        val opprettet: Tidspunkt,
        val fradrag: Fradrag,
    ) : Grunnlag(), Fradrag by fradrag {
        override val periode: Periode = fradrag.periode

        fun oppdaterFradragsperiode(
            oppdatertPeriode: Periode,
        ): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag> {
            return this.copyInternal(CopyArgs.Snitt(oppdatertPeriode)).flatMap {
                it?.right() ?: tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                    fradrag = FradragFactory.ny(
                        type = this.fradrag.fradragstype,
                        månedsbeløp = this.fradrag.månedsbeløp,
                        periode = oppdatertPeriode,
                        utenlandskInntekt = this.fradrag.utenlandskInntekt,
                        tilhører = this.fradrag.tilhører,
                    ),
                )
            }
        }

        override fun erLik(other: Grunnlag): Boolean {
            return other is Fradragsgrunnlag &&
                this.periode tilstøter other.periode &&
                this.fradrag.fradragstype == other.fradragstype &&
                this.fradrag.månedsbeløp == other.månedsbeløp &&
                this.fradrag.utenlandskInntekt == other.utenlandskInntekt &&
                this.fradrag.tilhører == other.tilhører
        }

        override fun copy(args: CopyArgs.Snitt): Fradragsgrunnlag? {
            return copyInternal(args).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        private fun copyInternal(args: CopyArgs.Snitt): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag?> {
            return fradrag.copy(args)?.let {
                tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = this.opprettet,
                    fradrag = it,
                ).getOrHandle { return it.left() }
            }.right()
        }

        companion object {
            @TestOnly
            fun create(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt,
                fradrag: Fradrag,
            ) = tryCreate(id, opprettet, fradrag).getOrHandle { throw IllegalArgumentException(it.toString()) }

            fun tryCreate(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt,
                fradrag: Fradrag,
            ): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag> {
                if (harUgyldigFradragsType(fradrag)) {
                    return UgyldigFradragsgrunnlag.UgyldigFradragstypeForGrunnlag.left()
                }

                return Fradragsgrunnlag(id = id, opprettet = opprettet, fradrag = fradrag).right()
            }

            fun List<Fradragsgrunnlag>.oppdaterFradragsperiode(
                oppdatertPeriode: Periode,
            ): Either<UgyldigFradragsgrunnlag, List<Fradragsgrunnlag>> {
                return this.map { it.oppdaterFradragsperiode(oppdatertPeriode) }.sequenceEither()
            }

            fun List<Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
            fun List<Fradragsgrunnlag>.periode(): Periode? = this.map { it.fradrag.periode }.let { perioder ->
                if (perioder.isEmpty()) null else
                    Periode.create(
                        fraOgMed = perioder.minOf { it.fraOgMed },
                        tilOgMed = perioder.maxOf { it.tilOgMed },
                    )
            }

            fun List<Fradragsgrunnlag>.slåSammenPeriodeOgFradrag(): List<Fradragsgrunnlag> {
                return this.sortedBy { it.periode.fraOgMed }
                    .fold(mutableListOf<MutableList<Fradragsgrunnlag>>()) { acc, fradragsgrunnlag ->
                        if (acc.isEmpty()) {
                            acc.add(mutableListOf(fradragsgrunnlag))
                        } else if (acc.last().sisteFradragsgrunnlagErLikOgTilstøtende(fradragsgrunnlag)) {
                            acc.last().add(fradragsgrunnlag)
                        } else {
                            acc.add(mutableListOf(fradragsgrunnlag))
                        }
                        acc
                    }.map {
                        val periode = it.map { it.periode }.minAndMaxOf()

                        tryCreate(
                            id = UUID.randomUUID(),
                            opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
                            fradrag = FradragFactory.ny(
                                type = it.first().fradragstype,
                                månedsbeløp = it.first().månedsbeløp,
                                periode = periode,
                                utenlandskInntekt = it.first().utenlandskInntekt,
                                tilhører = it.first().tilhører,
                            ),
                        ).getOrHandle { throw IllegalStateException(it.toString()) }
                    }
            }

            /**
             * inntil fradragsgrunnlag har sine egne fradragstyper så må vi sjekke at disse ikke er med
             */
            private fun harUgyldigFradragsType(fradrag: Fradrag): Boolean =
                setOf(
                    Fradragstype.ForventetInntekt,
                    Fradragstype.BeregnetFradragEPS,
                    Fradragstype.UnderMinstenivå,
                ).contains(fradrag.fradragstype)

            private fun List<Fradragsgrunnlag>.sisteFradragsgrunnlagErLikOgTilstøtende(other: Fradragsgrunnlag) =
                this.last().let { it.tilstøter(other) && it.erLik(other) }
        }

        sealed class UgyldigFradragsgrunnlag {
            object UgyldigFradragstypeForGrunnlag : UgyldigFradragsgrunnlag()
        }
    }

    /**
     * Domain model (create a flat model in addition to this in database-layer)
     */
    sealed class Bosituasjon : Grunnlag() {
        abstract override val id: UUID
        abstract val opprettet: Tidspunkt

        fun oppdaterBosituasjonsperiode(oppdatertPeriode: Periode): Bosituasjon {
            return when (this) {
                is Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.Enslig -> this.copy(periode = oppdatertPeriode)
                is Ufullstendig.HarEps -> this.copy(periode = oppdatertPeriode)
                is Ufullstendig.HarIkkeEps -> this.copy(periode = oppdatertPeriode)
            }
        }

        companion object {
            fun List<Bosituasjon>.oppdaterBosituasjonsperiode(oppdatertPeriode: Periode): List<Bosituasjon> {
                return this.map { it.oppdaterBosituasjonsperiode(oppdatertPeriode) }
            }

            fun List<Bosituasjon>.slåSammenPeriodeOgBosituasjon(): List<Bosituasjon> {
                return this.sortedBy { it.periode.fraOgMed }
                    .fold(mutableListOf<MutableList<Bosituasjon>>()) { acc, bosituasjon ->
                        if (acc.isEmpty()) {
                            acc.add(mutableListOf(bosituasjon))
                        } else if (acc.last().sisteBosituasjonsgrunnlagErLikOgTilstøtende(bosituasjon)) {
                            acc.last().add(bosituasjon)
                        } else {
                            acc.add(mutableListOf(bosituasjon))
                        }
                        acc
                    }.map {
                        val periode = it.map { it.periode }.minAndMaxOf()

                        when (val bosituasjon = it.first()) {
                            is Fullstendig -> bosituasjon.oppdaterBosituasjonsperiode(periode)
                            is Ufullstendig -> throw java.lang.IllegalStateException("Kan ikke ha ufullstendige bosituasjoner")
                        }
                    }
            }

            private fun List<Bosituasjon>.sisteBosituasjonsgrunnlagErLikOgTilstøtende(other: Bosituasjon) =
                this.last().let { it.tilstøter(other) && it.erLik(other) }
        }

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
                        override fun erLik(other: Grunnlag): Boolean {
                            if (other !is UførFlyktning) {
                                return false
                            }
                            return this.fnr == other.fnr
                        }

                        override fun copy(args: CopyArgs.Snitt): UførFlyktning? {
                            return args.snittFor(periode)?.let { copy(id = UUID.randomUUID(), periode = it) }
                        }
                    }

                    data class IkkeUførFlyktning(
                        override val id: UUID,
                        override val opprettet: Tidspunkt,
                        override val periode: Periode,
                        override val fnr: Fnr,
                        override val begrunnelse: String?,
                    ) : EktefellePartnerSamboer() {
                        override fun erLik(other: Grunnlag): Boolean {
                            if (other !is IkkeUførFlyktning) {
                                return false
                            }
                            return this.fnr == other.fnr
                        }

                        override fun copy(args: CopyArgs.Snitt): IkkeUførFlyktning? {
                            return args.snittFor(periode)?.let { copy(id = UUID.randomUUID(), periode = it) }
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
                    override fun erLik(other: Grunnlag): Boolean {
                        if (other !is SektiSyvEllerEldre) {
                            return false
                        }
                        return this.fnr == other.fnr
                    }

                    override fun copy(args: CopyArgs.Snitt): SektiSyvEllerEldre? {
                        return args.snittFor(periode)?.let { copy(id = UUID.randomUUID(), periode = it) }
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
                override fun erLik(other: Grunnlag): Boolean {
                    return other is Enslig
                }

                override fun copy(args: CopyArgs.Snitt): Enslig? {
                    return args.snittFor(periode)?.let { copy(id = UUID.randomUUID(), periode = it) }
                }
            }

            data class DelerBoligMedVoksneBarnEllerAnnenVoksen(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
                override val begrunnelse: String?,
            ) : Fullstendig() {
                override fun erLik(other: Grunnlag): Boolean {
                    return other is DelerBoligMedVoksneBarnEllerAnnenVoksen
                }

                override fun copy(args: CopyArgs.Snitt): DelerBoligMedVoksneBarnEllerAnnenVoksen? {
                    return args.snittFor(periode)?.let { copy(id = UUID.randomUUID(), periode = it) }
                }
            }
        }

        sealed class Ufullstendig : Bosituasjon() {
            /** Dette er en midlertid tilstand hvor det er valgt Ikke Eps, men ikke tatt stilling til bosituasjon Enslig eller med voksne
            Data klassen kan godt få et bedre navn... */
            data class HarIkkeEps(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Ufullstendig() {
                override fun erLik(other: Grunnlag): Boolean {
                    return other is HarIkkeEps
                }
            }

            /** Dette er en midlertid tilstand hvor det er valgt Eps, men ikke tatt stilling til om eps er ufør flyktning eller ikke
            Data klassen kan godt få et bedre navn... */
            data class HarEps(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
                val fnr: Fnr,
            ) : Ufullstendig() {
                override fun erLik(other: Grunnlag): Boolean {
                    return other is HarEps
                }
            }
        }

        fun harEktefelle(): Boolean {
            return this is Fullstendig.EktefellePartnerSamboer || this is Ufullstendig.HarEps
        }

        fun harEndretEllerFjernetEktefelle(gjeldendeBosituasjon: Bosituasjon): Boolean {
            val gjeldendeEpsFnr: Fnr? =
                (gjeldendeBosituasjon as? Fullstendig.EktefellePartnerSamboer)?.fnr
                    ?: (gjeldendeBosituasjon as? Ufullstendig.HarEps)?.fnr
            val nyEpsFnr: Fnr? = (this as? Fullstendig.EktefellePartnerSamboer)?.fnr
                ?: (this as? Ufullstendig.HarEps)?.fnr

            // begge er null eller samme fnr -> ingen endring
            if (gjeldendeEpsFnr == nyEpsFnr) {
                return false
            }
            // gjeldende er null -> ingen endring
            if (gjeldendeEpsFnr == null) {
                return false
            }
            return true
        }
    }
}

// Generell kommentar til extension-funksjonene: Disse er lagt til slik at vi enklere skal få kontroll over migreringen fra 1 bosituasjon til fler.

fun List<Grunnlag.Bosituasjon>.harFlerEnnEnBosituasjonsperiode(): Boolean = size > 1

/**
 * Kan være tom under vilkårsvurdering/datainnsamlings-tilstanden.
 * Kan være maks 1 i alle andre tilstander.
 * @throws IllegalStateException hvis size > 1
 * */
fun List<Grunnlag.Bosituasjon>.harEktefelle(): Boolean {
    return singleOrThrow().harEktefelle()
}

/**
 * Kan være tom under vilkårsvurdering/datainnsamlings-tilstanden.
 * Kan være maks 1 i alle andre tilstander.
 * @throws IllegalStateException hvis size != 1
 * */
fun List<Grunnlag.Bosituasjon>.singleOrThrow(): Grunnlag.Bosituasjon {
    if (size != 1) {
        throw IllegalStateException("Forventet 1 Grunnlag.Bosituasjon, men var: ${this.size}")
    }
    return this.first()
}

/**
 * Kan være tom under vilkårsvurdering/datainnsamlings-tilstanden.
 * Kan være maks 1 i alle andre tilstander.
 *  * @throws IllegalStateException hvis size != 1
 * */
fun List<Grunnlag.Bosituasjon>.singleFullstendigOrThrow(): Grunnlag.Bosituasjon.Fullstendig {
    return singleOrThrow().fullstendigOrThrow()
}

fun List<Grunnlag.Bosituasjon>.throwIfMultiple(): Grunnlag.Bosituasjon? {
    if (this.size > 1) {
        throw IllegalStateException("Det er ikke støtte for flere bosituasjoner")
    }
    return this.firstOrNull()
}

fun Grunnlag.Bosituasjon.fullstendigOrThrow(): Grunnlag.Bosituasjon.Fullstendig {
    return (this as? Grunnlag.Bosituasjon.Fullstendig)
        ?: throw IllegalStateException("Forventet Grunnlag.Bosituasjon type Fullstendig, men var ${this::class.simpleName}")
}
