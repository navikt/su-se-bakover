package no.nav.su.se.bakover.web.services.utbetaling.kvittering

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse.Companion.toKvitteringResponse
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class KvitteringResponseTest {

    @Test
    fun `toKvittering`() {

        //language=XML
        val xml = """
<?xml version="1.0" encoding="UTF-8"?>
<oppdrag xmlns="http://www.trygdeetaten.no/skjema/oppdrag">
   <mmel>
      <systemId>231-OPPD</systemId>
      <alvorlighetsgrad>00</alvorlighetsgrad>
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
         <kodeKomponent>SUUFORE</kodeKomponent>
         <nokkelAvstemming>2a08e16a-7569-47cd-b600-039158</nokkelAvstemming>
         <tidspktMelding>2020-09-02-15.57.08.298000</tidspktMelding>
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
      </oppdrags-linje-150>
   </oppdrag-110>
</Oppdrag>
        """.trimIndent()

        xml.toKvitteringResponse(XmlMapper())
        //TODO: Testen feiler pgrCannot construct instance of `no.nav.su.se.bakover.web.services.utbetaling.kvittering.KvitteringResponse` (no Creators, like default constructor, exist)
        // Finn ut hvordan XML deserialisering kan støtte kotlin data classes med val (slik som json-versjonen)
    }
}
