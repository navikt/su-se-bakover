package vilkår.inntekt.domain.grunnlag

import arrow.core.Either
import arrow.core.NonEmptyCollection
import arrow.core.NonEmptyList
import arrow.core.flatMap
import arrow.core.fold
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.SlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinjeMedSegSelv
import no.nav.su.se.bakover.common.domain.tidslinje.fjernPerioder
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.måneder
import org.jetbrains.annotations.TestOnly
import vilkår.common.domain.grunnlag.Grunnlag
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

data class Fradragsgrunnlag private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    val fradrag: Fradrag,
) : Grunnlag,
    Fradrag by fradrag,
    KanPlasseresPåTidslinjeMedSegSelv<Fradragsgrunnlag> {
    override val periode: Periode = fradrag.periode

    /**
     * Sjekker om fradragsgrunnlaget kan slås sammen med et annet fradragsgrunnlag.
     * For å kunne slås sammen må de ha samme fradragstype, utenlandskInntekt, tilhører, og månedsbeløp.
     * I tillegg må periodene tilstøte eller overlappe.
     *
     * Note on overlapp: Av historiske caser, må fradrag sjekke for overlapp, selv om vi skulle ønske at det ikke var nødvendig.
     */
    fun kanSlåSammen(other: Fradragsgrunnlag): Boolean {
        if (fradrag.fradragstype != other.fradragstype) {
            return false
        }
        if (fradrag.utenlandskInntekt != other.utenlandskInntekt) {
            return false
        }
        if (fradrag.tilhører != other.tilhører) {
            return false
        }
        if (fradrag.månedsbeløp != other.månedsbeløp) {
            return false
        }
        return periode.tilstøter(other.periode) || periode.overlapper(other.periode)
    }

    fun nyFradragsperiode(
        periode: Periode,
    ): Fradragsgrunnlag {
        return this.copy(
            fradrag = when (val f = this.fradrag) {
                is FradragForMåned -> f.nyPeriode(periode)
                is FradragForPeriode -> f.copy(periode = periode)
                is Fradragsgrunnlag -> throw IllegalStateException("Fradraget til Fradragsgrunnlag kan ikke være Fradragsgrunnlag (rekursjon).")
            },
        )
    }

    /**
     * Denne funksjonen er skreddersydd for endring av stønadsperiode under søknadsbehandling.
     * Dersom vi har lagt inn fradrag for en sub-periode og den nye stønadsperioden overlapper denne sub-perioden, vil snittet være den nye fradragsperioden.
     * Dersom den nye stønadsperioden ikke overlapper, vil vi ta med oss alle fradragene og sette den nye stønadsperioden som fradragsperiode.
     */
    fun oppdaterStønadsperiode(
        nyStønadsperiode: Stønadsperiode,
        clock: Clock,
    ): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag> {
        return this.copyInternal(CopyArgs.Snitt(nyStønadsperiode.periode)).flatMap {
            it?.right() ?: tryCreate(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                fradrag = FradragFactory.nyFradragsperiode(
                    fradragstype = this.fradrag.fradragstype,
                    månedsbeløp = this.fradrag.månedsbeløp,
                    periode = nyStønadsperiode.periode,
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

    override fun copyWithNewId(): Fradragsgrunnlag = this.copy(id = UUID.randomUUID())

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

    fun fjernFradragEPS(perioder: SlåttSammenIkkeOverlappendePerioder): List<Fradragsgrunnlag> {
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

        /**
         * Dersom en av List<Fradragsgrunnlag> er ugyldig, vil vi feile.
         */
        fun List<Fradragsgrunnlag>.oppdaterStønadsperiode(
            oppdatertPeriode: Stønadsperiode,
            clock: Clock,
        ): Either<UgyldigFradragsgrunnlag, List<Fradragsgrunnlag>> {
            return either {
                this@oppdaterStønadsperiode.map {
                    it.oppdaterStønadsperiode(oppdatertPeriode, clock).bind()
                }
            }
        }

        fun List<Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
        fun List<Fradragsgrunnlag>.perioder(): SlåttSammenIkkeOverlappendePerioder =
            map { it.periode }.minsteAntallSammenhengendePerioder()

        fun List<Fradragsgrunnlag>.allePerioderMedEPS(): SlåttSammenIkkeOverlappendePerioder {
            return filter { it.tilhørerEps() }.map { it.periode }.minsteAntallSammenhengendePerioder()
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

    fun oppdaterBeløpFraSupplement(beløp: BigDecimal): Fradragsgrunnlag = this.copy(
        fradrag = fradrag.oppdaterBeløp(beløp),
    )
}

fun Collection<Fradragsgrunnlag>.slåSammen(clock: Clock): List<Fradragsgrunnlag> {
    return this.toNonEmptyListOrNull()?.slåSammen(clock) ?: emptyList()
}

fun NonEmptyCollection<Fradragsgrunnlag>.slåSammen(clock: Clock): NonEmptyList<Fradragsgrunnlag> {
    val tidspunktNow = Tidspunkt.now(clock)
    return this.groupBy { Triple(it.fradragstype, it.tilhører, it.utenlandskInntekt) }.map { (triple, liste) ->
        val (fradragstype, tilhører, utenlandskinntekt) = triple
        val månedTilFradragForMåned = liste.map { it.periode }.måneder().associateWith { måned ->
            val f = liste.filter { it.periode.inneholder(måned) }
            Fradragsgrunnlag.create(
                opprettet = tidspunktNow,
                fradrag = FradragForMåned(
                    fradragstype = fradragstype,
                    månedsbeløp = f.sumOf { it.månedsbeløp },
                    måned = måned,
                    utenlandskInntekt = utenlandskinntekt,
                    tilhører = tilhører,
                ),
            )
        }

        månedTilFradragForMåned.fold(mutableListOf<Fradragsgrunnlag>()) { acc, (_, fradragsgrunnlag) ->
            if (acc.isEmpty()) {
                acc.add(fradragsgrunnlag)
                // Vi forsikrer oss tidligere om at disse ikke kan overlappe. Og siden månedene er sortert, vil det bli riktig med forlendMedEnMåned().
            } else if (acc.last().kanSlåSammen(fradragsgrunnlag)) {
                acc[acc.lastIndex] = acc.last().nyFradragsperiode(acc.last().periode.forlengMedEnMåned())
            } else {
                acc.add(fradragsgrunnlag)
            }
            acc
        }
    }.flatten().sortedWith(
        compareBy(
            { it.fradragstype.kategori },
            { it.tilhører },
            { it.utenlandskInntekt == null },
            { it.periode.fraOgMed },
        ),
    ).toNonEmptyList()
}
