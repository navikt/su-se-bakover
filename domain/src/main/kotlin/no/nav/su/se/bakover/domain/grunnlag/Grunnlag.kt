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
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.common.tid.periode.minAndMaxOf
import no.nav.su.se.bakover.common.tid.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinjeMedSegSelv
import no.nav.su.se.bakover.domain.tidslinje.fjernPerioder
import org.jetbrains.annotations.TestOnly
import satser.domain.Satskategori
import java.time.Clock
import java.util.UUID

sealed interface Grunnlag {
    val id: UUID

    val periode: Periode

    fun tilstøter(other: Grunnlag) = this.periode.tilstøter(other.periode)

    /**
     * unnlater å sjekke på ID og opprettet
     */
    fun erLik(other: Grunnlag): Boolean

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
    ) : Grunnlag, KanPlasseresPåTidslinje<Uføregrunnlag> {
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

    /**
     * Domain model (create a flat model in addition to this in database-layer)
     */
    sealed interface Bosituasjon : Grunnlag {
        abstract override val id: UUID
        abstract override val periode: Periode
        val opprettet: Tidspunkt
        val satskategori: Satskategori?
        val eps: Fnr?

        /**
         * Bosituasjon med ektefelle/partner/samboer.
         * NB: ikke det samme som om bruker bor med eller uten andre personer.
         */
        fun harEPS(): Boolean

        fun oppdaterBosituasjonsperiode(oppdatertPeriode: Periode): Fullstendig {
            return when (this) {
                is Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> this.copy(periode = oppdatertPeriode)
                is Fullstendig.Enslig -> this.copy(periode = oppdatertPeriode)
                is Ufullstendig -> throw IllegalStateException("Tillatter ikke ufullstendige bosituasjoner")
            }
        }

        companion object {
            // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
            fun List<Fullstendig>.oppdaterBosituasjonsperiode(oppdatertPeriode: Periode): List<Fullstendig> {
                return this.map { it.oppdaterBosituasjonsperiode(oppdatertPeriode) }
            }

            fun List<Fullstendig>.slåSammenPeriodeOgBosituasjon(): List<Fullstendig> {
                return this.sortedBy { it.periode.fraOgMed }
                    .fold(mutableListOf<MutableList<Fullstendig>>()) { acc, bosituasjon ->
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
                        it.first().oppdaterBosituasjonsperiode(periode)
                    }
            }

            private fun List<Fullstendig>.sisteBosituasjonsgrunnlagErLikOgTilstøtende(other: Fullstendig): Boolean {
                return this.last().let { it.tilstøter(other) && it.erLik(other) }
            }

            fun List<Bosituasjon>.minsteAntallSammenhengendePerioder(): List<Periode> {
                return map { it.periode }.minsteAntallSammenhengendePerioder()
            }

            fun List<Bosituasjon>.perioderMedEPS(): List<Periode> {
                return filter { it.harEPS() }.map { it.periode }.minsteAntallSammenhengendePerioder()
            }

            fun List<Fullstendig>.perioderUtenEPS(): List<Periode> {
                return filter { !it.harEPS() }.map { it.periode }.minsteAntallSammenhengendePerioder()
            }

            fun List<Bosituasjon>.harOverlappende(): Boolean {
                return map { it.periode }.harOverlappende()
            }

            fun List<Bosituasjon>.harEPS(): Boolean {
                return any { it.harEPS() }
            }

            fun List<Bosituasjon>.inneholderUfullstendigeBosituasjoner(): Boolean {
                return this.filterIsInstance<Ufullstendig>().isNotEmpty()
            }

            /**
             * Ignorerer rekkefølge & perioder
             * Passer ikke så bra for revurdering
             */
            fun List<Bosituasjon>.harFjernetEllerEndretEps(oppdatertBosituasjon: List<Bosituasjon>): Boolean {
                val nåværende = this.mapNotNull { it.eps }.distinct()
                val oppdatert = oppdatertBosituasjon.mapNotNull { it.eps }.distinct()

                return nåværende.intersect(oppdatert).toList() != nåværende
            }
        }

        sealed interface Fullstendig : Bosituasjon, KanPlasseresPåTidslinje<Fullstendig> {
            abstract override val satskategori: Satskategori

            sealed interface EktefellePartnerSamboer : Fullstendig {
                val fnr: Fnr
                override val eps: Fnr? get() = fnr
                override fun harEPS(): Boolean = true

                sealed interface Under67 : EktefellePartnerSamboer {
                    data class UførFlyktning(
                        override val id: UUID,
                        override val opprettet: Tidspunkt,
                        override val periode: Periode,
                        override val fnr: Fnr,
                    ) : EktefellePartnerSamboer {
                        override val satskategori: Satskategori = Satskategori.ORDINÆR

                        override fun erLik(other: Grunnlag): Boolean {
                            if (other !is UførFlyktning) {
                                return false
                            }
                            return this.fnr == other.fnr
                        }

                        override fun copy(args: CopyArgs.Tidslinje): UførFlyktning = when (args) {
                            CopyArgs.Tidslinje.Full -> {
                                copy(id = UUID.randomUUID())
                            }

                            is CopyArgs.Tidslinje.NyPeriode -> {
                                copy(id = UUID.randomUUID(), periode = args.periode)
                            }
                        }
                    }

                    data class IkkeUførFlyktning(
                        override val id: UUID,
                        override val opprettet: Tidspunkt,
                        override val periode: Periode,
                        override val fnr: Fnr,
                    ) : EktefellePartnerSamboer {

                        override val satskategori: Satskategori = Satskategori.HØY

                        override fun erLik(other: Grunnlag): Boolean {
                            if (other !is IkkeUførFlyktning) {
                                return false
                            }
                            return this.fnr == other.fnr
                        }

                        override fun copy(args: CopyArgs.Tidslinje): IkkeUførFlyktning = when (args) {
                            CopyArgs.Tidslinje.Full -> {
                                copy(id = UUID.randomUUID())
                            }

                            is CopyArgs.Tidslinje.NyPeriode -> {
                                copy(id = UUID.randomUUID(), periode = args.periode)
                            }
                        }
                    }
                }

                data class SektiSyvEllerEldre(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val periode: Periode,
                    override val fnr: Fnr,
                ) : EktefellePartnerSamboer {

                    override val satskategori: Satskategori = Satskategori.ORDINÆR

                    override fun erLik(other: Grunnlag): Boolean {
                        if (other !is SektiSyvEllerEldre) {
                            return false
                        }
                        return this.fnr == other.fnr
                    }

                    override fun copy(args: CopyArgs.Tidslinje): SektiSyvEllerEldre = when (args) {
                        CopyArgs.Tidslinje.Full -> {
                            copy(id = UUID.randomUUID())
                        }

                        is CopyArgs.Tidslinje.NyPeriode -> {
                            copy(id = UUID.randomUUID(), periode = args.periode)
                        }
                    }
                }
            }

            /** Bor ikke med noen over 18 år */
            data class Enslig(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Fullstendig {
                override val eps: Fnr? get() = null
                override val satskategori: Satskategori = Satskategori.HØY

                override fun harEPS(): Boolean {
                    return false
                }

                override fun erLik(other: Grunnlag): Boolean {
                    return other is Enslig
                }

                override fun copy(args: CopyArgs.Tidslinje): Enslig = when (args) {
                    CopyArgs.Tidslinje.Full -> {
                        copy(id = UUID.randomUUID())
                    }

                    is CopyArgs.Tidslinje.NyPeriode -> {
                        copy(id = UUID.randomUUID(), periode = args.periode)
                    }
                }
            }

            data class DelerBoligMedVoksneBarnEllerAnnenVoksen(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Fullstendig {
                override val eps: Fnr? get() = null
                override val satskategori: Satskategori = Satskategori.ORDINÆR

                override fun harEPS(): Boolean {
                    return false
                }

                override fun erLik(other: Grunnlag): Boolean {
                    return other is DelerBoligMedVoksneBarnEllerAnnenVoksen
                }

                override fun copy(args: CopyArgs.Tidslinje): DelerBoligMedVoksneBarnEllerAnnenVoksen = when (args) {
                    CopyArgs.Tidslinje.Full -> {
                        copy(id = UUID.randomUUID())
                    }

                    is CopyArgs.Tidslinje.NyPeriode -> {
                        copy(id = UUID.randomUUID(), periode = args.periode)
                    }
                }
            }
        }

        sealed interface Ufullstendig : Bosituasjon {

            override val satskategori: Nothing? get() = null

            /** Dette er en midlertid tilstand hvor det er valgt Ikke Eps, men ikke tatt stilling til bosituasjon Enslig eller med voksne
             Data klassen kan godt få et bedre navn... */
            data class HarIkkeEps(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Ufullstendig {
                override val eps: Fnr? get() = null
                override fun harEPS(): Boolean {
                    return false
                }

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
            ) : Ufullstendig {
                override val eps: Fnr? get() = fnr
                override fun harEPS(): Boolean {
                    return true
                }

                override fun erLik(other: Grunnlag): Boolean {
                    return other is HarEps
                }
            }
        }
    }
}

fun Grunnlag.Bosituasjon.fullstendigOrThrow(): Grunnlag.Bosituasjon.Fullstendig {
    return (this as? Grunnlag.Bosituasjon.Fullstendig)
        ?: throw IllegalStateException("Forventet Grunnlag.Bosituasjon type Fullstendig, men var ${this::class.qualifiedName}")
}

fun List<Grunnlag.Bosituasjon>.singleFullstendigOrThrow(): Grunnlag.Bosituasjon.Fullstendig {
    return singleOrThrow().fullstendigOrThrow()
}

fun List<Grunnlag.Bosituasjon>.singleFullstendigEpsOrNull(): Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer? {
    return singleOrNull() as? Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer
}

fun List<Grunnlag.Bosituasjon>.singleOrThrow(): Grunnlag.Bosituasjon {
    if (size != 1) {
        throw IllegalStateException("Forventet 1 Grunnlag.Bosituasjon, men var: ${this.size}")
    }
    return this.first()
}

/**
 * Listen med periode trenger ikke være sammenhengende eller sortert.
 * Den kan og inneholde duplikater.
 */
fun List<Grunnlag>.periode(): Periode = this.map { it.periode }.minAndMaxOf()
