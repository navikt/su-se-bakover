package no.nav.su.se.bakover.domain.oppdrag

import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.util.LinkedList

class Utbetalingshistorikk(
    nyeUtbetalingslinjer: NonEmptyList<Utbetalingslinje>,
    eksisterendeUtbetalingslinjer: List<Utbetalingslinje>,
    private val clock: Clock,
) {
    private val sorterteNyeUtbetalingslinjer = nyeUtbetalingslinjer.sortedWith(utbetalingslinjeSortering)
    private val sorterteEksisterendeUtbetalingslinjer = eksisterendeUtbetalingslinjer.sortedWith(utbetalingslinjeSortering)
    init {
        require(!nyeUtbetalingslinjer.harOverlappende()) { "Nye utbetalingslinjer kan ikke overlappe" }
    }
    fun generer(): List<Utbetalingslinje> {
        val nyopprettedeLinjer = sorterteNyeUtbetalingslinjer.oppdaterReferanseTilForrigeUtbetalingslinje()

        val nyeForRekonstruksjon = finnUtbetalingslinjerSomSkalRekonstrueres()
            /**
             * Oppdater referansene før [rekonstruer] slik at kopiene som opprettes bevarer korrekte forrige ref.
             * Fjerner nyopprettede linjer til slutt da disse ikke er kandidater for rekonstruksjon.
             */
            .let { (nyopprettedeLinjer + it).oppdaterReferanseTilForrigeUtbetalingslinje() - nyopprettedeLinjer.toSet() }
            .filterIsInstance<Utbetalingslinje.Ny>()

        val enringerForRekonstruksjon = finnUtbetalingslinjerSomSkalRekonstrueres()
            .filterIsInstance<Utbetalingslinje.Endring>()

        val rekonstruerte = finnEndringerForNyeLinjer(
            nye = nyeForRekonstruksjon,
            endringer = enringerForRekonstruksjon,
        ).map { rekonstruer(it) }
            .flatMap { (rekonstruertNy, rekonstruerteEndringer) ->
                listOf(rekonstruertNy) + rekonstruerteEndringer
            }

        return (nyopprettedeLinjer + rekonstruerte).oppdaterReferanseTilForrigeUtbetalingslinje()
            .sortedWith(utbetalingslinjeSortering)
            .also {
                kontrollerAtTidslinjeForRekonstruertPeriodeErUforandret()
                it.kontrollerAtNyeLinjerHarFåttNyId()
                it.kontrollerAtEksisterendeErKjedetMedNyeUtbetalinger()
                it.sjekkAlleNyeLinjerHarForskjelligForrigeReferanse()
                it.sjekkSortering()
                it.sjekkIngenNyeOverlapper()
            }
    }

    private fun minumumFraOgMedDatoForRekonstruerteLinjer(): LocalDate {
        return rekonstruerEksisterendeUtbetalingerEtterDato().plusDays(1)
    }

    private fun finnUtbetalingslinjerSomSkalRekonstrueres(): List<Utbetalingslinje> {
        return sorterteEksisterendeUtbetalingslinjer.filter { it.tilOgMed.isAfter(rekonstruerEksisterendeUtbetalingerEtterDato()) }
    }

    private fun rekonstruerEksisterendeUtbetalingerEtterDato(): LocalDate {
        return sorterteNyeUtbetalingslinjer.last().let {
            when (it) {
                is Utbetalingslinje.Endring.Opphør -> it.virkningsperiode.tilOgMed
                is Utbetalingslinje.Endring.Reaktivering -> it.virkningsperiode.tilOgMed
                is Utbetalingslinje.Endring.Stans -> it.virkningsperiode.tilOgMed
                is Utbetalingslinje.Ny -> it.tilOgMed
            }
        }
    }

    private fun finnEndringerForNyeLinjer(
        nye: List<Utbetalingslinje.Ny>,
        endringer: List<Utbetalingslinje.Endring>,
    ): List<Pair<Utbetalingslinje.Ny, List<Utbetalingslinje.Endring>>> {
        return nye.map { ny -> ny to endringer.filter { it.id == ny.id } }
    }

    private fun rekonstruer(pair: Pair<Utbetalingslinje.Ny, List<Utbetalingslinje.Endring>>): Pair<Utbetalingslinje.Ny, List<Utbetalingslinje.Endring>> {
        return pair.let { (ny, endringer) ->
            val rekonstruertNy = ny.copy(
                id = UUID30.randomUUID(),
                opprettet = Tidspunkt.now(clock),
                fraOgMed = maxOf(
                    ny.fraOgMed,
                    minumumFraOgMedDatoForRekonstruerteLinjer(),
                ),
            )

            rekonstruertNy to endringer.map { endring ->
                when (endring) {
                    is Utbetalingslinje.Endring.Opphør -> {
                        Utbetalingslinje.Endring.Opphør(
                            utbetalingslinje = rekonstruertNy,
                            virkningsperiode = Periode.create(
                                fraOgMed = maxOf(
                                    endring.virkningsperiode.fraOgMed,
                                    minumumFraOgMedDatoForRekonstruerteLinjer(),
                                ),
                                tilOgMed = endring.virkningsperiode.tilOgMed,
                            ),
                            clock = clock,
                        )
                    }

                    is Utbetalingslinje.Endring.Reaktivering -> {
                        Utbetalingslinje.Endring.Reaktivering(
                            utbetalingslinje = rekonstruertNy,
                            virkningstidspunkt = maxOf(
                                endring.virkningsperiode.fraOgMed,
                                minumumFraOgMedDatoForRekonstruerteLinjer(),
                            ),
                            clock = clock,
                        )
                    }

                    is Utbetalingslinje.Endring.Stans -> {
                        Utbetalingslinje.Endring.Stans(
                            utbetalingslinje = rekonstruertNy,
                            virkningstidspunkt = maxOf(
                                endring.virkningsperiode.fraOgMed,
                                minumumFraOgMedDatoForRekonstruerteLinjer(),
                            ),
                            clock = clock,
                        )
                    }
                }
            }
        }
    }

    private fun kontrollerAtTidslinjeForRekonstruertPeriodeErUforandret() {
        finnUtbetalingslinjerSomSkalRekonstrueres()
            .ifNotEmpty {
                val periode = Periode.create(
                    fraOgMed = minumumFraOgMedDatoForRekonstruerteLinjer(),
                    tilOgMed = maxOf { it.tilOgMed },
                )

                val tidslinjeGammel = sorterteEksisterendeUtbetalingslinjer.tidslinje(
                    clock = clock,
                    periode = periode,
                ).getOrHandle { throw RuntimeException("Kunne ikke generere tidslinje: $it") }

                val tidslinjeNy = tidslinje(
                    clock = clock,
                    periode = periode,
                ).getOrHandle { throw RuntimeException("Kunne ikke generere tidslinje: $it") }

                check(tidslinjeGammel.ekvivalentMed(tidslinjeNy)) { "Rekonstuert tidslinje er ulik original" }
            }
    }

    private fun List<Utbetalingslinje>.kontrollerAtEksisterendeErKjedetMedNyeUtbetalinger() {
        check(
            sorterteEksisterendeUtbetalingslinjer.lastOrNull()?.let { siste ->
                first().let {
                    when (it) {
                        is Utbetalingslinje.Endring.Opphør -> it.forrigeUtbetalingslinjeId == siste.forrigeUtbetalingslinjeId
                        is Utbetalingslinje.Endring.Reaktivering -> it.forrigeUtbetalingslinjeId == siste.forrigeUtbetalingslinjeId
                        is Utbetalingslinje.Endring.Stans -> it.forrigeUtbetalingslinjeId == siste.forrigeUtbetalingslinjeId
                        is Utbetalingslinje.Ny -> it.forrigeUtbetalingslinjeId == siste.id
                    }
                }
            } ?: true,
        ) { "Den første av de nye utbetalingene skal være kjedet til eksisterende utbetalinger" }
    }

    private fun List<Utbetalingslinje>.kontrollerAtNyeLinjerHarFåttNyId() {
        check(
            filterIsInstance<Utbetalingslinje.Ny>()
                .let { nyeLinjer ->
                    nyeLinjer.none { ny ->
                        ny.id in sorterteEksisterendeUtbetalingslinjer.map { it.id }
                    }
                },
        ) { "Alle nye utbetalingslinjer skal ha ny id" }
    }
}

fun List<Utbetalingslinje>.oppdaterReferanseTilForrigeUtbetalingslinje(): List<Utbetalingslinje> {
    val queue = LinkedList(this.reversed())
    val result = mutableListOf<Utbetalingslinje>()
    while (queue.isNotEmpty()) {
        val siste = queue.poll()
        if (queue.isNotEmpty()) {
            val forrige = queue.peek()
            result.add(
                when (siste) {
                    is Utbetalingslinje.Endring.Opphør -> siste.oppdaterReferanseTilForrigeUtbetalingslinje(forrige.forrigeUtbetalingslinjeId)
                    is Utbetalingslinje.Endring.Reaktivering -> siste.oppdaterReferanseTilForrigeUtbetalingslinje(forrige.forrigeUtbetalingslinjeId)
                    is Utbetalingslinje.Endring.Stans -> siste.oppdaterReferanseTilForrigeUtbetalingslinje(forrige.forrigeUtbetalingslinjeId)
                    is Utbetalingslinje.Ny -> siste.oppdaterReferanseTilForrigeUtbetalingslinje(forrige.id)
                }
            )
        } else {
            result.add(siste)
        }
    }
    return result.reversed()
}
