package no.nav.su.se.bakover.domain.grunnlag

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import arrow.core.sequence
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.satser.Satskategori
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinjeMedSegSelv
import no.nav.su.se.bakover.domain.tidslinje.masker
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
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
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
    ) : Grunnlag(), Fradrag by fradrag, KanPlasseresPåTidslinjeMedSegSelv<Fradragsgrunnlag> {
        override val periode: Periode = fradrag.periode

        fun oppdaterFradragsperiode(
            oppdatertPeriode: Periode,
        ): Either<UgyldigFradragsgrunnlag, Fradragsgrunnlag> {
            return this.copyInternal(CopyArgs.Snitt(oppdatertPeriode)).flatMap {
                it?.right() ?: tryCreate(
                    id = UUID.randomUUID(),
                    opprettet = Tidspunkt.now(), // TODO jah: Ta inn clock
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

        fun fjernFradragEPS(perioder: List<Periode>): List<Fradragsgrunnlag> {
            return when (tilhørerEps()) {
                true -> {
                    masker(perioder = perioder)
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

            // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
            fun List<Fradragsgrunnlag>.oppdaterFradragsperiode(
                oppdatertPeriode: Periode,
            ): Either<UgyldigFradragsgrunnlag, List<Fradragsgrunnlag>> {
                return this.map { it.oppdaterFradragsperiode(oppdatertPeriode) }.sequence()
            }

            fun List<Fradragsgrunnlag>.harEpsInntekt() = this.any { it.fradrag.tilhørerEps() }
            fun List<Fradragsgrunnlag>.perioder(): List<Periode> = map { it.periode }.minsteAntallSammenhengendePerioder()
            fun List<Fradragsgrunnlag>.allePerioderMedEPS(): List<Periode> {
                return filter { it.tilhørerEps() }.map { it.periode }.minsteAntallSammenhengendePerioder()
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
                            fradrag = FradragFactory.nyFradragsperiode(
                                fradragstype = it.first().fradragstype,
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
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            }
        }
    }

    /**
     * Domain model (create a flat model in addition to this in database-layer)
     */
    sealed class Bosituasjon : Grunnlag() {
        abstract override val id: UUID
        abstract val opprettet: Tidspunkt
        abstract val satskategori: Satskategori?

        /**
         * Bosituasjon med ektefelle/partner/samboer.
         * NB: ikke det samme som om bruker bor med eller uten andre personer.
         */
        abstract fun harEPS(): Boolean

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
            // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
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
                            is Ufullstendig -> throw IllegalStateException("Kan ikke ha ufullstendige bosituasjoner")
                        }
                    }
            }

            private fun List<Bosituasjon>.sisteBosituasjonsgrunnlagErLikOgTilstøtende(other: Bosituasjon) =
                this.last().let { it.tilstøter(other) && it.erLik(other) }

            fun List<Bosituasjon>.minsteAntallSammenhengendePerioder(): List<Periode> {
                return map { it.periode }.minsteAntallSammenhengendePerioder()
            }

            fun List<Bosituasjon>.perioderMedEPS(): List<Periode> {
                return filter { it.harEPS() }.map { it.periode }.minsteAntallSammenhengendePerioder()
            }

            fun List<Bosituasjon>.perioderUtenEPS(): List<Periode> {
                return filter { !it.harEPS() }.map { it.periode }.minsteAntallSammenhengendePerioder()
            }

            fun List<Bosituasjon>.harOverlappende(): Boolean {
                return map { it.periode }.harOverlappende()
            }

            fun List<Bosituasjon>.harEPS(): Boolean {
                return any { it.harEPS() }
            }
        }

        sealed class Fullstendig : Bosituasjon(), KanPlasseresPåTidslinje<Fullstendig> {
            abstract override val satskategori: Satskategori

            sealed class EktefellePartnerSamboer : Fullstendig() {
                abstract val fnr: Fnr
                override fun harEPS(): Boolean {
                    return true
                }

                sealed class Under67 : EktefellePartnerSamboer() {
                    data class UførFlyktning(
                        override val id: UUID,
                        override val opprettet: Tidspunkt,
                        override val periode: Periode,
                        override val fnr: Fnr,
                    ) : EktefellePartnerSamboer() {

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
                            is CopyArgs.Tidslinje.Maskert -> {
                                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
                            }
                        }
                    }

                    data class IkkeUførFlyktning(
                        override val id: UUID,
                        override val opprettet: Tidspunkt,
                        override val periode: Periode,
                        override val fnr: Fnr,
                    ) : EktefellePartnerSamboer() {

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
                            is CopyArgs.Tidslinje.Maskert -> {
                                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
                            }
                        }
                    }
                }

                data class SektiSyvEllerEldre(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val periode: Periode,
                    override val fnr: Fnr,
                ) : EktefellePartnerSamboer() {

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
                        is CopyArgs.Tidslinje.Maskert -> {
                            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
                        }
                    }
                }
            }

            /** Bor ikke med noen over 18 år */
            data class Enslig(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Fullstendig() {

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
                    is CopyArgs.Tidslinje.Maskert -> {
                        copy(args.args).copy(opprettet = opprettet.plusUnits(1))
                    }
                }
            }

            data class DelerBoligMedVoksneBarnEllerAnnenVoksen(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Fullstendig() {

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
                    is CopyArgs.Tidslinje.Maskert -> {
                        copy(args.args).copy(opprettet = opprettet.plusUnits(1))
                    }
                }
            }
        }

        sealed class Ufullstendig : Bosituasjon() {

            override val satskategori: Nothing? = null

            /** Dette er en midlertid tilstand hvor det er valgt Ikke Eps, men ikke tatt stilling til bosituasjon Enslig eller med voksne
             Data klassen kan godt få et bedre navn... */
            data class HarIkkeEps(
                override val id: UUID,
                override val opprettet: Tidspunkt,
                override val periode: Periode,
            ) : Ufullstendig() {
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
            ) : Ufullstendig() {
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
        ?: throw IllegalStateException("Forventet Grunnlag.Bosituasjon type Fullstendig, men var ${this::class.simpleName}")
}

fun List<Grunnlag.Bosituasjon>.singleFullstendigOrThrow(): Grunnlag.Bosituasjon.Fullstendig {
    return singleOrThrow().fullstendigOrThrow()
}

fun List<Grunnlag.Bosituasjon>.singleOrThrow(): Grunnlag.Bosituasjon {
    if (size != 1) {
        throw IllegalStateException("Forventet 1 Grunnlag.Bosituasjon, men var: ${this.size}")
    }
    return this.first()
}
