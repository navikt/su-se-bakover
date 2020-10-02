package no.nav.su.se.bakover.web.services.brev

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.ClientError
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.Vedtakstype
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Avslagsgrunn
import no.nav.su.se.bakover.domain.AvslagsgrunnBeskrivelse
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.VedtakInnhold
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.FastOppholdINorge
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Flyktning
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.LovligOpphold
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.OppholdIUtlandet
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.PersonligOppmøte
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon.Uførhet
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.service.sak.SakService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class BrevService(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling,
    private val sakService: SakService
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        fun lagVedtakInnhold(person: Person, behandling: Behandling): VedtakInnhold {
            val fnr = behandling.søknad.søknadInnhold.personopplysninger.fnr
            val avslagsgrunn = avslagsgrunnForBehandling(behandling)
            val avslagsgrunnBeskrivelse = beskrivelseForAvslagsgrunn(avslagsgrunn)
            val førsteMånedsberegning = behandling.beregning()?.månedsberegninger?.firstOrNull()

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
                status = behandling.status(),
                avslagsgrunn = avslagsgrunn,
                avslagsgrunnBeskrivelse = avslagsgrunnBeskrivelse,
                fradato = behandling.beregning()?.fraOgMed?.formatMonthYear(),
                tildato = behandling.beregning()?.tilOgMed?.formatMonthYear(),
                sats = behandling.beregning()?.sats.toString().toLowerCase(),
                satsbeløp = førsteMånedsberegning?.satsBeløp,
                satsGrunn = "HVOR SKAL DENNE GRUNNEN HENTES FRA",
                redusertStønadStatus = behandling.beregning()?.fradrag?.isNotEmpty() ?: false,
                redusertStønadGrunn = "HVOR HENTES DENNE GRUNNEN FRA",
                månedsbeløp = førsteMånedsberegning?.beløp,
                fradrag = behandling.beregning()?.fradrag?.toFradragPerMåned() ?: emptyList(),
                fradragSum = behandling.beregning()?.fradrag?.toFradragPerMåned()
                    ?.sumBy { fradrag -> fradrag.beløp } ?: 0,
                halvGrunnbeløp = Grunnbeløp.`0,5G`.fraDato(LocalDate.now()).toInt()
            )
        }
    }

    fun journalførVedtakOgSendBrev(
        sak: Sak,
        behandling: Behandling
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        val loggtema = "Journalføring og sending av vedtaksbrev"

        val person = hentPersonFraFnr(sak.fnr).fold({ return KunneIkkeOppretteJournalpostOgSendeBrev.left() }, { it })
        val brevInnhold =
            lagBrevPdf(behandling, person).fold({ return KunneIkkeOppretteJournalpostOgSendeBrev.left() }, { it })

        val journalPostId = dokArkiv.opprettJournalpost(
            Journalpost.Vedtakspost(
                person = person,
                sakId = sak.id.toString(),
                vedtakInnhold = lagVedtakInnhold(person, behandling),
                pdf = brevInnhold
            )
        ).fold(
            {
                log.error("$loggtema: Kunne ikke journalføre i ekstern system (joark/dokarkiv)")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            {
                log.error("$loggtema: Journalført i ekstern system (joark/dokarkiv) OK")
                it
            }
        )

        return sendBrev(journalPostId)
            .mapLeft {
                log.error("$loggtema: Kunne sende brev via ekternt system")
                KunneIkkeOppretteJournalpostOgSendeBrev
            }
            .map {
                log.error("$loggtema: Brev sendt OK via ekstern system")
                it
            }
    }

    fun lagUtkastTilBrev(
        behandling: Behandling
    ): Either<ClientError, ByteArray> {
        return sakService.hentSak(behandling.sakId)
            .mapLeft { throw RuntimeException("Fant ikke sak") }
            .map {
                val person = hentPersonFraFnr(it.fnr).fold(
                    { return ClientError(httpStatus = it.httpCode, message = it.message).left() },
                    { it }
                )
                return lagBrevPdf(behandling, person)
            }
    }

    private fun lagBrevPdf(
        behandling: Behandling,
        person: Person
    ): Either<ClientError, ByteArray> {
        val vedtakinnhold = lagVedtakInnhold(person, behandling)
        val innvilget = listOf(
            Behandling.BehandlingsStatus.SIMULERT,
            Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
        )
        val template = if (innvilget.contains(vedtakinnhold.status)) Vedtakstype.INNVILGELSE else Vedtakstype.AVSLAG

        return pdfGenerator.genererPdf(vedtakinnhold, template)
            .mapLeft {
                log.error("Kunne ikke generere brevinnhold")
                it
            }
            .map {
                log.info("Generert brevinnhold OK")
                it
            }
    }

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Fant ikke person i eksternt system basert på sakens fødselsnummer.")
            it
        }.map {
            log.info("Hentet person fra eksternt system OK")
            it
        }

    private fun sendBrev(journalPostId: String): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        return dokDistFordeling.bestillDistribusjon(journalPostId).mapLeft { KunneIkkeOppretteJournalpostOgSendeBrev }
    }

    object KunneIkkeOppretteJournalpostOgSendeBrev
}

fun avslagsgrunnForBehandling(behandling: Behandling): Avslagsgrunn? {
    if (behandling.beregning()?.beløpErNull() == true) {
        return Avslagsgrunn.FOR_HØY_INNTEKT
    }
    if (behandling.beregning()?.beløpErOverNullMenUnderMinstebeløp() == true) {
        return Avslagsgrunn.SU_UNDER_MINSTEGRENSE
    }

    return behandling.behandlingsinformasjon().let {
        when {
            it.uførhet?.status == Uførhet.Status.VilkårIkkeOppfylt -> Avslagsgrunn.UFØRHET
            it.flyktning?.status == Flyktning.Status.VilkårIkkeOppfylt -> Avslagsgrunn.FLYKTNING
            it.lovligOpphold?.status == LovligOpphold.Status.VilkårIkkeOppfylt -> Avslagsgrunn.OPPHOLDSTILLATELSE
            it.fastOppholdINorge?.status == FastOppholdINorge.Status.VilkårIkkeOppfylt -> Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
            it.oppholdIUtlandet?.status == OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet -> Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
            it.personligOppmøte?.status.let { s ->
                s == PersonligOppmøte.Status.IkkeMøttOpp ||
                    s == PersonligOppmøte.Status.FullmektigUtenLegeattest
            } -> Avslagsgrunn.PERSONLIG_OPPMØTE
            else -> null
        }
    }
}

fun beskrivelseForAvslagsgrunn(avslagsgrunn: Avslagsgrunn?): AvslagsgrunnBeskrivelse? =
    when (avslagsgrunn) {
        Avslagsgrunn.UFØRHET -> AvslagsgrunnBeskrivelse.UFØRHET_FLYKTNING
        Avslagsgrunn.FLYKTNING -> AvslagsgrunnBeskrivelse.UFØRHET_FLYKTNING
        Avslagsgrunn.FORMUE -> AvslagsgrunnBeskrivelse.FORMUE
        Avslagsgrunn.FOR_HØY_INNTEKT -> AvslagsgrunnBeskrivelse.HØY_INNTEKT
        Avslagsgrunn.SU_UNDER_MINSTEGRENSE -> AvslagsgrunnBeskrivelse.UNDER_MINSTEGRENSE
        Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> AvslagsgrunnBeskrivelse.UTLAND_OG_OPPHOLD
        Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER -> AvslagsgrunnBeskrivelse.UTLAND_OG_OPPHOLD
        Avslagsgrunn.OPPHOLDSTILLATELSE -> AvslagsgrunnBeskrivelse.UTLAND_OG_OPPHOLD
        else -> null
    }

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))

// TODO jah: Unngå utregninger i BrevService
fun List<Fradrag>.toFradragPerMåned(): List<Fradrag> =
    this.map {
        Fradrag(
            id = it.id,
            type = it.type,
            beløp = it.beløp / 12,
            utenlandskInntekt = it.utenlandskInntekt,
            inntektDelerAvPeriode = it.inntektDelerAvPeriode,
        )
    }
