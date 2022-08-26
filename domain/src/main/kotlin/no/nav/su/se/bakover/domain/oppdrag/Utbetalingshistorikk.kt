package no.nav.su.se.bakover.domain.oppdrag

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.periode.Periode
import java.time.Clock
import java.time.LocalDate

data class Utbetalingshistorikk(
    private val nyeUtbetalingslinjer: List<Utbetalingslinje>,
    private val eksisterendeUtbetalingslinjer: List<Utbetalingslinje>,
    private val clock: Clock,
) {
    fun generer(): List<Utbetalingslinje> {
        val nye = finnUtbetalingslinjerSomSkalRekonstrueres()
            .filterIsInstance<Utbetalingslinje.Ny>().also {
                (nyeUtbetalingslinjer + it).oppdaterReferanseTilForrigeUtbetalingslinje()
            }

        val endringer = finnUtbetalingslinjerSomSkalRekonstrueres()
            .filterIsInstance<Utbetalingslinje.Endring>()

        val rekonstruerte = finnEndringerForNyeLinjer(
            nye = nye,
            endringer = endringer,
        ).map { rekonstruer(it) }
            .flatMap { (rekonstruertNy, rekonstruerteEndringer) ->
                listOf(rekonstruertNy) + rekonstruerteEndringer
            }

        return (nyeUtbetalingslinjer + rekonstruerte).also {
            it.oppdaterReferanseTilForrigeUtbetalingslinje()
        }
    }

    private fun minumumFraOgMedDatoForRekonstruerteLinjer(): LocalDate {
        return rekonstruerEksisterendeUtbetalingerEtterDato().plusDays(1)
    }

    private fun finnUtbetalingslinjerSomSkalRekonstrueres(): List<Utbetalingslinje> {
        return eksisterendeUtbetalingslinjer.filter { it.tilOgMed.isAfter(rekonstruerEksisterendeUtbetalingerEtterDato()) }
    }

    private fun rekonstruerEksisterendeUtbetalingerEtterDato(): LocalDate {
        return nyeUtbetalingslinjer.last().let {
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
}

private fun List<Utbetalingslinje>.oppdaterReferanseTilForrigeUtbetalingslinje() {
    zipWithNext { a, b ->
        when (b) {
            is Utbetalingslinje.Endring.Opphør -> b.forrigeUtbetalingslinjeId = a.forrigeUtbetalingslinjeId
            is Utbetalingslinje.Endring.Reaktivering -> b.forrigeUtbetalingslinjeId = a.forrigeUtbetalingslinjeId
            is Utbetalingslinje.Endring.Stans -> b.forrigeUtbetalingslinjeId = a.forrigeUtbetalingslinjeId
            is Utbetalingslinje.Ny -> b.forrigeUtbetalingslinjeId = a.id
        }
    }
}
