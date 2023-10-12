package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.oktober
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Month

internal class TilbakekrevingSoapRequestMapperKtTest {

    @Test
    fun `mapper full tilbakekreving`() {
        val tilbakekrevingsvedtak =
            tilbakekrevingstestData(Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING)

        mapToTilbakekrevingsvedtakRequest(tilbakekrevingsvedtak).let {
            it.tilbakekrevingsvedtak.let { vedtakDto ->
                vedtakDto.kodeAksjon shouldBe "8"
                vedtakDto.vedtakId shouldBe BigInteger("436204")
                vedtakDto.kodeHjemmel shouldBe "SUL_13"
                vedtakDto.renterBeregnes shouldBe "N"
                vedtakDto.enhetAnsvarlig shouldBe "8020"
                vedtakDto.kontrollfelt shouldBe "2022-02-07-18.39.46.586953"
                vedtakDto.saksbehId shouldBe "saksbehandler"
                // den første Dto'en er en feilutbetaling
                vedtakDto.tilbakekrevingsperiode[0].let { periodeDto ->
                    periodeDto.periode.let {
                        it.fom = datatypeFactory.newXMLGregorianCalendar().apply {
                            year = 2021
                            month = Month.OCTOBER.value
                            day = 1
                        }
                        it.tom = datatypeFactory.newXMLGregorianCalendar().apply {
                            year = 2021
                            month = Month.OCTOBER.value
                            day = 31
                        }
                    }
                    periodeDto.renterBeregnes shouldBe "N"
                    periodeDto.belopRenter shouldBe BigDecimal("0")
                    periodeDto.tilbakekrevingsbelop[0].let { belopDto ->
                        belopDto.kodeKlasse shouldBe "KL_KODE_FEIL_INNT"
                        belopDto.belopOpprUtbet shouldBe BigDecimal("0.00")
                        belopDto.belopNy shouldBe BigDecimal("9989.00")
                        belopDto.belopTilbakekreves shouldBe BigDecimal("0.00")
                        belopDto.belopUinnkrevd shouldBe BigDecimal("0.00")
                    }
                    // den andre Dto'en er en ytelse
                    periodeDto.tilbakekrevingsbelop[1].let { belopDto ->
                        belopDto.kodeKlasse shouldBe "SUUFORE"
                        belopDto.belopOpprUtbet shouldBe BigDecimal(9989)
                        belopDto.belopNy shouldBe BigDecimal("0.00")
                        belopDto.belopTilbakekreves shouldBe BigDecimal(9989)
                        belopDto.belopUinnkrevd shouldBe BigDecimal("0.00")
                        belopDto.belopSkatt shouldBe BigDecimal("4395.00")
                        belopDto.kodeResultat shouldBe "FULL_TILBAKEKREV"
                        belopDto.kodeAarsak shouldBe "ANNET"
                        belopDto.kodeSkyld shouldBe "BRUKER"
                    }
                }
            }
        }
    }

    @Test
    fun `mapper ingen tilbakekreving`() {
        val tilbakekrevingsvedtak =
            tilbakekrevingstestData(Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING)

        mapToTilbakekrevingsvedtakRequest(tilbakekrevingsvedtak).let {
            it.tilbakekrevingsvedtak.let { vedtakDto ->
                vedtakDto.kodeAksjon shouldBe "8"
                vedtakDto.vedtakId shouldBe BigInteger("436204")
                vedtakDto.kodeHjemmel shouldBe "SUL_13"
                vedtakDto.renterBeregnes shouldBe "N"
                vedtakDto.enhetAnsvarlig shouldBe "8020"
                vedtakDto.kontrollfelt shouldBe "2022-02-07-18.39.46.586953"
                vedtakDto.saksbehId shouldBe "saksbehandler"
                vedtakDto.tilbakekrevingsperiode[0].let { periodeDto ->
                    periodeDto.periode.let {
                        it.fom = datatypeFactory.newXMLGregorianCalendar().apply {
                            year = 2021
                            month = Month.OCTOBER.value
                            day = 1
                        }
                        it.tom = datatypeFactory.newXMLGregorianCalendar().apply {
                            year = 2021
                            month = Month.OCTOBER.value
                            day = 31
                        }
                    }
                    periodeDto.renterBeregnes shouldBe "N"
                    periodeDto.belopRenter shouldBe BigDecimal("0")
                    periodeDto.tilbakekrevingsbelop[0].let { belopDto ->
                        belopDto.kodeKlasse shouldBe "KL_KODE_FEIL_INNT"
                        belopDto.belopOpprUtbet shouldBe BigDecimal("0.00")
                        belopDto.belopNy shouldBe BigDecimal("9989.00")
                        belopDto.belopTilbakekreves shouldBe BigDecimal("0.00")
                        belopDto.belopUinnkrevd shouldBe BigDecimal("0.00")
                    }
                    periodeDto.tilbakekrevingsbelop[1].let { belopDto ->
                        belopDto.kodeKlasse shouldBe "SUUFORE"
                        belopDto.belopOpprUtbet shouldBe BigDecimal(9989)
                        belopDto.belopNy shouldBe BigDecimal("0.00")
                        belopDto.belopTilbakekreves shouldBe BigDecimal("0.00")
                        belopDto.belopUinnkrevd shouldBe BigDecimal(9989)
                        belopDto.belopSkatt shouldBe BigDecimal("4395.00")
                        belopDto.kodeResultat shouldBe "INGEN_TILBAKEKREV"
                        belopDto.kodeAarsak shouldBe "ANNET"
                        belopDto.kodeSkyld shouldBe "IKKE_FORDELT"
                    }
                }
            }
        }
    }
}

private fun tilbakekrevingstestData(type: Tilbakekrevingsvedtak.Tilbakekrevingsresultat): Tilbakekrevingsvedtak =
    when (type) {
        Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> Tilbakekrevingsvedtak.FullTilbakekreving(
            vedtakId = "436204",
            ansvarligEnhet = "8020",
            kontrollFelt = "2022-02-07-18.39.46.586953",
            behandler = saksbehandler.toString(),
            tilbakekrevingsperioder = tilbakekrevingsperiodetestData(type),
        )

        Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> Tilbakekrevingsvedtak.IngenTilbakekreving(
            vedtakId = "436204",
            ansvarligEnhet = "8020",
            kontrollFelt = "2022-02-07-18.39.46.586953",
            behandler = saksbehandler.toString(),
            tilbakekrevingsperioder = tilbakekrevingsperiodetestData(type),
        )
    }

private fun tilbakekrevingsperiodetestData(type: Tilbakekrevingsvedtak.Tilbakekrevingsresultat): List<Tilbakekrevingsvedtak.Tilbakekrevingsperiode> =
    listOf(
        Tilbakekrevingsvedtak.Tilbakekrevingsperiode(
            periode = oktober(2021),
            renterBeregnes = false,
            beløpRenter = BigDecimal.ZERO,
            ytelse = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpYtelse(
                beløpTidligereUtbetaling = BigDecimal(9989),
                beløpNyUtbetaling = BigDecimal("0.00"),
                beløpSomSkalTilbakekreves = when (type) {
                    Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> BigDecimal(9989)
                    Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> BigDecimal("0.00")
                },
                beløpSomIkkeTilbakekreves = when (type) {
                    Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> BigDecimal("0.00")
                    Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> BigDecimal(9989)
                },
                beløpSkatt = BigDecimal("4395.00"),
                tilbakekrevingsresultat = type,
                skyld = when (type) {
                    Tilbakekrevingsvedtak.Tilbakekrevingsresultat.FULL_TILBAKEKREVING -> Tilbakekrevingsvedtak.Skyld.BRUKER
                    Tilbakekrevingsvedtak.Tilbakekrevingsresultat.INGEN_TILBAKEKREVING -> Tilbakekrevingsvedtak.Skyld.IKKE_FORDELT
                },
            ),
            feilutbetaling = Tilbakekrevingsvedtak.Tilbakekrevingsperiode.Tilbakekrevingsbeløp.TilbakekrevingsbeløpFeilutbetaling(
                beløpTidligereUtbetaling = BigDecimal("0.00"),
                beløpNyUtbetaling = BigDecimal("9989.00"),
                beløpSomSkalTilbakekreves = BigDecimal("0.00"),
                beløpSomIkkeTilbakekreves = BigDecimal("0.00"),
            ),
        ),
    )
