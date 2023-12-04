package tilbakekreving.infrastructure.client

import arrow.core.Nel
import no.nav.okonomi.tilbakekrevingservice.TilbakekrevingsvedtakRequest
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.tilbakekreving.tilbakekrevingsvedtak.vedtak.v1.TilbakekrevingsbelopDto
import no.nav.tilbakekreving.tilbakekrevingsvedtak.vedtak.v1.TilbakekrevingsperiodeDto
import no.nav.tilbakekreving.tilbakekrevingsvedtak.vedtak.v1.TilbakekrevingsvedtakDto
import no.nav.tilbakekreving.typer.v1.PeriodeDto
import tilbakekreving.domain.vurdert.PeriodevurderingMedKrav
import tilbakekreving.domain.vurdert.VurderingerMedKrav
import java.math.BigDecimal
import java.math.BigInteger
import javax.xml.datatype.DatatypeFactory
import kotlin.math.max

internal enum class AksjonsKode(val nummer: String) {
    FATT_VEDTAK("8"),
}

/**
 * Dersom man bruker hjemmel 'ANNET' sender tilbakekrevingskomponenten posisjon 118 som blank til NAVI/Predator og den vil bli behandlet som foreldet.
 * Dersom man bruker hjemmel 'SUL_13' vil tilbakekrevingskomponenten sende T på posisjon 118 istedet og vi vil få forventet oppførsel.
 * Vi har også bestilt SUL_13-1, SUL_13-2, SUL_13-3 og SUL_13-4 som vi ikke har tatt i bruk enda.
 */
internal enum class TilbakekrevingsHjemmel(val value: String) {
    T("SUL_13"),
    ;

    override fun toString() = value
}

internal enum class Tilbakekrevingsresultat {
    FULL_TILBAKEKREV,
    INGEN_TILBAKEKREV,
}

internal enum class TilbakekrevingsÅrsak {
    ANNET,
}

enum class Skyld {
    BRUKER,
    IKKE_FORDELT,
}

// Se: https://confluence.adeo.no/display/OKSY/Detaljer+om+de+enkelte+ID-koder
// Kan låne litt herfra: https://github.com/navikt/permittering-refusjon-tilbakekreving/blob/4fdaddaf255d5753ac00fee56c5a9918065fdc8f/src/main/kotlin/no/nav/permittering/refusjon/tilbakekreving/behandling/vedtak/TilbakekrevingVedtakHurtigspor.kt
fun mapToTilbakekrevingsvedtakRequest(
    vurderingerMedKrav: VurderingerMedKrav,
    attestertAv: NavIdentBruker.Attestant,
): TilbakekrevingsvedtakRequest {
    return TilbakekrevingsvedtakRequest().apply {
        this.tilbakekrevingsvedtak = TilbakekrevingsvedtakDto().apply {
            // 1 - 441 - Kode-aksjon - X(01) - Krav - Aksjonskode:
            // 7 - midlertidig lagring
            // 8 - fatte vedtak
            // Aksjonskode 7 vil ikke kunne benyttes i fase 1.
            this.kodeAksjon = AksjonsKode.FATT_VEDTAK.nummer

            // 2 - 441 - Vedtak-id - 9(10) - Krav - Identifikasjon av tilbakekrevingsvedtaket
            this.vedtakId = BigInteger(vurderingerMedKrav.eksternVedtakId)

            // 3 - 441 - Dato-vedtak-fagsystem - X(08) - Valgfritt - Vedtaksdato på fagsystems vedtak. Omfeltet ikke er utfylt, legger TIlbakekrevingskomponenten inn dagens dato
            this.datoVedtakFagsystem = null

            // 4 - 441 - Kode-hjemmel - X(20) - Krav - Lovhjemmel om tilbakekrevingsvedtaket
            // TODO jah (alder): Denne bør mappes fra domenet. Gjenbruk de SU Alder bruker i dag.
            this.kodeHjemmel = TilbakekrevingsHjemmel.T.toString()

            // 5 - 441 - Renter-beregnes - X(01) - Betinget krav - 'J' Dersom det skal beregnes renter på kravet
            this.renterBeregnes = "N"

            // 6 - 441 - Enhet-ansvarlig - X(13) - Krav - Ansvarlig enhet
            this.enhetAnsvarlig = "8020"

            // 7 - 441 - Kontrollfelt - X(26) - Krav - Brukes ved innsending av tilbakekrevingsvedtak for å kontrollere at kravgrunnlaget ikke er blitt endret i mellomtiden
            this.kontrollfelt = vurderingerMedKrav.eksternKontrollfelt

            // 8 - 441 - Saksbeh-id - X(08) - Krav - Saksbehandler
            this.saksbehId = attestertAv.toString()

            // Liste over 442 - Tilbakekrevingsperiode
            this.tilbakekrevingsperiode.addAll(mapTilbakekrevingsperioder(vurderingerMedKrav.perioder))
        }
    }
}

val datatypeFactory: DatatypeFactory = DatatypeFactory.newInstance()

private fun mapTilbakekrevingsperioder(
    perioder: Nel<PeriodevurderingMedKrav>,
): List<TilbakekrevingsperiodeDto> {
    return perioder.map {
        TilbakekrevingsperiodeDto().apply {
            periode = PeriodeDto().apply {
                // Disse mappes om til en xsd:date. Vi bruker verdiene som kommer fra kravgrunnlaget og skal ikke trenge ta høyde for tidssone når vi serialiserer datoen, så lenge vi ikke tar høyde for tidsone når vi deserialiserer datoen.
                // TODO: jah test edge-caser med en utc vs cet-klokke
                // 1 - Dato-periode-fom - 442 - X(08) - Krav - Tilbakekrevingsperioder delt opp slik at ingen spenner over månedsskifter
                fom = datatypeFactory.newXMLGregorianCalendar(it.periode.fraOgMed.toString())

                // 2 - Dato-periode-tom - 442 - X(08) - Krav - Tilbakekrevingsperioder delt opp slik at ingen spenner over månedsskifter
                tom = datatypeFactory.newXMLGregorianCalendar(it.periode.tilOgMed.toString())
            }
            // 3 - 442 - Renter-beregnes - X(01) - Valgfritt - 'J' dersom det skal beregnes retner på kravet (nytt felt)
            renterBeregnes = "N"

            // 4 - 442 - Belop-renter - Evt. beregnede renter i fagrutinen (nytt felt)
            belopRenter = BigDecimal.ZERO

            // Liste over 443 - Tilbakekrevingsbeløp
            tilbakekrevingsbelop.addAll(
                listOf(
                    mapYtelse(it),
                    // TODO jah: Er det behov for å sende denne delen?
                    mapFeilutbetaling(it),
                ),
            )
        }
    }
}

private fun mapYtelse(
    periode: PeriodevurderingMedKrav,
): TilbakekrevingsbelopDto {
    return TilbakekrevingsbelopDto().apply {
        // 1 - 443 - Kode-klasse - X(20) - Krav - Klassifisering av stønad, skatt, trekk etc. Det må minimum sendes med klassekoder for feilutbetaling og de ytelsesklassekoder som er feilutbetalt.
        this.kodeKlasse = "SUUFORE"

        // 3 - 443 - Belop-oppr-utbet - 9(8)V99 - Krav - Opprinnelig beregnet beløp, dvs. utbetalingen som førte til feilutbetaling. Dersom saksbehandler deler opp i perioder annerledes enn det som er levert på kravgrunnlaget, må beløp-oppr og beløp-ny beregnes på de nye perioder, med beløp fordelt pr. virkedag.
        this.belopOpprUtbet = BigDecimal(periode.bruttoTidligereUtbetalt)

        // 5 - 443 - Belop-ny - 9(8)V99 - Krav -  Beløpet som ble beregnet ved korrigeringen, evt. fordelt etter ny periodisering.
        this.belopNy = BigDecimal(periode.bruttoNyUtbetaling)

        // 7 - 443 - Belop-tilbakekreves - 9(8)V99 - Krav - Beløp som skal tilbakekreves for angitt periode. Ved ingen tilbakekreving skal beløpet settes til 0.
        this.belopTilbakekreves = BigDecimal(periode.bruttoSkalTilbakekreve)

        // 9 - 443 - Belop-uinnkrevd - 9(8)V99 - Valgfritt - Beløp som ikke skal tilbakekreves. Ved full tilbakekreving skal beløpet settes til 0. Om feltet ikke er utfylt vil Tilbakekrevingskomponenten sette inn beløp etter en forholdsmessig fordeling.
        this.belopUinnkrevd = BigDecimal(periode.bruttoSkalIkkeTilbakekreve)

        // 11 - 443 - Belop-skatt - 9(8)V99 - Valgfritt - Skattebeløp, som skal redusere beløp til innkreving.
        this.belopSkatt = BigDecimal(periode.skattSomGårTilReduksjon)

        this.kodeResultat = when (periode) {
            is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> Tilbakekrevingsresultat.INGEN_TILBAKEKREV.toString()
            is PeriodevurderingMedKrav.SkalTilbakekreve -> Tilbakekrevingsresultat.FULL_TILBAKEKREV.toString()
        }
        this.kodeAarsak = TilbakekrevingsÅrsak.ANNET.toString()
        this.kodeSkyld = when (periode) {
            is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> Skyld.IKKE_FORDELT.toString()
            is PeriodevurderingMedKrav.SkalTilbakekreve -> Skyld.BRUKER.toString()
        }
    }
}

private fun mapFeilutbetaling(
    periode: PeriodevurderingMedKrav,
): TilbakekrevingsbelopDto {
    return TilbakekrevingsbelopDto().apply {
        // 1 - 443 - Kode-klasse - X(20) - Krav - Klassifisering av stønad, skatt, trekk etc. Det må minimum sendes med klassekoder for feilutbetaling og de ytelsesklassekoder som er feilutbetalt.
        this.kodeKlasse = "KL_KODE_FEIL_INNT"

        // 3 - 443 - Belop-oppr-utbet - 9(8)V99 - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil alltid være 0. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
        this.belopOpprUtbet = BigDecimal.ZERO

        // TODO jah: Vi tar ikke med det feilutbetalte beløpet fra kravgrunnlaget, men det vil være det høyeste av disse. Bør vi legge det til?
        // 5 - 443 - Belop-ny - 9(8)V99 - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil være det feilutbetalte beløpet for perioden. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
        this.belopNy = BigDecimal(max(periode.bruttoSkalTilbakekreve, periode.bruttoSkalIkkeTilbakekreve))

        // 7 - 443 - Belop-tilbakekreves - 9(8)V99 - Krav - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil alltid være 0. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
        this.belopTilbakekreves = BigDecimal.ZERO

        // TODO jah: Verifiser om dette er valgfritt eller krav?
        // 9 - 443 - Belop-uinnkrevd - 9(8)V99 - Valgfritt - Krav - Egen betydning for KL_KODE_FEIL_INNT. Vil alltid være 0. Vi asserter på dette når vi tolker kravgrunnlaget fra oppdrag.
        this.belopUinnkrevd = BigDecimal.ZERO
    }
}
