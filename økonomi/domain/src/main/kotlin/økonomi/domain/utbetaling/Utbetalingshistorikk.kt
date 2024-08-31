package økonomi.domain.utbetaling

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.RekkefølgeGenerator
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.LocalDate
import java.util.LinkedList

/**
 * @throws IllegalArgumentException dersom vi ikke har en OK kvittering for alle utbetalingene.
 */
class Utbetalingshistorikk(
    private val nyeUtbetalingslinjer: NonEmptyList<Utbetalingslinje>,
    val eksisterendeUtbetalinger: Utbetalinger,
    private val nesteUtbetalingstidspunkt: () -> Tidspunkt,
    private val rekkefølgeGenerator: RekkefølgeGenerator,
    val aksepterKvitteringMedFeil: Boolean = false,
) {
    private val rekonstruerEtterDato = rekonstruerEksisterendeUtbetalingerEtterDato()
    private val minimumFraOgMedForRekonstruerteLinjer = minumumFraOgMedDatoForRekonstruerteLinjer()

    init {
        nyeUtbetalingslinjer.sjekkIngenNyeOverlapper()
        nyeUtbetalingslinjer.sjekkUnikOpprettet()
        nyeUtbetalingslinjer.sjekkSortering()
        if (aksepterKvitteringMedFeil) {
            eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterte()
        } else {
            eksisterendeUtbetalinger.kastHvisIkkeAlleErKvitterteUtenFeil()
        }
    }

    fun generer(): List<Utbetalingslinje> {
        return ForrigeUtbetalingslinjeKoblendeListe().apply {
            nyeUtbetalingslinjer.forEach { this.add(it) }
            finnEndringerForNyeLinjer(
                nye = finnUtbetalingslinjerSomSkalRekonstrueres()
                    .filterIsInstance<Utbetalingslinje.Ny>(),
                endringer = finnUtbetalingslinjerSomSkalRekonstrueres()
                    .filterIsInstance<Utbetalingslinje.Endring>(),
            ).map {
                rekonstruer(it)
            }.flatMap { (rekonstruertNy, rekonstruerteEndringer) ->
                listOf(rekonstruertNy) + rekonstruerteEndringer
            }.forEach { this.add(it) }
        }.also {
            kontrollerRekonstruertPeriodeErUforandret()
            it.kontrollerAtNyeLinjerHarFåttNyId()
            it.kontrollerAtEksisterendeErKjedetMedNyeUtbetalinger()
            it.sjekkAlleNyeLinjerHarForskjelligIdOgForrigeReferanse()
            it.sjekkSortering()
            it.sjekkUnikOpprettet()
        }
    }

    private fun minumumFraOgMedDatoForRekonstruerteLinjer(): LocalDate {
        return rekonstruerEtterDato.plusDays(1)
    }

    private fun finnUtbetalingslinjerSomSkalRekonstrueres(): List<Utbetalingslinje> {
        return eksisterendeUtbetalinger.utbetalingslinjer.filter { it.periode.tilOgMed.isAfter(rekonstruerEtterDato) }
    }

    private fun rekonstruerEksisterendeUtbetalingerEtterDato(): LocalDate {
        return nyeUtbetalingslinjer.last().periode.tilOgMed
    }

    private fun finnEndringerForNyeLinjer(
        nye: List<Utbetalingslinje.Ny>,
        endringer: List<Utbetalingslinje.Endring>,
    ): List<Pair<Utbetalingslinje.Ny, List<Utbetalingslinje.Endring>>> {
        return nye.map { ny -> ny to endringer.filter { it.id == ny.id } }
    }

    private fun rekonstruer(
        pair: Pair<Utbetalingslinje.Ny, List<Utbetalingslinje.Endring>>,
    ): Pair<Utbetalingslinje.Ny, List<Utbetalingslinje.Endring>> {
        return pair.let { (ny, endringer) ->
            val rekonstruertNy = ny.copy(
                id = UUID30.randomUUID(),
                opprettet = nesteUtbetalingstidspunkt(),
                rekkefølge = rekkefølgeGenerator.neste(),
                fraOgMed = maxOf(
                    ny.originalFraOgMed(),
                    minimumFraOgMedForRekonstruerteLinjer,
                ),
            )

            rekonstruertNy to endringer.map { endring ->
                when (endring) {
                    is Utbetalingslinje.Endring.Opphør -> {
                        Utbetalingslinje.Endring.Opphør(
                            utbetalingslinjeSomSkalEndres = rekonstruertNy,
                            virkningsperiode = Periode.create(
                                fraOgMed = maxOf(
                                    endring.periode.fraOgMed,
                                    minimumFraOgMedForRekonstruerteLinjer,
                                ),
                                tilOgMed = endring.periode.tilOgMed,
                            ),
                            opprettet = nesteUtbetalingstidspunkt(),
                            rekkefølge = rekkefølgeGenerator.neste(),
                        )
                    }

                    is Utbetalingslinje.Endring.Reaktivering -> {
                        Utbetalingslinje.Endring.Reaktivering(
                            utbetalingslinjeSomSkalEndres = rekonstruertNy,
                            virkningstidspunkt = maxOf(
                                endring.periode.fraOgMed,
                                minimumFraOgMedForRekonstruerteLinjer,
                            ),
                            opprettet = nesteUtbetalingstidspunkt(),
                            rekkefølge = rekkefølgeGenerator.neste(),
                        )
                    }

                    is Utbetalingslinje.Endring.Stans -> {
                        Utbetalingslinje.Endring.Stans(
                            utbetalingslinjeSomSkalEndres = rekonstruertNy,
                            virkningstidspunkt = maxOf(
                                endring.periode.fraOgMed,
                                minimumFraOgMedForRekonstruerteLinjer,
                            ),
                            opprettet = nesteUtbetalingstidspunkt(),
                            rekkefølge = rekkefølgeGenerator.neste(),
                        )
                    }
                }
            }
        }
    }

    private fun kontrollerRekonstruertPeriodeErUforandret() {
        finnUtbetalingslinjerSomSkalRekonstrueres()
            .ifNotEmpty {
                val periode = Periode.create(
                    fraOgMed = minimumFraOgMedForRekonstruerteLinjer,
                    tilOgMed = this.maxOf { it.periode.tilOgMed },
                )
                val eksisterende = eksisterendeUtbetalinger.utbetalingslinjer.mapTilTidslinje()

                val rekonstruert = this.mapTilTidslinje()

                // TODO jah: Dersom utbetalingslinjene hadde en ekvivalentMed-metode, kunne vi brukt den istedet for mapTilTidslinje().
                check(
                    eksisterende.ekvivalentMedInnenforPeriode(
                        rekonstruert,
                        periode,
                    ),
                ) { "Rekonstuerte utbetalingslinjer: $rekonstruert er ulik eksisterende: $eksisterende" }
            }
    }

    private fun List<Utbetalingslinje>.kontrollerAtEksisterendeErKjedetMedNyeUtbetalinger() {
        check(
            eksisterendeUtbetalinger.sisteUtbetalingslinje()?.let { siste ->
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
                        ny.id in eksisterendeUtbetalinger.utbetalingslinjer.map { it.id }
                    }
                },
        ) { "Alle nye utbetalingslinjer skal ha ny id" }
    }
}

class ForrigeUtbetalingslinjeKoblendeListe() : LinkedList<Utbetalingslinje>() {

    constructor(utbetalingslinje: List<Utbetalingslinje>) : this() {
        apply {
            utbetalingslinje.sorted().forEach {
                add(it)
            }
        }
    }

    override fun add(element: Utbetalingslinje): Boolean {
        addLast(element)
        return true
    }

    override fun addLast(e: Utbetalingslinje) {
        val siste = peekLast()
        if (siste != null) {
            when (e) {
                is Utbetalingslinje.Endring.Opphør -> {
                    super.addLast(e.oppdaterReferanseTilForrigeUtbetalingslinje(siste.forrigeUtbetalingslinjeId))
                }

                is Utbetalingslinje.Endring.Reaktivering -> {
                    super.addLast(e.oppdaterReferanseTilForrigeUtbetalingslinje(siste.forrigeUtbetalingslinjeId))
                }

                is Utbetalingslinje.Endring.Stans -> {
                    super.addLast(e.oppdaterReferanseTilForrigeUtbetalingslinje(siste.forrigeUtbetalingslinjeId))
                }

                is Utbetalingslinje.Ny -> {
                    super.addLast(e.oppdaterReferanseTilForrigeUtbetalingslinje(siste.id))
                }
            }
        } else {
            super.addLast(e)
        }
    }
}
