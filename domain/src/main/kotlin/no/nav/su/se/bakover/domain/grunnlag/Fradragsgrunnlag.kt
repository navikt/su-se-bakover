package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import beregning.domain.fradrag.Fradrag
import beregning.domain.fradrag.FradragFactory
import beregning.domain.fradrag.Fradragstype
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinjeMedSegSelv
import no.nav.su.se.bakover.common.domain.tidslinje.fjernPerioder
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import org.jetbrains.annotations.TestOnly
import vilkår.domain.grunnlag.Grunnlag
import java.time.Clock
import java.util.UUID

data class Fradragsgrunnlag private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    val fradrag: Fradrag,
) : Grunnlag, Fradrag by fradrag, KanPlasseresPåTidslinjeMedSegSelv<Fradragsgrunnlag> {
    override val periode: Periode = fradrag.periode

    fun oppdaterFradragsperiode(
        oppdatertPeriode: Periode,
        clock: Clock,
    ): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag> {
        return this.copyInternal(CopyArgs.Snitt(oppdatertPeriode)).flatMap {
            it?.right() ?: tryCreate(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                fradrag = FradragFactory.nyFradragsperiode(
                    fradragstype = this.fradrag.fradragstype,
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
        return copyInternal(args).getOrElse { throw IllegalArgumentException(it.toString()) }
    }

    private fun copyInternal(args: CopyArgs.Snitt): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag?> {
        return fradrag.copy(args)?.let {
            tryCreate(
                id = UUID.randomUUID(),
                opprettet = this.opprettet,
                fradrag = it,
            ).getOrElse { return it.left() }
        }.right()
    }

    fun fjernFradragEPS(perioder: List<Periode>): List<Fradragsgrunnlag> {
        return when (tilhørerEps()) {
            true -> {
                fjernPerioder(perioder = perioder)
            }

            false -> {
                listOf(this)
            }
        }
    }

    companion object {
        @TestOnly
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            fradrag: Fradrag,
        ) = tryCreate(id, opprettet, fradrag).getOrElse { throw IllegalArgumentException(it.toString()) }

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

        // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
        fun List<Fradragsgrunnlag>.oppdaterFradragsperiode(
            oppdatertPeriode: Periode,
            clock: Clock,
        ): Either<UgyldigFradragsgrunnlag, List<Fradragsgrunnlag>> {
            return either {
                this@oppdaterFradragsperiode.map {
                    it.oppdaterFradragsperiode(oppdatertPeriode, clock).bind()
                }
            }
        }

        fun List<Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
        fun List<Fradragsgrunnlag>.perioder(): List<Periode> =
            map { it.periode }.minsteAntallSammenhengendePerioder()

        fun List<Fradragsgrunnlag>.allePerioderMedEPS(): List<Periode> {
            return filter { it.tilhørerEps() }.map { it.periode }.minsteAntallSammenhengendePerioder()
        }

        fun List<Fradragsgrunnlag>.slåSammenPeriodeOgFradrag(clock: Clock): List<Fradragsgrunnlag> {
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
                        opprettet = Tidspunkt.now(clock),
                        fradrag = FradragFactory.nyFradragsperiode(
                            fradragstype = it.first().fradragstype,
                            månedsbeløp = it.first().månedsbeløp,
                            periode = periode,
                            utenlandskInntekt = it.first().utenlandskInntekt,
                            tilhører = it.first().tilhører,
                        ),
                    ).getOrElse { throw IllegalStateException(it.toString()) }
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

    sealed interface UgyldigFradragsgrunnlag {
        data object UgyldigFradragstypeForGrunnlag : UgyldigFradragsgrunnlag
    }

    override fun copy(args: CopyArgs.Tidslinje): Fradragsgrunnlag = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(id = UUID.randomUUID())
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            /**
             * TODO
             * Sammenhengen mellom Fradrag/Fradragsgrunnlag for å få til å kalle hele veien ned med [CopyArgs].
             * Pt lar det seg ikke gjøre pga av dobbelt impl av samme interface med ulik returtype.
             * All den tid [Fradragsgrunnlag] likevel ikke er ment å periodiseres i sammenheng med andre enn seg selv
             * (se forskjell på [KanPlasseresPåTidslinjeMedSegSelv]/[KanPlasseresPåTidslinje]) bør dette likevel være trygt så lenge
             * den som kaller kvitter seg med perioder som ikke overlapper først.
             */
            copy(id = UUID.randomUUID(), fradrag = fradrag.copy(CopyArgs.Snitt(args.periode))!!)
        }
    }
}
