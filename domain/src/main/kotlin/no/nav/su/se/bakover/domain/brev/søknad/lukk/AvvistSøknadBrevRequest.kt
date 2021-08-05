package no.nav.su.se.bakover.domain.brev.søknad.lukk

import arrow.core.Either
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.brev.lagPersonalia
import no.nav.su.se.bakover.domain.dokument.Dokument

data class AvvistSøknadBrevRequest(
    override val person: Person,
    private val brevConfig: BrevConfig,
    private val saksbehandlerNavn: String,
) : LagBrevRequest {
    override val brevInnhold = when (brevConfig) {
        is BrevConfig.Vedtak -> AvvistSøknadVedtakBrevInnhold(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = brevConfig.getFritekst(),
        )
        is BrevConfig.Fritekst -> AvvistSøknadFritekstBrevInnhold(
            personalia = lagPersonalia(),
            saksbehandlerNavn = saksbehandlerNavn,
            fritekst = brevConfig.getFritekst(),
        )
    }

    override fun tilDokument(genererPdf: (lagBrevRequest: LagBrevRequest) -> Either<LagBrevRequest.KunneIkkeGenererePdf, ByteArray>): Either<LagBrevRequest.KunneIkkeGenererePdf, Dokument.UtenMetadata> {
        when (brevConfig) {
            is BrevConfig.Fritekst -> {
                return genererDokument(genererPdf).map {
                    Dokument.UtenMetadata.Informasjon(
                        tittel = it.first,
                        generertDokument = it.second,
                        generertDokumentJson = it.third,
                    )
                }
            }
            is BrevConfig.Vedtak -> {
                return genererDokument(genererPdf).map {
                    Dokument.UtenMetadata.Vedtak(
                        tittel = it.first,
                        generertDokument = it.second,
                        generertDokumentJson = it.third,
                    )
                }
            }
        }
    }
}
