package tilbakekreving.domain.vedtaksbrev

import dokument.domain.Brevtype
import dokument.domain.generer.Brevmal
import dokument.domain.generer.Dokumenttype
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.EnkeltMånedsoversikt
import dokument.domain.generer.bygger.maler.MånedsoversiktMedSum
import dokument.domain.generer.bygger.maler.VedtaksbrevTilbakekrevingsbehandlingPdfInnhold
import dokument.domain.generer.bygger.maler.Vurdering
import no.nav.su.se.bakover.common.domain.tid.toBrevformat
import tilbakekreving.domain.vurdering.PeriodevurderingMedKrav
import tilbakekreving.domain.vurdering.VurderingerMedKrav
import java.time.LocalDate

fun VedtaksbrevTilbakekrevingsbehandlingDokumentCommand.fromBrevCommand(
    personalia: PersonaliaPdfInnhold,
    saksbehandlerNavn: String,
    attestantNavn: String?,
    idag: LocalDate,
): VedtaksbrevTilbakekrevingsbehandlingPdfInnhold {
    return VedtaksbrevTilbakekrevingsbehandlingPdfInnhold(
        sakstype = sakstype,
        personalia = personalia,
        saksbehandlerNavn = saksbehandlerNavn,
        attestantNavn = attestantNavn,
        fritekst = fritekst,
        // Denne formateres annerledes enn i personalia, selvom begge deler er dagens dato. 2021-01-01 vil gi 01.01.2021 i personalia, mens 1. januar 2021 i dette feltet.
        // TODO jah: Kanskje vi kan bruke denne i su-pdfgen? https://github.com/navikt/pdfgen/blob/master/src/main/kotlin/no/nav/pdfgen/template/Helpers.kt
        dato = idag.toBrevformat(),
        månedsoversiktMedSum = vurderingerMedKrav.månedsoversiktMedSum(),
        skalTilbakekreve = skalTilbakekreve,
        dødsbo = dødsbo,
        bruttoSkalIkkeTilbakekreveSummert = vurderingerMedKrav.bruttoSkalIkkeTilbakekreveSummert,
    )
}

fun VurderingerMedKrav.månedsoversiktMedSum(): MånedsoversiktMedSum {
    return MånedsoversiktMedSum(
        sorterteMåneder = this.perioder.map {
            EnkeltMånedsoversikt(
                periode = it.periode.ddMMyyyy(),
                vurdering = when (it) {
                    is PeriodevurderingMedKrav.SkalIkkeTilbakekreve -> Vurdering.SkalIkkeTilbakekreve
                    is PeriodevurderingMedKrav.SkalTilbakekreve -> Vurdering.SkalTilbakekreve
                },
                bruttoSkalTilbakekreve = it.bruttoSkalTilbakekreve,
                nettoSkalTilbakekreve = it.nettoSkalTilbakekreve,
            )
        },
        sumBruttoSkalTilbakekreve = this.bruttoSkalTilbakekreveSummert,
        sumNettoSkalTilbakekreve = this.nettoSkalTilbakekreveSummert,
    )
}

val vedtaksbrevTilbakekrevingBrev = Brevmal<VedtaksbrevTilbakekrevingsbehandlingDokumentCommand>(
    dokumenttype = Dokumenttype.VEDTAK,
    brevtype = Brevtype.VEDTAK,
) { felles, kommando ->
    kommando.fromBrevCommand(
        personalia = felles.personalia,
        saksbehandlerNavn = felles.saksbehandlerNavn,
        attestantNavn = felles.attestantNavn,
        idag = felles.idag,
    )
}
