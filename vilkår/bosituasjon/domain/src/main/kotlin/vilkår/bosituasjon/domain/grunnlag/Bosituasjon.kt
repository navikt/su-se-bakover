package vilkår.bosituasjon.domain.grunnlag

import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tid.periode.EmptyPerioder.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.domain.tid.periode.SlåttSammenIkkeOverlappendePerioder
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import satser.domain.Satskategori
import vilkår.common.domain.grunnlag.Grunnlag
import java.util.UUID

/**
 * Domain model (create a flat model in addition to this in database-layer)
 */
sealed interface Bosituasjon : Grunnlag {
    abstract override val id: UUID
    abstract override val periode: Periode
    override val opprettet: Tidspunkt
    val satskategori: Satskategori?
    val eps: Fnr?

    /**
     * Bosituasjon med ektefelle/partner/samboer.
     * NB: ikke det samme som om bruker bor med eller uten andre personer.
     */
    fun harEPS(): Boolean

    /**
     * Når vi endrer til å støtte flere bosituasjoner under søknadsbehandling, må denne endres.
     */
    fun oppdaterStønadsperiode(nyPeriode: Stønadsperiode): Fullstendig {
        return oppdaterPeriode(nyPeriode.periode)
    }

    fun oppdaterPeriode(nyPeriode: Periode): Fullstendig {
        return when (this) {
            is Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen -> this.copy(periode = nyPeriode)
            is Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning -> this.copy(periode = nyPeriode)
            is Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre -> this.copy(periode = nyPeriode)
            is Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning -> this.copy(periode = nyPeriode)
            is Fullstendig.Enslig -> this.copy(periode = nyPeriode)
            is Ufullstendig -> throw IllegalStateException("oppdaterStønadsperiode for bosituasjon: Tillater ikke ufullstendige bosituasjoner")
        }
    }

    companion object {
        /**
         * Denne er skreddersydd for [GjeldendeVedtaksdata] som allerede har generert nye IDer og tidspunkt.
         * Gjenbruker den første eksisterende id og opprettet per gruppering (tilstøter og er lik)
         * [Collection<Fullstendig>] kan ikke ha overlappende perioder, men kan ha hull.
         */
        fun Collection<Fullstendig>.slåSammenPeriodeOgBosituasjon(): List<Fullstendig> {
            require(!this.map { it.periode }.harOverlappende()) {
                "Kan ikke slå sammen bosituasjoner med overlappende perioder: ${this.map { it.periode }}"
            }
            return this.sortedBy { it.periode.fraOgMed }
                .fold(mutableListOf()) { acc, bosituasjon ->
                    if (acc.isEmpty()) {
                        acc.add(bosituasjon)
                    } else if (acc.last().kanSlåSammen(bosituasjon)) {
                        acc[acc.lastIndex] =
                            acc.last().oppdaterPeriode(acc.last().periode.forlengMedPeriode(bosituasjon.periode))
                    } else {
                        acc.add(bosituasjon)
                    }
                    acc
                }
        }

        fun List<Bosituasjon>.minsteAntallSammenhengendePerioder(): SlåttSammenIkkeOverlappendePerioder {
            return map { it.periode }.minsteAntallSammenhengendePerioder()
        }

        fun List<Bosituasjon>.perioderMedEPS(): SlåttSammenIkkeOverlappendePerioder {
            return filter { it.harEPS() }.map { it.periode }.minsteAntallSammenhengendePerioder()
        }

        fun List<Fullstendig>.perioderUtenEPS(): SlåttSammenIkkeOverlappendePerioder {
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

    sealed interface Fullstendig :
        Bosituasjon,
        KanPlasseresPåTidslinje<Fullstendig> {
        abstract override val satskategori: Satskategori

        /**
         * Sammenligner alle felter minus id, opprettet og sjekker at periodene tilstøter.
         * Det gir ikke mening og kalle denne hvis de overlapper, men det vil returneres false.
         * */
        fun kanSlåSammen(other: Bosituasjon): Boolean

        sealed interface EktefellePartnerSamboer : Fullstendig {
            val fnr: Fnr
            override val eps: Fnr get() = fnr
            override fun harEPS(): Boolean = true

            sealed interface Under67 : EktefellePartnerSamboer {
                data class UførFlyktning(
                    override val id: UUID,
                    override val opprettet: Tidspunkt,
                    override val periode: Periode,
                    override val fnr: Fnr,
                ) : EktefellePartnerSamboer {
                    override val satskategori: Satskategori = Satskategori.ORDINÆR

                    override fun kanSlåSammen(other: Bosituasjon) =
                        other is UførFlyktning && this.fnr == other.fnr && this.periode.tilstøter(other.periode)

                    // TODO jah og ramzi: Slett
                    override fun erLik(other: Grunnlag): Boolean {
                        if (other !is UførFlyktning) {
                            return false
                        }
                        return this.fnr == other.fnr
                    }

                    override fun copyWithNewId(): UførFlyktning = this.copy(id = UUID.randomUUID())

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
                    override fun kanSlåSammen(other: Bosituasjon) =
                        other is UførFlyktning && this.fnr == other.fnr && this.periode.tilstøter(other.periode)

                    // TODO jah og ramzi: Slett
                    override fun erLik(other: Grunnlag): Boolean {
                        if (other !is IkkeUførFlyktning) {
                            return false
                        }
                        return this.fnr == other.fnr
                    }

                    override fun copyWithNewId(): IkkeUførFlyktning = this.copy(id = UUID.randomUUID())

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

                override fun kanSlåSammen(other: Bosituasjon) =
                    other is SektiSyvEllerEldre && this.fnr == other.fnr && this.periode.tilstøter(other.periode)

                // TODO jah og ramzi: Slett
                override fun erLik(other: Grunnlag): Boolean {
                    if (other !is SektiSyvEllerEldre) {
                        return false
                    }
                    return this.fnr == other.fnr
                }

                override fun copyWithNewId(): SektiSyvEllerEldre = this.copy(id = UUID.randomUUID())

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

            override fun kanSlåSammen(other: Bosituasjon) = other is Enslig && this.periode.tilstøter(other.periode)

            // TODO jah og ramzi: Slett
            override fun erLik(other: Grunnlag): Boolean {
                return other is Enslig
            }

            override fun copyWithNewId(): Enslig = this.copy(id = UUID.randomUUID())

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

            override fun kanSlåSammen(other: Bosituasjon) =
                other is DelerBoligMedVoksneBarnEllerAnnenVoksen && this.periode.tilstøter(other.periode)

            // TODO jah og ramzi: Slett
            override fun erLik(other: Grunnlag): Boolean {
                return other is DelerBoligMedVoksneBarnEllerAnnenVoksen
            }

            override fun copyWithNewId(): DelerBoligMedVoksneBarnEllerAnnenVoksen = this.copy(id = UUID.randomUUID())

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
            override fun harEPS(): Boolean = false

            // TODO jah og ramzi: Slett
            override fun erLik(other: Grunnlag): Boolean {
                return other is HarIkkeEps
            }

            override fun copyWithNewId(): HarIkkeEps = this.copy(id = UUID.randomUUID())
        }

        /** Dette er en midlertid tilstand hvor det er valgt Eps, men ikke tatt stilling til om eps er ufør flyktning eller ikke
         Data klassen kan godt få et bedre navn... */
        data class HarEps(
            override val id: UUID,
            override val opprettet: Tidspunkt,
            override val periode: Periode,
            val fnr: Fnr,
        ) : Ufullstendig {
            override val eps: Fnr get() = fnr
            override fun harEPS(): Boolean = true

            override fun copyWithNewId(): HarEps = this.copy(id = UUID.randomUUID())

            // TODO jah og ramzi: Slett
            override fun erLik(other: Grunnlag): Boolean {
                return other is HarEps
            }
        }
    }
}

fun Bosituasjon.fullstendigOrThrow(): Bosituasjon.Fullstendig {
    return (this as? Bosituasjon.Fullstendig)
        ?: throw IllegalStateException("Forventet Grunnlag.Bosituasjon type Fullstendig, men var ${this::class.qualifiedName}")
}

fun List<Bosituasjon>.singleFullstendigOrThrow(): Bosituasjon.Fullstendig {
    return singleOrThrow().fullstendigOrThrow()
}

fun List<Bosituasjon>.singleFullstendigEpsOrNull(): Bosituasjon.Fullstendig.EktefellePartnerSamboer? {
    return singleOrNull() as? Bosituasjon.Fullstendig.EktefellePartnerSamboer
}

fun List<Bosituasjon>.singleOrThrow(): Bosituasjon {
    if (size != 1) {
        throw IllegalStateException("Forventet 1 Grunnlag.Bosituasjon, men var: ${this.size}")
    }
    return this.first()
}

fun List<Bosituasjon>.epsForMåned(): Map<Måned, Fnr> {
    return this.filter { it.eps != null }.map {
        it.periode.måneder() to it.eps!!
    }.flatMap {
        it.first.map { i -> i to it.second }
    }.toMap()
}

fun List<Bosituasjon>.epsTilPeriode(): Map<Fnr, List<Periode>> = this.filter { it.eps != null }
    .groupBy { it.eps!! }
    .mapValues { it.value.map { it.periode } }.mapValues {
        it.value.minsteAntallSammenhengendePerioder()
    }

fun List<Bosituasjon>.periodeTilEpsFnr(): Map<Periode, Fnr> = this.filter { it.eps != null }
    .groupBy { it.periode }.mapValues {
        it.value.single().eps!!
    }

fun List<Bosituasjon>.merEnn1Eps(): Boolean =
    this.filterIsInstance<Bosituasjon.Fullstendig.EktefellePartnerSamboer>().map { it.fnr }.distinct().size > 1
