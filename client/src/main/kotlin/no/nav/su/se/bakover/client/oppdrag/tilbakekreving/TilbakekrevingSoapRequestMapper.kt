package no.nav.su.se.bakover.client.oppdrag.tilbakekreving

import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakRequest
import no.nav.su.se.bakover.client.oppdrag.OppdragDefaults
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Kravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsvedtak
import no.nav.tilbakekreving.tilbakekrevingsvedtak.vedtak.v1.TilbakekrevingsbelopDto
import no.nav.tilbakekreving.tilbakekrevingsvedtak.vedtak.v1.TilbakekrevingsperiodeDto
import no.nav.tilbakekreving.tilbakekrevingsvedtak.vedtak.v1.TilbakekrevingsvedtakDto
import no.nav.tilbakekreving.typer.v1.PeriodeDto
import java.math.BigDecimal
import java.math.BigInteger
import javax.xml.datatype.DatatypeFactory

// Se: https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder
// Kan låne litt herfra: https://github.com/navikt/permittering-refusjon-tilbakekreving/blob/4fdaddaf255d5753ac00fee56c5a9918065fdc8f/src/main/kotlin/no/nav/permittering/refusjon/tilbakekreving/behandling/vedtak/TilbakekrevingVedtakHurtigspor.kt
fun mapToTilbakekrevingsvedtakRequest(tilbakekrevingsvedtak: Tilbakekrevingsvedtak): TilbakekrevingsvedtakRequest {
    return TilbakekrevingsvedtakRequest().apply {
        this.tilbakekrevingsvedtak = TilbakekrevingsvedtakDto().apply {

            // 1 - 441 - Kode-aksjon - X(01) - Krav - Aksjonskode:
            // 7 - midlertidig lagring
            // 8 - fatte vedtak
            // Aksjonskode 7 vil ikke kunne benyttes i fase 1.
            this.kodeAksjon = "8"

            // 2 - 441 - Vedtak-id - 9(10) - Krav - Identifikasjon av tilbakekrevingsvedtaket
            this.vedtakId = BigInteger(tilbakekrevingsvedtak.kravgrunnlag.vedtakId)

            // 3 - 441 - Dato-vedtak-fagsystem - X(08) - Valgfritt - Vedtaksdato på fagsystems vedtak. Omfeltet ikke er utfylt, legger TIlbakekrevingskomponenten inn dagens dato
            this.datoVedtakFagsystem = null

            // 4 - 441 - Kode-hjemmel - X(20) - Krav - Lovhjemmel om tilbakekrevingsvedtaket
            // TODO jah: Dette må mappes fra domenet. Gjenbruk de SU Alder bruker i dag.
            this.kodeHjemmel = Hjemmelskoder.TODO.hjemmelskode

            // 5 - 441 - Renter-beregnes - X(01) - Betinget krav - 'J' Dersom det skal beregnes renter på kravet
            // TODO jah: Verifiser med fag/juridisk/økonomi at vi ikke skal beregne med renter
            this.renterBeregnes = "N"

            // 6 - 441 - Enhet-ansvarlig - X(13) - Krav - Ansvarlig enhet
            this.enhetAnsvarlig = OppdragDefaults.oppdragsenhet.enhet

            // 7 - 441 - Kontrollfelt - X(26) - Krav - Brukes ved innsending av tilbakekrevingsvedtak for å kontrollere at kravgrunnlaget ikke er blitt endret i mellomtiden
            this.kontrollfelt = tilbakekrevingsvedtak.kravgrunnlag.kontrollfelt

            // 8 - 441 - Saksbeh-id - X(08) - Krav - Saksbehandler
            this.saksbehId = tilbakekrevingsvedtak.kravgrunnlag.behandler.toString()

            // Liste over 442 - Tilbakekrevingsperiode
            this.tilbakekrevingsperiode.addAll(mapTilbakekrevingsperioder(tilbakekrevingsvedtak.kravgrunnlag.grunnlagsperioder))
        }
    }
}

val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()
private fun mapTilbakekrevingsperioder(perioder: List<Kravgrunnlag.Grunnlagsperiode>): List<TilbakekrevingsperiodeDto> {
    return perioder.map {
        TilbakekrevingsperiodeDto().apply {

            this.periode = PeriodeDto().apply {
                // Disse mappes om til en xsd:date. Vi bruker verdiene som kommer fra kravgrunnlaget og skal ikke trenge ta høyde for tidssone når vi serialiserer datoen, så lenge vi ikke tar høyde for tidsone når vi deserialiserer datoen.
                // TODO: jah test edge-caser med en utc vs cet-klokke
                // 1 - Dato-periode-fom - 442 - X(08) - Krav - Tilbakekrevingsperioder delt opp slik at ingen spenner over månedsskifter
                this.fom = datatypeFactory.newXMLGregorianCalendar(it.periode.fraOgMed.toString())

                // 2 - Dato-periode-tom - 442 - X(08) - Krav - Tilbakekrevingsperioder delt opp slik at ingen spenner over månedsskifter
                this.tom = datatypeFactory.newXMLGregorianCalendar(it.periode.tilOgMed.toString())
            }
            // 3 - 442 - Renter-beregnes - X(01) - Valgfritt - 'J' dersom det skal beregnes retner på kravet (nytt felt)
            this.renterBeregnes = "N"

            // 4 - 442 - Belop-renter - Evt. beregnede renter i fagrutinen (nytt felt)
            this.belopRenter = null

            // Liste over 443 - Tilbakekrevingsbeløp
            this.tilbakekrevingsbelop.addAll(mapTilbakekrevingsbeløp(it))
        }
    }
}

private fun mapTilbakekrevingsbeløp(grunnlagsperiode: Kravgrunnlag.Grunnlagsperiode): List<TilbakekrevingsbelopDto> {
    // TODO jah: utgrei om vi skal filtrere på klassekoder/typer.
    return grunnlagsperiode.grunnlagsbeløp.map {
        TilbakekrevingsbelopDto().apply {

            // 1 - 443 - Kode-klasse - X(20) - Krav - Klassifisering av stønad, skatt, trekk etc. Det må minimum sendes med klassekoder for feilutbetaling og de ytelsesklassekoder som er feilutbetalt.
            this.kodeKlasse = it.kode.toString() // TODO jah:

            // 3 - 443 - Belop-oppr-utbet - 9(8)V99 - Krav - Opprinnelig beregnet beløp, dvs. utbetalingen som førte til feilutbetaling. Dersom saksbehandler deler opp i perioder annerledes enn det som er levert på kravgrunnlaget, må beløp-oppr og beløp-ny beregnes på de nye perioder, med beløp fordelt pr. virkedag.
            this.belopOpprUtbet = BigDecimal.ZERO // TODO jah:

            // 5 - 443 - Belop-ny - 9(8)V99 - Krav -  Beløpet som ble beregnet ved korrigeringen, evt. fordelt etter ny periodisering.
            this.belopNy = BigDecimal.ZERO // TODO jah:

            // 7 - 443 - Belop-tilbakekreves - 9(8)V99 - Krav - Beløp som skal tilbakekreves for angitt periode. Ved ingen tilbakekreving skal beløpet settes til 0.
            this.belopTilbakekreves = BigDecimal.ZERO // TODO jah:

            // 9 - 443 - Belop-uinnkrevd - 9(8)V99 - Valgfritt - Beløp som ikke skal tilbakekreves. Ved full tilbakekreving skal beløpet settes til 0. Om feltet ikke er utfylt vil Tilbakekrevingskomponenten sette inn beløp etter en forholdsmessig fordeling.
            this.belopUinnkrevd = BigDecimal.ZERO // TODO jah:

            // 11 - 443 - Belop-skatt - 9(8)V99 - Valgfritt - Skattebeløp, som skal redusere beløp til innkreving.
            this.belopSkatt = BigDecimal.ZERO // TODO jah:

            // 10 (sic) - 443 - Kode-resultat - X(20) - Krav - Hvilket vedtak som er fattet ang. tilbakekreving:
            // DELVIS_TILBAKEKREV Delvis tilbakekreving
            // FEILREGISTRERT Feilregistrert
            // FORELDET Foreldet
            // FULL_TILBAKEKREV Full tilbakekreving
            // INGEN_TILBAKEKREV Ingen tilbakekreving
            this.kodeResultat = "" // TODO jah: Vi vil kun støtte ingen/full tilbakekreving til å begynne med.

            // 11 (sic) - 443 - Kode-aarsak - X(20) - Krav - Årsak til feilutbetalingen:
            // ANNET Annet
            // ARBHOYINNT Arbeid/Høy inntekt
            // BEREGNFEIL Beregningsfeil
            // DODSFALL Dødsfall
            // EKTESKAP Ekteskap
            // FEILREGEL Feil regelbruk
            // FEILUFOREG Feil uføregrad
            // FLYTTUTLAND Flyttet utland
            // IKKESJEKKYTELSE Ikke sjekket mot andre ytelser
            // OVERSETTMLD Oversett melding
            // SAMLIV Samliv
            // UTBFEILMOT Utbetaling til feil mottaker
            this.kodeAarsak =
                "ANNET" // TODO jah: Bestem om vi skal utlede disse fra vilkår/bosit/avslagsgrunn. Eller om det vil være enklere for oss å be saksbehandler fylle ut. Eller hvis det ikke er viktig for økonomi, bare send ANNET.

            // 12 - 443 - Kode-skyld - X(20) - Valgfritt - Hvem som har skyld i at det ble feilutbetalt:
            // BRUKER Bruker
            // IKKE_FORDELT Ikke fordelt
            // NAV NAV
            // SKYLDDELING Skylddeling mellom bruker og NAV
            this.kodeSkyld = null // TODO
        }
    }
}
