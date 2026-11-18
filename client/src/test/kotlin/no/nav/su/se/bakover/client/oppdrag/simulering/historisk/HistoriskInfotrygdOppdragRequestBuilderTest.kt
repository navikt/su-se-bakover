package no.nav.su.se.bakover.client.oppdrag.simulering.historisk

import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.simulering.buildXmlRequestBody
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest.Oppdragslinje
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.historisk.revurdering.HistoriskInfotrygdRevurdertMånedsresultat
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskInfotrygdOppdragsgrunnlag
import no.nav.su.se.bakover.test.historisk.HistoriskInfotrygdOppdragTestdata
import no.nav.su.se.bakover.test.xml.shouldBeSimilarXmlTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import økonomi.domain.avstemming.Avstemmingsnøkkel
import java.math.BigDecimal

internal class HistoriskInfotrygdOppdragRequestBuilderTest {
    private val data = HistoriskInfotrygdOppdragTestdata
    private val saksnummer = Saksnummer(2021L)
    private val fnr = Fnr("12345678911")
    private val utbetalingId = UUID30.randomUUID()
    private val attestant = NavIdentBruker.Attestant("A123456")

    @ParameterizedTest
    @CsvSource(
        "NY, NY, NY, NY",
        "ENDRING, ENDR, ENDRING, ENDR",
    )
    fun `utforskende XML har OPPH og ENDR erstatningslinje med fullt nytt beløp`(
        oppdragskode: UtbetalingRequest.KodeEndring,
        forventetOppdragskode: String,
        opphørskode: Oppdragslinje.KodeEndringLinje,
        forventetOpphørskode: String,
    ) {
        val måned = data.januar
        val gjeldende = data.gjeldende(periode = måned)
        val grunnlag = HistoriskInfotrygdOppdragsgrunnlag.bygg(data.revurdering(gjeldende), gjeldende).shouldBeRight()
        val opphørId = UUID30.randomUUID()
        val erstatningId = UUID30.randomUUID()
        val format = UverifisertHistoriskOppdragsformat(
            kodeEndring = oppdragskode,
            opphørslinjer = mapOf(
                måned to UverifisertInfotrygdOpphørslinje(opphørId, opphørskode, måned, BigDecimal.ZERO),
            ),
            erstatningslinjer = mapOf(måned to HistoriskErstatningslinjevalg(erstatningId, null)),
        )
        val request = bygg(grunnlag, format).shouldBeRight()

        request.kodeAksjon shouldBe UtbetalingRequest.KodeAksjon.SIMULERING
        request.oppdragslinjer.map { it.attestant } shouldBe List(2) {
            listOf(Oppdragslinje.Attestant(attestant.navIdent))
        }
        val xml = buildXmlRequestBody(simuleringsperiode = måned, request = request)
        xml.shouldBeSimilarXmlTo(
            """
            <ns2:simulerBeregningRequest
                xmlns:ns2="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
                xmlns:ns3="http://nav.no/system/os/entiteter/oppdragSkjema">
                <request>
                    <oppdrag>
                        <kodeEndring>$forventetOppdragskode</kodeEndring>
                        <kodeFagomraade>SUALDER</kodeFagomraade>
                        <fagsystemId>2021</fagsystemId>
                        <utbetFrekvens>MND</utbetFrekvens>
                        <oppdragGjelderId>12345678911</oppdragGjelderId>
                        <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
                        <saksbehId>SU</saksbehId>
                        <ns3:enhet>
                            <typeEnhet>BOS</typeEnhet>
                            <enhet>8020</enhet>
                            <datoEnhetFom>1970-01-01</datoEnhetFom>
                        </ns3:enhet>
                        <oppdragslinje>
                            <kodeEndringLinje>$forventetOpphørskode</kodeEndringLinje>
                            <kodeStatusLinje>OPPH</kodeStatusLinje>
                            <datoStatusFom>2018-01-01</datoStatusFom>
                            <delytelseId>$opphørId</delytelseId>
                            <kodeKlassifik>SUALDER</kodeKlassifik>
                            <datoVedtakFom>2018-01-01</datoVedtakFom>
                            <datoVedtakTom>2018-01-31</datoVedtakTom>
                            <sats>0</sats>
                            <fradragTillegg>T</fradragTillegg>
                            <typeSats>MND</typeSats>
                            <brukKjoreplan>N</brukKjoreplan>
                            <saksbehId>SU</saksbehId>
                            <utbetalesTilId>12345678911</utbetalesTilId>
                            <henvisning>$utbetalingId</henvisning>
                            <ns3:attestant><attestantId>SU</attestantId></ns3:attestant>
                        </oppdragslinje>
                        <oppdragslinje>
                            <kodeEndringLinje>ENDR</kodeEndringLinje>
                            <delytelseId>$erstatningId</delytelseId>
                            <kodeKlassifik>SUALDER</kodeKlassifik>
                            <datoVedtakFom>2018-01-01</datoVedtakFom>
                            <datoVedtakTom>2018-01-31</datoVedtakTom>
                            <sats>12000</sats>
                            <fradragTillegg>T</fradragTillegg>
                            <typeSats>MND</typeSats>
                            <brukKjoreplan>N</brukKjoreplan>
                            <saksbehId>SU</saksbehId>
                            <utbetalesTilId>12345678911</utbetalesTilId>
                            <henvisning>$utbetalingId</henvisning>
                            <ns3:attestant><attestantId>SU</attestantId></ns3:attestant>
                        </oppdragslinje>
                    </oppdrag>
                    <simuleringsPeriode>
                        <datoSimulerFom>2018-01-01</datoSimulerFom>
                        <datoSimulerTom>2018-01-31</datoSimulerTom>
                    </simuleringsPeriode>
                </request>
            </ns2:simulerBeregningRequest>
            """.trimIndent(),
            strict = true,
        )
    }

    @Test
    fun `rent opphør bygger bare opphørslinjer for valgte måneder med felles henvisning`() {
        val grunnlag = grunnlag(opphør = true)
        val format = format(grunnlag)

        val request = bygg(grunnlag, format).shouldBeRight()

        request.oppdragslinjer.map { it.kodeStatusLinje } shouldBe
            List(data.periode.måneder().size) { Oppdragslinje.KodeStatusLinje.OPPHØR }
        request.oppdragslinjer.map { it.datoStatusFom } shouldBe listOf("2018-01-01", "2018-02-01")
        request.oppdragslinjer.map { it.datoVedtakTom } shouldBe listOf("2018-01-31", "2018-02-28")
        request.utbetalingsId() shouldBe utbetalingId
        bygg(grunnlag, format).shouldBeRight() shouldBe request
    }

    @Test
    fun `eksisterende SU-oppdrag er eksplisitt utenfor utkastet`() {
        val grunnlag = grunnlag()

        bygg(grunnlag, format(grunnlag), harEksisterendeSuOppdrag = true).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragRequest.EksisterendeSuOppdragIkkeStøttet
    }

    @Test
    fun `manglende opphørsvalg gir feil i stedet for delvis oppdrag`() {
        val grunnlag = grunnlag()
        val format = format(grunnlag).let {
            it.copy(opphørslinjer = it.opphørslinjer - data.februar)
        }

        bygg(grunnlag, format).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragRequest.OpphørslinjevalgDekkerIkkePerioden
    }

    @Test
    fun `rent opphør avviser erstatningslinjer`() {
        val grunnlag = grunnlag(opphør = true)
        val format = format(grunnlag).copy(
            erstatningslinjer = mapOf(data.januar to HistoriskErstatningslinjevalg(UUID30.randomUUID(), null)),
        )

        bygg(grunnlag, format).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragRequest.ErstatningslinjevalgStemmerIkkeMedBeregningen
    }

    @Test
    fun `opphør og erstatningslinje kan ikke dele linje-ID`() {
        val grunnlag = grunnlag()
        val opprinnelig = format(grunnlag)
        val erstatningslinje = HistoriskErstatningslinjevalg(opprinnelig.opphørslinjer.getValue(data.januar).id, null)
        val format = opprinnelig.copy(
            erstatningslinjer = opprinnelig.erstatningslinjer + (data.januar to erstatningslinje),
        )

        bygg(grunnlag, format).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragRequest.DupliserteLinjeIder
    }

    @Test
    fun `erstatningslinje kan ikke referere til en ukjent tidligere linje`() {
        val grunnlag = grunnlag()
        val opprinnelig = format(grunnlag)
        val erstatningslinje = opprinnelig.erstatningslinjer.getValue(data.januar).copy(
            refDelytelseId = UUID30.randomUUID(),
        )
        val format = opprinnelig.copy(
            erstatningslinjer = opprinnelig.erstatningslinjer + (data.januar to erstatningslinje),
        )

        bygg(grunnlag, format).shouldBeLeft() shouldBe
            KunneIkkeByggeHistoriskInfotrygdOppdragRequest.UgyldigLinjereferanse(data.januar)
    }

    private fun grunnlag(opphør: Boolean = false): HistoriskInfotrygdOppdragsgrunnlag {
        val gjeldende = data.gjeldende()
        return HistoriskInfotrygdOppdragsgrunnlag.bygg(
            data.revurdering(gjeldende, data.beregning(opphør = opphør)),
            gjeldende,
        ).shouldBeRight()
    }

    private fun format(grunnlag: HistoriskInfotrygdOppdragsgrunnlag) = UverifisertHistoriskOppdragsformat(
        kodeEndring = UtbetalingRequest.KodeEndring.NY,
        opphørslinjer = grunnlag.periode.måneder().associateWith { måned ->
            UverifisertInfotrygdOpphørslinje(
                id = UUID30.randomUUID(),
                kodeEndringLinje = Oppdragslinje.KodeEndringLinje.NY,
                vedtaksperiode = måned,
                sats = BigDecimal.ZERO,
            )
        },
        erstatningslinjer = grunnlag.månedsresultater.filterValues {
            it is HistoriskInfotrygdRevurdertMånedsresultat.Ytelse
        }.mapValues { HistoriskErstatningslinjevalg(UUID30.randomUUID(), null) },
    )

    private fun bygg(
        grunnlag: HistoriskInfotrygdOppdragsgrunnlag,
        format: UverifisertHistoriskOppdragsformat,
        harEksisterendeSuOppdrag: Boolean = false,
    ) = byggHistoriskInfotrygdOppdragRequest(
        grunnlag = grunnlag,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingId = utbetalingId,
        attestant = attestant,
        avstemmingsnøkkel = Avstemmingsnøkkel(data.tidspunkt),
        kjøreplan = Oppdragslinje.Kjøreplan.NEI,
        harEksisterendeSuOppdrag = harEksisterendeSuOppdrag,
        format = format,
    )
}
