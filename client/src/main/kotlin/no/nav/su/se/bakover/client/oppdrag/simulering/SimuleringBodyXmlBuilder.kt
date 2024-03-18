@file:Suppress("HttpUrlsUsage")

package no.nav.su.se.bakover.client.oppdrag.simulering

import no.nav.su.se.bakover.client.oppdrag.utbetaling.UtbetalingRequest
import no.nav.su.se.bakover.client.oppdrag.utbetaling.toUtbetalingRequest
import no.nav.su.se.bakover.common.tid.periode.Periode
import økonomi.domain.utbetaling.Utbetaling

internal fun buildXmlRequestBody(
    utbetaling: Utbetaling.UtbetalingForSimulering,
): String {
    return buildXmlRequestBody(
        simuleringsperiode = utbetaling.periode,
        request = toUtbetalingRequest(utbetaling).oppdragRequest,
    )
}

/**
 * https://confluence.adeo.no/display/OKSY/Inputdata+fra+fagrutinen+til+Oppdragssystemet
 *
 * Effekt av å sette datoSimulerFom og datoSimulerTom:
 *
 * Ingen satt:
 *  For nye (ikke utbetalt) perioder:
 *      Kun simulering for første berørte måned returneres.
 *  For eksisterende (utbetalt) perioder:
 *      Simulering for alle berørte måneder returneres (opp til og med siste ikke utbetalte måned)
 *
 * Begge satt:
 *  For nye (ikke utbetalte) perioder:
 *      Simulering for alle måneder i perioden returneres.
 *  For eksisterende (utbetalt) perioder:
 *      Simulering for alle måneder i perioden returneres. OBS: Berørte måneder kan ligge utenfor valgt periode!
 *
 * Kun fra og med satt:
 *  Samme som "Ingen satt", med nedre begrensning. Til og med bestemmes av utbetalingslinje.
 *  OBS: Berørte måneder kan være tidligere enn valgt dato!
 *
 * Kun til og med satt:
 *  Samme som "Begge satt", men øvre begrensning. Fra og med bestemmes av utbetalingslinje.
 *  OBS: Berørte måneder kan være senere enn valgt dato!
 *
 */
internal fun buildXmlRequestBody(
    simuleringsperiode: Periode,
    request: UtbetalingRequest.OppdragRequest,
): String {
    // TODO jah: attestantId settes til saksbehandleren, men bør egentlig loopes og settes til linje.attestant. Men det bør gjøres i en egen PR.
    // language=XML
    return """<ns2:simulerBeregningRequest xmlns:ns2="http://nav.no/system/os/tjenester/simulerFpService/simulerFpServiceGrensesnitt"
                             xmlns:ns3="http://nav.no/system/os/entiteter/oppdragSkjema">
    <request>
        <oppdrag>
            <kodeEndring>${request.kodeEndring.value}</kodeEndring>
            <kodeFagomraade>${request.kodeFagomraade}</kodeFagomraade>
            <fagsystemId>${request.fagsystemId}</fagsystemId>
            <utbetFrekvens>${request.utbetFrekvens.value}</utbetFrekvens>
            <oppdragGjelderId>${request.oppdragGjelderId}</oppdragGjelderId>
            <datoOppdragGjelderFom>${request.datoOppdragGjelderFom}</datoOppdragGjelderFom>
            <saksbehId>${request.saksbehId}</saksbehId>
            ${
        request.oppdragsEnheter.joinToString(separator = "\n") { enhet ->
            """<ns3:enhet>
                <typeEnhet>${enhet.typeEnhet}</typeEnhet>
                <enhet>${enhet.enhet}</enhet>
                <datoEnhetFom>${enhet.datoEnhetFom}</datoEnhetFom>
            </ns3:enhet>"""
        }
    }
            ${
        request.oppdragslinjer.joinToString(separator = "\n") { linje ->
            """<oppdragslinje>
                <kodeEndringLinje>${linje.kodeEndringLinje.value}</kodeEndringLinje>
                 ${linje.kodeStatusLinje?.let { """<kodeStatusLinje>${linje.kodeStatusLinje.value}</kodeStatusLinje>""" } ?: ""}
                 ${linje.datoStatusFom?.let { """<datoStatusFom>${linje.datoStatusFom}</datoStatusFom>""" } ?: ""}
                <delytelseId>${linje.delytelseId}</delytelseId>
                <kodeKlassifik>${linje.kodeKlassifik}</kodeKlassifik>
                <datoVedtakFom>${linje.datoVedtakFom}</datoVedtakFom>
                <datoVedtakTom>${linje.datoVedtakTom}</datoVedtakTom>
                <sats>${linje.sats}</sats>
                <fradragTillegg>${linje.fradragTillegg.value}</fradragTillegg>
                <typeSats>${linje.typeSats.value}</typeSats>
                <brukKjoreplan>${linje.brukKjoreplan.value}</brukKjoreplan>
                <saksbehId>${linje.saksbehId}</saksbehId>
                <utbetalesTilId>${linje.utbetalesTilId}</utbetalesTilId>
                <henvisning>${request.utbetalingsId()}</henvisning>
                ${linje.refFagsystemId?.let { """<refFagsystemId>${linje.refFagsystemId}</refFagsystemId>""" } ?: ""}
                ${linje.refDelytelseId?.let { """<refDelytelseId>${linje.refDelytelseId}</refDelytelseId>""" } ?: ""}
                ${
                linje.grad?.let { grad ->
                    """<ns3:grad>
                    <typeGrad>${grad.typeGrad.value}</typeGrad>
                    <grad>${grad.grad}</grad>
                </ns3:grad>"""
                }
            }
                <ns3:attestant>
                    <attestantId>${linje.saksbehId}</attestantId>
                </ns3:attestant>
                
            </oppdragslinje>"""
        }
    }
        </oppdrag>
        <simuleringsPeriode>
            <datoSimulerFom>${simuleringsperiode.fraOgMed}</datoSimulerFom>
            <datoSimulerTom>${simuleringsperiode.tilOgMed}</datoSimulerTom>
        </simuleringsPeriode>
    </request>
</ns2:simulerBeregningRequest>"""
}
