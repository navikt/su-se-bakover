package no.nav.su.se.bakover.client.oppdrag.simulering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseKode
import no.nav.su.se.bakover.domain.oppdrag.simulering.KlasseType
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertDetaljer
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertUtbetaling
import no.nav.system.os.entiteter.beregningskjema.Beregning
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaa
import no.nav.system.os.entiteter.beregningskjema.BeregningStoppnivaaDetaljer
import no.nav.system.os.entiteter.beregningskjema.BeregningsPeriode
import no.nav.system.os.tjenester.simulerfpservice.simulerfpserviceservicetypes.SimulerBeregningResponse
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger

internal class SimuleringResponseMapperTest {

    @Test
    fun `mapper til internt format`() {
        SimuleringResponseMapper(oppdragResponse()).simulering shouldBe Simulering(
            gjelderId = Fnr("12345678910"),
            gjelderNavn = "gjelderNavn",
            datoBeregnet = 1.januar(2020),
            nettoBeløp = 15000,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    utbetaling = listOf(expectedUtbetaling),
                ),
            ),
        )
    }

    @Test
    fun `filterer vekk skatt og andre ytelser enn supplerende stønad`() {
        val medSkattOgAndreYtelser = oppdragResponse(
            listOf(
                lagBergningsperiode(
                    listOf(
                        lagStoppnivaa(
                            listOf(
                                lagDetalj(kode = "SUUFORE", "YTEL"),
                                lagDetalj(kode = "FSKTSKAT", "SKAT"),
                            ),
                        ),
                        lagStoppnivaa(
                            listOf(
                                lagDetalj(kode = "UFOREUT", type = "YTEL"),
                                lagDetalj(kode = "FSKTSKAT", "SKAT"),
                            ),
                        ),
                    ),
                ),
                lagBergningsperiode(
                    listOf(
                        lagStoppnivaa(
                            listOf(
                                lagDetalj(kode = "SUUFORE", "YTEL"),
                                lagDetalj(kode = "FSKTSKAT", "SKAT"),
                            ),
                        ),
                        lagStoppnivaa(
                            listOf(
                                lagDetalj(kode = "UFOREUT", type = "YTEL"),
                                lagDetalj(kode = "FSKTSKAT", "SKAT"),
                            ),
                        ),
                    ),
                ),
            ),
        )

        SimuleringResponseMapper(medSkattOgAndreYtelser).simulering shouldBe Simulering(
            gjelderId = Fnr("12345678910"),
            gjelderNavn = "gjelderNavn",
            datoBeregnet = 1.januar(2020),
            nettoBeløp = 15000,
            periodeList = listOf(
                SimulertPeriode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    utbetaling = listOf(expectedUtbetaling),
                ),
                SimulertPeriode(
                    fraOgMed = 1.januar(2020),
                    tilOgMed = 31.desember(2020),
                    utbetaling = listOf(expectedUtbetaling),
                ),
            ),
        )
    }
}

private val expectedUtbetaling = SimulertUtbetaling(
    fagSystemId = "fagsystemId",
    utbetalesTilId = Fnr("12345678910"),
    utbetalesTilNavn = "utbetalesTilNavn",
    forfall = 1.februar(2020),
    feilkonto = false,
    detaljer = listOf(
        SimulertDetaljer(
            faktiskFraOgMed = 1.januar(2020),
            faktiskTilOgMed = 31.desember(2020),
            konto = "1234.12.12345",
            belop = 12000,
            tilbakeforing = false,
            sats = 15000,
            typeSats = "MND",
            antallSats = 1,
            uforegrad = 50,
            klassekode = KlasseKode.SUUFORE,
            klassekodeBeskrivelse = "Supplerende stønad for uføre",
            klasseType = KlasseType.YTEL,
        ),
    ),
)

private fun oppdragResponse(
    beregningsperioder: List<BeregningsPeriode> = listOf(lagBergningsperiode()),
) = SimulerBeregningResponse().apply {
    simulering = Beregning().apply {
        gjelderId = "12345678910"
        gjelderNavn = "gjelderNavn"
        datoBeregnet = "2020-01-01"
        belop = BigDecimal(15000)
        beregningsperioder.forEach {
            beregningsPeriode.add(it)
        }
    }
}

private fun lagBergningsperiode(
    beregningStoppnivåer: List<BeregningStoppnivaa> = listOf(lagStoppnivaa()),
) = BeregningsPeriode().apply {
    periodeFom = "2020-01-01"
    periodeTom = "2020-12-31"
    beregningStoppnivåer.forEach {
        beregningStoppnivaa.add(it)
    }
}

private fun lagStoppnivaa(
    detaljer: List<BeregningStoppnivaaDetaljer> = listOf(lagDetalj()),
) = BeregningStoppnivaa().apply {
    fagsystemId = "fagsystemId"
    utbetalesTilId = "12345678910"
    utbetalesTilNavn = "utbetalesTilNavn"
    forfall = "2020-02-01"
    isFeilkonto = false
    detaljer.forEach {
        beregningStoppnivaaDetaljer.add(it)
    }
}

private fun lagDetalj(
    kode: String = "SUUFORE",
    type: String = "YTEL",
) = BeregningStoppnivaaDetaljer().apply {
    faktiskFom = "2020-01-01"
    faktiskTom = "2020-12-31"
    uforeGrad = BigInteger.valueOf(50L)
    antallSats = BigDecimal(1L)
    typeSats = "MND"
    belop = BigDecimal(12000)
    sats = BigDecimal(15000)
    kontoStreng = "1234.12.12345"
    isTilbakeforing = false
    klassekode = kode
    klasseKodeBeskrivelse = "Supplerende stønad for uføre"
    typeKlasse = type
}
