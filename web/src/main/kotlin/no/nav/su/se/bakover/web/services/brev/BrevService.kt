package no.nav.su.se.bakover.web.services.brev

import arrow.core.Either
import arrow.core.flatMap
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.person.PersonFactory
import no.nav.su.se.bakover.domain.Avslagsgrunn
import no.nav.su.se.bakover.domain.AvslagsgrunnBeskrivelseFlagg
import no.nav.su.se.bakover.domain.BehandlingDto
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
    private val personFactory: PersonFactory
) {
    private val log = LoggerFactory.getLogger(BrevService::class.java)

    fun lagUtkastTilBrev(behandlingDto: BehandlingDto): Either<ClientError, ByteArray> {
        val fnr = behandlingDto.søknad.søknadInnhold.personopplysninger.fnr
        val avslagsgrunn = avslagsgrunnForBehandling(behandlingDto)
        val avslagsgrunnBeskrivelse = flaggForAvslagsgrunn(avslagsgrunn)
        return personFactory.forFnr(fnr)
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
                        postnummer = person.adresse?.poststed?.postnummer,
                        poststed = person.adresse?.poststed?.poststed,
                        status = behandlingDto.status,
                        avslagsgrunn = avslagsgrunn,
                        avslagsgrunnBeskrivelse = avslagsgrunnBeskrivelse,
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
fun List<FradragDto>.toFradragPerMåned(): List<FradragDto> = this.map { it -> FradragDto(it.id, it.type, it.beløp / 12, it.beskrivelse) }
