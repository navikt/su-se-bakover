package no.nav.su.se.bakover.service.brev

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
import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.FradragPerMåned
import no.nav.su.se.bakover.domain.Grunnbeløp
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Satsgrunn
import no.nav.su.se.bakover.domain.VedtakInnhold
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
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
import java.util.UUID

class BrevServiceImpl(
    private val pdfGenerator: PdfGenerator,
    private val personOppslag: PersonOppslag,
    private val dokArkiv: DokArkiv,
    private val dokDistFordeling: DokDistFordeling,
    private val sakService: SakService
) : BrevService {
    private val log = LoggerFactory.getLogger(this::class.java)

    private fun lagVedtaksinnhold(person: Person, behandling: Behandling): VedtakInnhold =
        when {
            erAvslått(behandling) -> lagAvslagsvedtak(person, behandling)
            erInnvilget(behandling) -> lagInnvilgelsesvedtak(person, behandling)
            else -> throw java.lang.RuntimeException("Kan ikke lage vedtaksinnhold for behandling som ikke er avslått/innvilget")
        }

    private fun lagAvslagsvedtak(person: Person, behandling: Behandling) =
        VedtakInnhold.Avslagsvedtak(
            dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            fødselsnummer = behandling.søknad.søknadInnhold.personopplysninger.fnr,
            fornavn = person.navn.fornavn,
            etternavn = person.navn.etternavn,
            adresse = person.adresse?.adressenavn,
            bruksenhet = person.adresse?.bruksenhet,
            husnummer = person.adresse?.husnummer,
            postnummer = person.adresse?.poststed?.postnummer,
            poststed = person.adresse?.poststed?.poststed!!,
            satsbeløp = behandling.beregning()?.månedsberegninger?.firstOrNull()?.satsBeløp ?: 0,
            fradragSum = behandling.beregning()?.fradrag?.toFradragPerMåned()?.sumBy { fradrag -> fradrag.beløp } ?: 0,
            avslagsgrunn = avslagsgrunnForBehandling(behandling)!!,
            halvGrunnbeløp = Grunnbeløp.`0,5G`.fraDato(LocalDate.now()).toInt()
        )

    private fun lagInnvilgelsesvedtak(person: Person, behandling: Behandling): VedtakInnhold.Innvilgelsesvedtak {
        val førsteMånedsberegning =
            behandling.beregning()!!.månedsberegninger.firstOrNull()!! // Støtte för variende beløp i framtiden?

        return VedtakInnhold.Innvilgelsesvedtak(
            dato = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),
            fødselsnummer = behandling.søknad.søknadInnhold.personopplysninger.fnr,
            fornavn = person.navn.fornavn,
            etternavn = person.navn.etternavn,
            adresse = person.adresse?.adressenavn,
            bruksenhet = person.adresse?.bruksenhet,
            husnummer = person.adresse?.husnummer,
            postnummer = person.adresse?.poststed?.postnummer,
            poststed = person.adresse?.poststed?.poststed!!,
            månedsbeløp = førsteMånedsberegning.beløp,
            fradato = behandling.beregning()!!.fraOgMed.formatMonthYear(),
            tildato = behandling.beregning()!!.tilOgMed.formatMonthYear(),
            sats = behandling.beregning()?.sats.toString().toLowerCase(),
            satsbeløp = førsteMånedsberegning.satsBeløp,
            satsGrunn = satsgrunnForBehandling(behandling)!!,
            redusertStønadStatus = behandling.beregning()?.fradrag?.isNotEmpty() ?: false,
            harEktefelle = behandling.behandlingsinformasjon().bosituasjon?.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            fradrag = behandling.beregning()!!.fradrag.toFradragPerMåned(),
            fradragSum = behandling.beregning()!!.fradrag.toFradragPerMåned().sumBy { fradrag -> fradrag.beløp },
        )
    }

    private fun erInnvilget(behandling: Behandling): Boolean {
        val innvilget = listOf(
            Behandling.BehandlingsStatus.SIMULERT,
            Behandling.BehandlingsStatus.BEREGNET_INNVILGET,
            Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            Behandling.BehandlingsStatus.IVERKSATT_INNVILGET
        )

        return innvilget.contains(behandling.status())
    }

    private fun erAvslått(behandling: Behandling): Boolean {
        val avslått = listOf(
            Behandling.BehandlingsStatus.BEREGNET_AVSLAG,
            Behandling.BehandlingsStatus.VILKÅRSVURDERT_AVSLAG,
            Behandling.BehandlingsStatus.TIL_ATTESTERING_AVSLAG,
            Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
        )

        return avslått.contains(behandling.status())
    }

    override fun journalførVedtakOgSendBrev(
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
                vedtakInnhold = lagVedtaksinnhold(person, behandling),
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

    override fun lagUtkastTilBrev(
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

    override fun journalførTrukketSøknadOgSendBrev(
        sakId: UUID,
        søknadId: UUID
    ): Either<KunneIkkeOppretteJournalpostOgSendeBrev, String> {
        val loggtema = "Journalføring og trekking av søknad"

        val person = sakService.hentSak(sakId).fold(
            ifLeft = {
                log.error("$loggtema: fant ikke sak for sakId: $sakId")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            ifRight = { sak ->
                hentPersonFraFnr(sak.fnr).fold(
                    ifLeft = {
                        log.error("$loggtema: kunne ikke hente person for sakId: $sakId")
                        return KunneIkkeOppretteJournalpostOgSendeBrev.left()
                    },
                    ifRight = { person ->
                        log.info("Hentet Person for avsluttet søknads-behandling OK")
                        person
                    }
                )
            }
        )

        val trukketSøknadBrevPdf = genererTrukketSøknadBrevPdf(sakId = sakId, søknadId = søknadId).fold(
            ifLeft = {
                log.error("$loggtema: kunne ikke generere pdf for å trekke søknad")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            ifRight = {
                log.info("Generert brev for trekking av søknad OK")
                it
            }
        )

        val journalPostId = dokArkiv.opprettJournalpost(
            Journalpost.Trukket(
                person = person,
                pdf = trukketSøknadBrevPdf,
                sakId = sakId,
                søknadId = søknadId,
            )
        ).fold(
            ifLeft = {
                log.error("$loggtema: kunne ikke få journalpost id")
                return KunneIkkeOppretteJournalpostOgSendeBrev.left()
            },
            ifRight = {
                log.info("Journalpost id for trekking av søknad OK")
                it
            }
        )

        return sendBrev(journalPostId)
    }

    private fun lagBrevPdf(
        behandling: Behandling,
        person: Person
    ): Either<ClientError, ByteArray> {
        val vedtakinnhold = lagVedtaksinnhold(person, behandling)
        val template = when (vedtakinnhold) {
            is VedtakInnhold.Innvilgelsesvedtak -> Vedtakstype.INNVILGELSE
            is VedtakInnhold.Avslagsvedtak -> Vedtakstype.AVSLAG
        }

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

    private fun genererTrukketSøknadBrevPdf(
        sakId: UUID,
        søknadId: UUID
    ): Either<ClientError, ByteArray> {

        return pdfGenerator.genererTrukketSøknadPdf(sakId = sakId, søknadId = søknadId, Vedtakstype.TREKK_SØKNAD)
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
            it.formue?.status == Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt -> Avslagsgrunn.FORMUE
            it.personligOppmøte?.status.let { s ->
                s == PersonligOppmøte.Status.IkkeMøttOpp ||
                    s == PersonligOppmøte.Status.FullmektigUtenLegeattest
            } -> Avslagsgrunn.PERSONLIG_OPPMØTE
            else -> null
        }
    }
}

fun satsgrunnForBehandling(behandling: Behandling): Satsgrunn? {
    return behandling.behandlingsinformasjon().bosituasjon?.let {
        when {
            !it.delerBolig -> Satsgrunn.ENSLIG
            it.delerBoligMed == Boforhold.DelerBoligMed.VOKSNE_BARN -> Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            it.delerBoligMed == Boforhold.DelerBoligMed.ANNEN_VOKSEN -> Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
            it.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER
                && it.ektemakeEllerSamboerUnder67År == false -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67
            it.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER &&
                it.ektemakeEllerSamboerUnder67År == true && it.ektemakeEllerSamboerUførFlyktning == false -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
            it.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER
                && it.ektemakeEllerSamboerUførFlyktning == true -> Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
            else -> null
        }
    }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))

internal fun List<Fradrag>.toFradragPerMåned(): List<FradragPerMåned> =
    this.map {
        FradragPerMåned(it.type, it.perMåned())
    }
