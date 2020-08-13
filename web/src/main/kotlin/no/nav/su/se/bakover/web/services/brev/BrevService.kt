package no.nav.su.se.bakover.web.services.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonFactory
import no.nav.su.se.bakover.domain.BehandlingDto
import no.nav.su.se.bakover.domain.VedtakInnhold
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BrevService(
    private val pdfGenerator: PdfGenerator,
    private val personFactory: PersonFactory
) {
    private val log = LoggerFactory.getLogger(BrevService::class.java)

    fun lagUtkastTilBrev(behandlingDto: BehandlingDto): Either<ClientError, ByteArray> {
        val fnr = behandlingDto.søknad.søknadInnhold.personopplysninger.fnr
        return personFactory.forFnr(fnr).flatMap { person ->
            pdfGenerator.genererPdf(
                VedtakInnhold(
                    dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    fødselsnummer = fnr,
                    fornavn = person.navn.fornavn,
                    etternavn = person.navn.etternavn,
                    adresse = person.adresse?.adressenavn,
                    postnummer = person.adresse?.poststed?.postnummer,
                    poststed = person.adresse?.poststed?.poststed,
                    månedsbeløp = behandlingDto.beregning?.getMånedsbeløp(),
                    fradato = behandlingDto.beregning?.fom?.format(DateTimeFormatter.ofPattern("MM yyyy")), // TODO: Trekk ut datoformatering
                    tildato = behandlingDto.beregning?.tom?.format(DateTimeFormatter.ofPattern("MM yyyy")),
                    // Er det riktig att bruka tom dato her?
                    nysøkdato = behandlingDto.beregning?.tom?.format(DateTimeFormatter.ofPattern("MM yyyy")),
                    sats = behandlingDto.beregning?.sats,
                    satsbeløp = behandlingDto.beregning?.getSatsbeløp(),
                    status = behandlingDto.status
                )
            )
        }
    }

}
