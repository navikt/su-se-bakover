package no.nav.su.se.bakover.web.services.brev

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.BehandlingDto
import no.nav.su.se.bakover.domain.VedtakInnhold
import no.nav.su.se.bakover.domain.beregning.FradragDto
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BrevService(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag
) {
    private val log = LoggerFactory.getLogger(BrevService::class.java)

    fun lagUtkastTilBrev(behandlingDto: BehandlingDto): Either<ClientError, ByteArray> {
        val fnr = behandlingDto.søknad.søknadInnhold.personopplysninger.fnr
        return personOppslag.person(fnr)
            .mapLeft {
                log.warn("Fant ikke person for søknad $fnr")
                it
            }.flatMap { person ->
                // TODO variabelt beløp pr mnd? wtf?
                val førsteMånedsberegning = behandlingDto.beregning?.månedsberegninger?.firstOrNull()
                pdfGenerator.genererPdf(
                    VedtakInnhold(
                        dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                        fødselsnummer = fnr,
                        fornavn = person.navn.fornavn,
                        etternavn = person.navn.etternavn,
                        adresse = person.adresse?.adressenavn,
                        bruksenhet = person.adresse?.bruksenhet,
                        husnummer = person.adresse?.husnummer,
                        postnummer = person.adresse?.poststed?.postnummer,
                        poststed = person.adresse?.poststed?.poststed,
                        status = behandlingDto.status,
                        fradato = behandlingDto.beregning?.fom?.formatMonthYear(),
                        tildato = behandlingDto.beregning?.tom?.formatMonthYear(),
                        sats = behandlingDto.beregning?.sats.toString().toLowerCase(),
                        satsbeløp = førsteMånedsberegning?.satsBeløp,
                        satsGrunn = "HVOR SKAL DENNE GRUNNEN HENTES FRA", // hard code
                        redusertStønadStatus = true,
                        redusertStønadGrunn = "HVOR HENTES DENNE GRUNNEN FRA",
                        månedsbeløp = førsteMånedsberegning?.beløp,
                        fradrag = behandlingDto.beregning?.fradrag?.toFradragPerMåned() ?: emptyList(),
                        fradragSum = behandlingDto.beregning?.fradrag?.toFradragPerMåned()?.sumBy { fradrag -> fradrag.beløp } ?: 0
                    )
                )
            }
    }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear() = this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
fun List<FradragDto>.toFradragPerMåned(): List<FradragDto> = this.map { it -> FradragDto(it.id, it.type, it.beløp / 12, it.beskrivelse) }
