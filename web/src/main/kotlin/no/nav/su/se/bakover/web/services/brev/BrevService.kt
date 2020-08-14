package no.nav.su.se.bakover.web.services.brev

import arrow.core.Either
import arrow.core.flatMap
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
        return personFactory.forFnr(fnr)
            .mapLeft {
                log.warn("Fant ikke person for søknad $fnr")
                it
            }.flatMap { person ->
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
                        fradato = behandlingDto.beregning?.fom?.formatMonthYear(),
                        tildato = behandlingDto.beregning?.tom?.formatMonthYear(),
                        // Er det riktig att bruka tom dato her?
                        nysøkdato = behandlingDto.beregning?.tom?.formatMonthYear(),
                        sats = behandlingDto.beregning?.sats,
                        satsbeløp = behandlingDto.beregning?.getSatsbeløp(),
                        status = behandlingDto.status
                    )
                )
            }
    }
}

fun LocalDate.formatMonthYear() = this.format(DateTimeFormatter.ofPattern("MM yyyy"))
