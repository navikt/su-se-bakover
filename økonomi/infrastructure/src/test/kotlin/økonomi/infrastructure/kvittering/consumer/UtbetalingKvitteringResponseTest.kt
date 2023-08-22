package økonomi.infrastructure.kvittering.consumer

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.common.UUID30
import org.junit.jupiter.api.Test
import økonomi.infrastructure.kvittering.consumer.UtbetalingKvitteringResponse.Companion.toKvitteringResponse

internal class UtbetalingKvitteringResponseTest {

    @Test
    fun `deserialiserer KvitteringRespons`() {
        kvitteringXml(UUID30.fromString("268e62fb-3079-4e8d-ab32-ff9fb9")).toKvitteringResponse(
            UtbetalingKvitteringConsumer.xmlMapper,
        ) shouldBe UtbetalingKvitteringResponse(
            mmel = UtbetalingKvitteringResponse.Mmel(
                systemId = "231-OPPD",
                kodeMelding = null,
                alvorlighetsgrad = UtbetalingKvitteringResponse.Alvorlighetsgrad.ALVORLIG_FEIL,
                beskrMelding = null,
                sqlKode = null,
                sqlState = null,
                sqlMelding = null,
                mqCompletionKode = null,
                mqReasonKode = null,
                programId = null,
                sectionNavn = null,
            ),
            oppdragRequest = UtbetalingRequest.OppdragRequest(
                kodeAksjon = UtbetalingRequest.KodeAksjon.UTBETALING,
                kodeEndring = UtbetalingRequest.KodeEndring.NY,
                kodeFagomraade = "SUUFORE",
                fagsystemId = "35413bd5-f66a-44d8-b7e9-1006d5",
                utbetFrekvens = UtbetalingRequest.Utbetalingsfrekvens.MND,
                oppdragGjelderId = "18127621833",
                datoOppdragGjelderFom = "1970-01-01",
                saksbehId = "SU",
                avstemming = UtbetalingRequest.Avstemming(
                    kodeKomponent = "SU",
                    nokkelAvstemming = avstemmingsnøkkelIXml,
                    tidspktMelding = avstemmingsnøkkelTidspunktIXml,
                ),
                oppdragsEnheter = listOf(
                    UtbetalingRequest.OppdragsEnhet(
                        typeEnhet = "BOS",
                        enhet = "8020",
                        datoEnhetFom = "1970-01-01",
                    ),
                ),
                oppdragslinjer = listOf(
                    UtbetalingRequest.Oppdragslinje(
                        kodeEndringLinje = UtbetalingRequest.Oppdragslinje.KodeEndringLinje.NY,
                        delytelseId = "4fad33a7-9a7d-4732-9d3f-b9d0fc",
                        kodeKlassifik = "SUUFORE",
                        datoVedtakFom = "2020-01-01",
                        datoVedtakTom = "2020-12-31",
                        sats = "20637",
                        fradragTillegg = UtbetalingRequest.Oppdragslinje.FradragTillegg.TILLEGG,
                        typeSats = UtbetalingRequest.Oppdragslinje.TypeSats.MND,
                        brukKjoreplan = UtbetalingRequest.Oppdragslinje.Kjøreplan.NEI,
                        saksbehId = "SU",
                        utbetalesTilId = "18127621833",
                        refDelytelseId = null,
                        refFagsystemId = null,
                        attestant = listOf(UtbetalingRequest.Oppdragslinje.Attestant("A123456")),
                        datoStatusFom = null,
                        kodeStatusLinje = null,
                        grad = UtbetalingRequest.Oppdragslinje.Grad(
                            typeGrad = UtbetalingRequest.Oppdragslinje.TypeGrad.UFOR,
                            grad = 50,
                        ),
                        utbetalingId = "268e62fb-3079-4e8d-ab32-ff9fb9",
                    ),
                ),
            ),
        )
    }

    companion object {
        const val avstemmingsnøkkelIXml: String = "7282171188123456"
        const val avstemmingsnøkkelTidspunktIXml = "2200-10-06-09.19.48.123456"

        //language=XML
        fun kvitteringXml(
            utbetalingsId: UUID30,
            alvorlighetsgrad: UtbetalingKvitteringResponse.Alvorlighetsgrad = UtbetalingKvitteringResponse.Alvorlighetsgrad.ALVORLIG_FEIL,
        ) =
            """
<?xml version="1.0" encoding="UTF-8"?>
<oppdrag xmlns="http://www.trygdeetaten.no/skjema/oppdrag">
   <mmel>
      <systemId>231-OPPD</systemId>
      <alvorlighetsgrad>$alvorlighetsgrad</alvorlighetsgrad>
   </mmel>
   <oppdrag-110>
      <kodeAksjon>1</kodeAksjon>
      <kodeEndring>NY</kodeEndring>
      <kodeFagomraade>SUUFORE</kodeFagomraade>
      <fagsystemId>35413bd5-f66a-44d8-b7e9-1006d5</fagsystemId>
      <utbetFrekvens>MND</utbetFrekvens>
      <oppdragGjelderId>18127621833</oppdragGjelderId>
      <datoOppdragGjelderFom>1970-01-01</datoOppdragGjelderFom>
      <saksbehId>SU</saksbehId>
      <avstemming-115>
         <kodeKomponent>SU</kodeKomponent>
         <nokkelAvstemming>$avstemmingsnøkkelIXml</nokkelAvstemming>
         <tidspktMelding>$avstemmingsnøkkelTidspunktIXml</tidspktMelding>
      </avstemming-115>
      <oppdrags-enhet-120>
         <typeEnhet>BOS</typeEnhet>
         <enhet>8020</enhet>
         <datoEnhetFom>1970-01-01</datoEnhetFom>
      </oppdrags-enhet-120>
      <oppdrags-linje-150>
         <kodeEndringLinje>NY</kodeEndringLinje>
         <delytelseId>4fad33a7-9a7d-4732-9d3f-b9d0fc</delytelseId>
         <kodeKlassifik>SUUFORE</kodeKlassifik>
         <datoVedtakFom>2020-01-01</datoVedtakFom>
         <datoVedtakTom>2020-12-31</datoVedtakTom>
         <sats>20637</sats>
         <fradragTillegg>T</fradragTillegg>
         <typeSats>MND</typeSats>
         <brukKjoreplan>N</brukKjoreplan>
         <saksbehId>SU</saksbehId>
         <utbetalesTilId>18127621833</utbetalesTilId>
         <grad-170>
            <typeGrad>UFOR</typeGrad>
            <grad>50</grad>
         </grad-170>
         <attestant-180>
            <attestantId>A123456</attestantId>
         </attestant-180>
         <ukjentFeltBørIgnorereres>ukjent</ukjentFeltBørIgnorereres>
         <henvisning>$utbetalingsId</henvisning>
      </oppdrags-linje-150>
   </oppdrag-110>
</Oppdrag>
            """.trimIndent()
    }
}
