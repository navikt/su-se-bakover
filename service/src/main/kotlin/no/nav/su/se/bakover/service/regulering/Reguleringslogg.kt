package no.nav.su.se.bakover.service.regulering

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.whenever
import no.nav.su.se.bakover.domain.regulering.Regulering
import no.nav.su.se.bakover.domain.regulering.Reguleringstype
import no.nav.su.se.bakover.domain.regulering.supplement.ReguleringssupplementFor
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellRegulering
import no.nav.su.se.bakover.domain.regulering.ÅrsakTilManuellReguleringKategori
import vilkår.inntekt.domain.grunnlag.FradragTilhører

/**
 * Kaster exception dersom noen av reguleringene er automatisk, eller dersom det benyttes historiske fradrag
 * @return et map fra årsaken til CSV-vennlig string - årsaken kan brukes til å filtrere for årsaken du er interessert i
 *
 * Eksempel output:
 *
 * kolonne1;kolonne2;kolonne3;kolonne4;kolonne5
 *
 * dataKolonne1;dataKolonne2;dataKolonne3;dataKolonne4;dataKolonne5
 */
fun List<Regulering>.toCSVLoggableString(): Map<ÅrsakTilManuellReguleringKategori, String> {
    return this
        .asSequence()
        .map { it.toCSVLoggableString() }
        .groupBy { it.keys }
        .map {
            mapOf(
                it.key.first() to when (it.key.first()) {
                    ÅrsakTilManuellReguleringKategori.DifferanseEtterRegulering ->
                        "saksnummer;vårtBeløpFørRegulering;bruttoBeløpFraAprilVedtak;nettoBeløpFraAprilVedtak;forventetBeløpEtterRegulering;eksternBruttoBeløpEtterRegulering;eksternNettoBeløpEtterRegulering;differanse;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.DifferanseFørRegulering ->
                        "saksnummer;vårtBeløpFørRegulering;eksternBruttoBeløpFørRegulering;eksternNettoBeløpFørRegulering;differanse;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.FantIkkeVedtakForApril ->
                        "saksnummer;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.FradragMåHåndteresManuelt -> throw IllegalStateException("${it.key.first()} er en historisk årsak og skal derfor ikke benyttes til logging av regulering med nyere typer")
                    ÅrsakTilManuellReguleringKategori.YtelseErMidlertidigStanset ->
                        "saksnummer\n" + it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.ForventetInntektErStørreEnn0 ->
                        "saksnummer\n" + it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.UtbetalingFeilet -> throw IllegalStateException("${it.key.first()} er en historisk årsak og skal derfor ikke benyttes til logging av regulering med nyere typer")
                    ÅrsakTilManuellReguleringKategori.BrukerManglerSupplement ->
                        "saksnummer;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.SupplementInneholderIkkeFradraget ->
                        "saksnummer;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.FinnesFlerePerioderAvFradrag ->
                        "saksnummer;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.FradragErUtenlandsinntekt ->
                        "saksnummer;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.SupplementHarFlereVedtaksperioderForFradrag ->
                        "saksnummer;perioder;fradragskategori;fradragTilhører\n" +
                            it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.AutomatiskSendingTilUtbetalingFeilet ->
                        "saksnummer\n" + it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.VedtakstidslinjeErIkkeSammenhengende ->
                        "saksnummer\n" + it.value.flatMap { it.values }.joinToString("\n")

                    ÅrsakTilManuellReguleringKategori.DelvisOpphør ->
                        "saksnummer;opphørsperioder\n" + it.value.flatMap { it.values }.joinToString("\n")
                },
            )
        }.toSet()
        .associate { it.keys.first() to it.values.first() }
}

/**
 * kaster exception hvis reguleringstype er automatisk - kun ment å brukes for manuelle reguleringer
 */
private fun Regulering.toCSVLoggableString(): Map<ÅrsakTilManuellReguleringKategori, String> {
    return when (reguleringstype) {
        Reguleringstype.AUTOMATISK -> throw IllegalStateException("toLoggableString() er kunt ment å bli brukt fra reguleringer som er manuell")
        is Reguleringstype.MANUELL -> {
            (this.reguleringstype as Reguleringstype.MANUELL).problemer.map { årsak ->
                when (årsak) {
                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                        supplementBruker = this.eksternSupplementRegulering.bruker,
                        supplementEps = this.eksternSupplementRegulering.eps,
                    )

                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FantIkkeVedtakForApril -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.DelvisOpphør -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0 -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset -> årsak.toCSVLoggableString(
                        saksnummer = saksnummer,
                    )

                    is ÅrsakTilManuellRegulering.Historisk -> throw IllegalStateException("Historiske årsaker skal ikke benyttes i til logging av reguleringer med nyere typer")
                }
            }.let {
                it.groupBy {
                    it.keys.first()
                }.mapValues {
                    it.value.joinToString("\n") {
                        it.values.first()
                    }
                }
            }
        }
    }
}

private fun ÅrsakTilManuellRegulering.AutomatiskSendingTilUtbetalingFeilet.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to saksnummer.toString())

private fun ÅrsakTilManuellRegulering.DelvisOpphør.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    this.opphørsperioder.joinToString(",") {
        "(${it.fraOgMed} - ${it.tilOgMed})"
    }.let {
        mapOf(this.kategori to "$saksnummer;$it")
    }

private fun ÅrsakTilManuellRegulering.ForventetInntektErStørreEnn0.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to saksnummer.toString())

private fun ÅrsakTilManuellRegulering.VedtakstidslinjeErIkkeSammenhengende.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to saksnummer.toString())

private fun ÅrsakTilManuellRegulering.YtelseErMidlertidigStanset.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to saksnummer.toString())

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.BrukerManglerSupplement.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to "$saksnummer;${this.fradragskategori};${this.fradragTilhører}")

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FinnesFlerePerioderAvFradrag.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to "$saksnummer;${this.fradragskategori};${this.fradragTilhører}")

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FradragErUtenlandsinntekt.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to "$saksnummer;${this.fradragskategori};${this.fradragTilhører}")

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementHarFlereVedtaksperioderForFradrag.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    this.eksterneReguleringsvedtakperioder.joinToString(",") {
        "(${it.fraOgMed} - ${it.tilOgMed})"
    }.let {
        mapOf(this.kategori to "$saksnummer;$it;${this.fradragskategori};${this.fradragTilhører}")
    }

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.SupplementInneholderIkkeFradraget.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to "$saksnummer;${this.fradragskategori};${this.fradragTilhører}")

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.FantIkkeVedtakForApril.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to "$saksnummer;${this.fradragskategori};${this.fradragTilhører}")

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseFørRegulering.toCSVLoggableString(
    saksnummer: Saksnummer,
): Map<ÅrsakTilManuellReguleringKategori, String> =
    mapOf(this.kategori to "$saksnummer;${this.vårtBeløpFørRegulering};${this.eksternBruttoBeløpFørRegulering};${this.eksternNettoBeløpFørRegulering};${this.differanse};${this.fradragskategori};${this.fradragTilhører}")

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering.toCSVLoggableString(
    saksnummer: Saksnummer,
    bruttoBeløpFraAprilVedtak: String,
    nettoBeløpFraAprilVedtak: String,
): String =
    "$saksnummer;${this.vårtBeløpFørRegulering};$bruttoBeløpFraAprilVedtak;$nettoBeløpFraAprilVedtak;${this.forventetBeløpEtterRegulering};${this.eksternBruttoBeløpEtterRegulering};${this.eksternNettoBeløpEtterRegulering};${this.differanse};${this.fradragskategori};${this.fradragTilhører}"

private fun ÅrsakTilManuellRegulering.FradragMåHåndteresManuelt.DifferanseEtterRegulering.toCSVLoggableString(
    saksnummer: Saksnummer,
    supplementBruker: ReguleringssupplementFor?,
    supplementEps: List<ReguleringssupplementFor>,
): Map<ÅrsakTilManuellReguleringKategori, String> {
    val årsakTilhørerBruker = this.fradragTilhører == FradragTilhører.BRUKER

    return årsakTilhørerBruker.whenever(
        isFalse = {
            val supplementForEpsForFradrag = supplementEps.mapNotNull { it.getForKategori(this.fradragskategori) }
            supplementForEpsForFradrag
                .joinToString("\n") {
                    val bruttoBeløpFraAprilVedtak = it.endringsvedtak?.eksterneData()?.first()?.bruttoYtelse ?: ""
                    val nettoBeløpFraAprilVedtak = it.endringsvedtak?.eksterneData()?.first()?.nettoYtelse ?: ""
                    this.toCSVLoggableString(
                        saksnummer = saksnummer,
                        bruttoBeløpFraAprilVedtak = bruttoBeløpFraAprilVedtak,
                        nettoBeløpFraAprilVedtak = nettoBeløpFraAprilVedtak,
                    )
                }
                .let { mapOf(this.kategori to it) }
        },
        isTrue = {
            val supplementForBrukerForFradrag = supplementBruker!!.getForKategori(this.fradragskategori)!!
            val bruttoBeløpFraAprilVedtak =
                supplementForBrukerForFradrag.endringsvedtak?.eksterneData()?.first()?.bruttoYtelse ?: ""
            val nettoBeløpFraAprilVedtak =
                supplementForBrukerForFradrag.endringsvedtak?.eksterneData()?.first()?.nettoYtelse ?: ""
            mapOf(
                this.kategori to this.toCSVLoggableString(
                    saksnummer = saksnummer,
                    bruttoBeløpFraAprilVedtak = bruttoBeløpFraAprilVedtak,
                    nettoBeløpFraAprilVedtak = nettoBeløpFraAprilVedtak,
                ),
            )
        },
    )
}
