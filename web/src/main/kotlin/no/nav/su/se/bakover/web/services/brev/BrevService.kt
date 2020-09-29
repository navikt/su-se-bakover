package no.nav.su.se.bakover.web.services.brev

import arrow.core.Either
import arrow.core.left
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.client.pdf.Vedtakstype
import no.nav.su.se.bakover.client.person.PersonOppslag
import no.nav.su.se.bakover.domain.Avslagsgrunn
import no.nav.su.se.bakover.domain.AvslagsgrunnBeskrivelseFlagg
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.VedtakInnhold
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Fradrag
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
    private val log = LoggerFactory.getLogger(this::class.java)

    companion object {
        fun lagVedtakInnhold(person: Person, behandling: Behandling): VedtakInnhold {
            val fnr = behandling.søknad.søknadInnhold.personopplysninger.fnr
            val avslagsgrunn = avslagsgrunnForBehandling(behandling)
            val avslagsgrunnBeskrivelse = flaggForAvslagsgrunn(avslagsgrunn)
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

        val person = hentPersonFraFnr(sak.fnr).fold({ return it.left() }, { it })

        val brevInnhold = lagUtkastTilBrev(behandling, person).fold({ return it.left() }, { it })

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
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, ByteArray> {
        val fnr = behandling.fnr
        val person = hentPersonFraFnr(fnr).fold({ return it.left() }, { it })
        return lagUtkastTilBrev(behandling, person)
    }

    private fun lagUtkastTilBrev(
        behandling: Behandling,
        person: Person
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, ByteArray> {
        val vedtakinnhold = lagVedtakInnhold(person, behandling)
        val innvilget = listOf(
            Behandling.BehandlingsStatus.SIMULERT,
            Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
        )
        val template = if (innvilget.contains(vedtakinnhold.status)
        ) Vedtakstype.INNVILGELSE else Vedtakstype.AVSLAG
        return pdfGenerator.genererPdf(vedtakinnhold, template)
            .mapLeft {
                log.error("Journalføring og sending av vedtaksbrev: Kunne ikke generere brevinnhold")
                KunneIkkeOppretteJournalpostOgSendeBrev
            }
            .map {
                log.error("Journalføring og sending av vedtaksbrev: Generert brevinnhold OK")
                it
            }
    }

    private fun hentPersonFraFnr(fnr: Fnr) = personOppslag.person(fnr)
        .mapLeft {
            log.error("Journalføring og sending av vedtaksbrev: Fant ikke person i ekstern system basert på sakens fødselsnummer.")
            KunneIkkeOppretteJournalpostOgSendeBrev
        }.map {
            log.info("Journalføring og sending av vedtaksbrev: Hentet person fra eksternt system OK")
            it
        }

    private fun sendBrev(journalPostId: String): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        return dokDistFordeling.bestillDistribusjon(journalPostId).mapLeft { KunneIkkeOppretteJournalpostOgSendeBrev }
    }

    object KunneIkkeOppretteJournalpostOgSendeBrev
}

fun flaggForAvslagsgrunn(avslagsgrunn: Avslagsgrunn?): AvslagsgrunnBeskrivelseFlagg? =
    when (avslagsgrunn) {
        Avslagsgrunn.UFØRHET -> AvslagsgrunnBeskrivelseFlagg.UFØRHET_FLYKTNING
        Avslagsgrunn.FLYKTNING -> AvslagsgrunnBeskrivelseFlagg.UFØRHET_FLYKTNING
        Avslagsgrunn.FORMUE -> AvslagsgrunnBeskrivelseFlagg.FORMUE
        Avslagsgrunn.FOR_HØY_INNTEKT -> AvslagsgrunnBeskrivelseFlagg.HØY_INNTEKT
        Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE -> AvslagsgrunnBeskrivelseFlagg.UTLAND_OG_OPPHOLD
        Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER -> AvslagsgrunnBeskrivelseFlagg.UTLAND_OG_OPPHOLD
        else -> null
    }

fun avslagsgrunnForBehandling(behandling: Behandling): Avslagsgrunn? =
    behandling.behandlingsinformasjon().let {
        when {
            it.uførhet?.status == Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt -> Avslagsgrunn.UFØRHET
            it.flyktning?.status == Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt -> Avslagsgrunn.FLYKTNING
            it.lovligOpphold?.status == Behandlingsinformasjon.LovligOpphold.Status.VilkårIkkeOppfylt -> Avslagsgrunn.OPPHOLDSTILLATELSE
            it.fastOppholdINorge?.status == Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt -> Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
            it.oppholdIUtlandet?.status == Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet -> Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
            it.personligOppmøte?.status.let { s ->
                s == Behandlingsinformasjon.PersonligOppmøte.Status.IkkeMøttOpp ||
                    s == Behandlingsinformasjon.PersonligOppmøte.Status.FullmektigUtenLegeattest
            } -> Avslagsgrunn.PERSONLIG_OPPMØTE
            else -> null
        }
    }

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))

// TODO jah: Unngå utregninger i BrevService
fun List<Fradrag>.toFradragPerMåned(): List<Fradrag> =
    this.map { Fradrag(it.id, it.type, it.beløp / 12, it.beskrivelse) }
