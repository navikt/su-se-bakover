package no.nav.su.se.bakover.web.services.brev

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PdlFeil
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Avslagsgrunn
import no.nav.su.se.bakover.domain.AvslagsgrunnBeskrivelseFlagg
import no.nav.su.se.bakover.domain.BehandlingDto
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.VedtakInnhold
import no.nav.su.se.bakover.domain.Vilkår
import no.nav.su.se.bakover.domain.Vilkårsvurdering
import no.nav.su.se.bakover.domain.beregning.FradragDto
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BrevService(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling
) {
    private val log = LoggerFactory.getLogger(BrevService::class.java)

    companion object {
        fun lagVedtakInnhold(person: Person, behandlingDto: BehandlingDto): VedtakInnhold {
            val fnr = behandlingDto.søknad.søknadInnhold.personopplysninger.fnr
            val avslagsgrunn = avslagsgrunnForBehandling(behandlingDto)
            val avslagsgrunnBeskrivelse = flaggForAvslagsgrunn(avslagsgrunn)
            val førsteMånedsberegning = behandlingDto.beregning?.månedsberegninger?.firstOrNull()

            return VedtakInnhold(
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
                avslagsgrunn = avslagsgrunn,
                avslagsgrunnBeskrivelse = avslagsgrunnBeskrivelse,
                fradato = behandlingDto.beregning?.fom?.formatMonthYear(),
                tildato = behandlingDto.beregning?.tom?.formatMonthYear(),
                sats = behandlingDto.beregning?.sats.toString().toLowerCase(),
                satsbeløp = førsteMånedsberegning?.satsBeløp,
                satsGrunn = "HVOR SKAL DENNE GRUNNEN HENTES FRA",
                redusertStønadStatus = behandlingDto.beregning?.fradrag?.isNotEmpty() ?: false,
                redusertStønadGrunn = "HVOR HENTES DENNE GRUNNEN FRA",
                månedsbeløp = førsteMånedsberegning?.beløp,
                fradrag = behandlingDto.beregning?.fradrag?.toFradragPerMåned() ?: emptyList(),
                fradragSum = behandlingDto.beregning?.fradrag?.toFradragPerMåned()
                    ?.sumBy { fradrag -> fradrag.beløp } ?: 0,
                halvGrunnbeløp = Grunnbeløp.`0,5G`.fraDato(LocalDate.now()).toInt()
            )
        }
    }

    fun opprettJournalpostOgSendBrev(sak: Sak, behandlingDto: BehandlingDto): Either<ClientError, String> {
        val person = personOppslag.person(sak.fnr).getOrElse { throw RuntimeException("Finner ikke person") }
        val pdf = lagUtkastTilBrev(behandlingDto).getOrElse {
            throw RuntimeException("Kunne ikke lage utkast av brev")
        }

        return dokArkiv.opprettJournalpost(
            dokumentInnhold = lagVedtakInnhold(person = person, behandlingDto = behandlingDto),
            person = person,
            pdf = pdf,
            sakId = sak.id.toString()
        ).fold(
            ifLeft = {
                log.error("Kunne ikke opprette journalpost for attestering")
                throw RuntimeException("Kunne ikke opprette journalpost for attestering")
            },
            ifRight = { journalPostId -> sendBrev(journalPostId) }
        )
    }

    fun lagUtkastTilBrev(behandlingDto: BehandlingDto): Either<ClientError, ByteArray> {
        val fnr = behandlingDto.søknad.søknadInnhold.personopplysninger.fnr
        return personOppslag.person(fnr)
            .mapLeft {
                log.warn("Fant ikke person for søknad $fnr")
                ClientError(httpCodeFor(it), it.message)
            }.flatMap { person ->
                val vedtakInnhold = lagVedtakInnhold(person, behandlingDto)
                pdfGenerator.genererPdf(vedtakInnhold)
            }
    }

    fun sendBrev(journalPostId: String): Either<ClientError, String> {
        return dokDistFordeling.bestillDistribusjon(journalPostId)
    }
}

private fun httpCodeFor(pdlFeil: PdlFeil) = when (pdlFeil) {
    is PdlFeil.FantIkkePerson -> 404
    else -> 500
}

fun flaggForAvslagsgrunn(avslagsgrunn: Avslagsgrunn?): AvslagsgrunnBeskrivelseFlagg? =
    when (avslagsgrunn) {
        Avslagsgrunn.UFØRHET -> AvslagsgrunnBeskrivelseFlagg.UFØRHET_FLYKTNING
        Avslagsgrunn.FLYKTNING -> AvslagsgrunnBeskrivelseFlagg.UFØRHET_FLYKTNING
        Avslagsgrunn.FORMUE -> AvslagsgrunnBeskrivelseFlagg.FORMUE
        Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> AvslagsgrunnBeskrivelseFlagg.UTLAND_OG_OPPHOLD_I_NORGE
        else -> null
    }

fun avslagsgrunnForBehandling(behandlingDto: BehandlingDto): Avslagsgrunn? {
    val vilkårIkkeOK = behandlingDto.vilkårsvurderinger.find { it.status == Vilkårsvurdering.Status.IKKE_OK }

    if (vilkårIkkeOK != null) {
        return vilkårToAvslagsgrunn(vilkårIkkeOK.vilkår)
    }
    return null
}

fun vilkårToAvslagsgrunn(vilkår: Vilkår) =
    when (vilkår) {
        Vilkår.UFØRHET -> Avslagsgrunn.UFØRHET
        Vilkår.FLYKTNING -> Avslagsgrunn.FLYKTNING
        Vilkår.FORMUE -> Avslagsgrunn.FORMUE
        Vilkår.BOR_OG_OPPHOLDER_SEG_I_NORGE -> Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
        Vilkår.OPPHOLDSTILLATELSE -> Avslagsgrunn.OPPHOLDSTILLATELSE
        Vilkår.PERSONLIG_OPPMØTE -> Avslagsgrunn.PERSONLIG_OPPMØTE
    }

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear() = this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
fun List<FradragDto>.toFradragPerMåned(): List<FradragDto> =
    this.map { it -> FradragDto(it.id, it.type, it.beløp / 12, it.beskrivelse) }
