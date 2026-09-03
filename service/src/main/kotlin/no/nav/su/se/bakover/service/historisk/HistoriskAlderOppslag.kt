package no.nav.su.se.bakover.service.historisk

import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskVedtaksperiode
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskYtelseForMåned
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskYtelsestidslinje
import java.time.LocalDate
import java.time.YearMonth

class HistoriskAlderOppslag(
    private val repo: HistoriskAlderProjeksjonRepo,
) {
    fun harSak(personident: String): Boolean = repo.harSak(personident)

    fun hentVedtaksperioder(personident: String): List<HistoriskVedtaksperiode> =
        repo.hentVedtaksperioder(personident)

    fun hentTidslinje(
        personident: String,
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): HistoriskYtelsestidslinje {
        require(fraOgMed <= tilOgMed) { "fraOgMed må være før eller lik tilOgMed" }
        val perioder =
            repo.hentYtelsesperioder(
                personident = personident,
                fraOgMed = fraOgMed.atDay(1),
                tilOgMed = tilOgMed.atEndOfMonth(),
            )
        val måneder =
            generateSequence(fraOgMed) { it.plusMonths(1) }
                .takeWhile { it <= tilOgMed }
                .map { måned ->
                    val dato = måned.atDay(1)
                    val treff = perioder.filter { dato in it.fraOgMed..it.tilOgMed }
                    when {
                        treff.isEmpty() -> {
                            HistoriskYtelseForMåned.IngenYtelse(måned)
                        }

                        else -> {
                            check(treff.size == 1) {
                                "Fant overlappende historiske ytelsesperioder for samme person og måned"
                            }
                            treff.single().let {
                                HistoriskYtelseForMåned.Ytelse(
                                    måned = måned,
                                    stønadId = it.stønadId,
                                    vedtakId = it.vedtakId,
                                    sats = it.sats,
                                    fradrag = it.fradrag,
                                    bosituasjon = it.bosituasjon,
                                    årligYtelsesbeløp = it.årligYtelsesbeløp,
                                )
                            }
                        }
                    }
                }.toList()

        return HistoriskYtelsestidslinje(
            personident = personident,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            måneder = måneder,
        )
    }

    fun harYtelsePåDato(personident: String, dato: LocalDate): Boolean =
        hentTidslinje(personident, YearMonth.from(dato), YearMonth.from(dato))
            .måneder
            .single() is HistoriskYtelseForMåned.Ytelse

    fun harYtelseIMinstÉnMåned(
        personident: String,
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): Boolean =
        hentTidslinje(personident, fraOgMed, tilOgMed).måneder.any {
            it is HistoriskYtelseForMåned.Ytelse
        }

    fun harYtelseIHelePerioden(
        personident: String,
        fraOgMed: YearMonth,
        tilOgMed: YearMonth,
    ): Boolean =
        hentTidslinje(personident, fraOgMed, tilOgMed).måneder.all {
            it is HistoriskYtelseForMåned.Ytelse
        }
}
