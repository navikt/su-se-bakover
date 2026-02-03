package no.nav.su.se.bakover.domain.mottaker

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.person.Fnr
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

interface MottakerRepo {
    fun hentMottaker(mottakerIdentifikator: MottakerIdentifikator): MottakerDomain?
    fun lagreMottaker(mottaker: MottakerDomain)
    fun oppdaterMottaker(mottaker: MottakerDomain)
    fun slettMottaker(mottakerId: UUID)
}

interface MottakerService {
    fun hentMottaker(mottakerIdentifikator: MottakerIdentifikator, sakId: UUID): Either<FeilkoderMottaker, MottakerDomain?>
    fun lagreMottaker(mottaker: LagreMottaker, sakId: UUID): Either<FeilkoderMottaker, Unit>
    fun oppdaterMottaker(mottaker: OppdaterMottaker, sakId: UUID): Either<FeilkoderMottaker, Unit>
    fun slettMottaker(mottakerIdentifikator: MottakerIdentifikator, sakId: UUID): Either<FeilkoderMottaker, Unit>
}

sealed interface FeilkoderMottaker {
    data object KanIkkeLagreMottaker : FeilkoderMottaker
    data object KanIkkeOppdatereMottaker : FeilkoderMottaker
    data object BrevFinnesIDokumentBasen : FeilkoderMottaker
    data object ForespurtSakIdMatcherIkkeMottaker : FeilkoderMottaker
    data class UgyldigMottakerRequest(val feil: String) : FeilkoderMottaker
}

class MottakerServiceImpl(
    private val mottakerRepo: MottakerRepo,
    private val dokumentRepo: DokumentRepo,
) : MottakerService {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Alle dokumenter som kun har sakid men ingen annen id kan ikke ha flere mottakere da de er "automatiske"
     * Eller manuelle brev som er opprettet direkte på saken uten annen tilknytning og kan ikke unikt identifiseres
     * hvis vi skal støtte frie brev med flere mottakere mot som feks [lagreOgSendOpplastetPdfPåSak] må man ha en ekstra referanseid på dem
     */
    override fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
    ): Either<FeilkoderMottaker, MottakerDomain?> {
        val hentetMottaker = mottakerRepo.hentMottaker(mottakerIdentifikator)?.let {
            if (it.sakId != sakId) {
                return FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker.left()
            } else {
                it
            }
        }
        return hentetMottaker.right()
    }

    private fun kanEndreForMottaker(mottaker: MottakerDomain): Boolean {
        val dokumenter = when (mottaker.referanseType) {
            ReferanseTypeMottaker.SØKNAD ->
                dokumentRepo.hentForSøknad(mottaker.referanseId)

            ReferanseTypeMottaker.VEDTAK ->
                dokumentRepo.hentForVedtak(mottaker.referanseId)

            // De andre typene støtter ikke flere mottakere og kan ha flere per revurderingid som ødelegger bindingen mot mottakertabellen siden man ikke har noen unik id å knytte det mot.
            // Du kan til og med ha flere av samme typen per revurdering så her må man tilpasse om det skal støttes.
            ReferanseTypeMottaker.REVURDERING -> dokumentRepo.hentForRevurdering(mottaker.referanseId)
                .filter { it is Dokument.MedMetadata.Vedtak }

            ReferanseTypeMottaker.KLAGE ->
                dokumentRepo.hentForKlage(mottaker.referanseId)
        }

        return dokumenter.isEmpty()
    }

    override fun lagreMottaker(
        mottaker: LagreMottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        val mottakerValidert = mottaker.toDomain().getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it.joinToString { "," }).left()
        }
        val kanEndre = kanEndreForMottaker(mottakerValidert)
        return if (kanEndre) {
            mottakerRepo.lagreMottaker(mottakerValidert).right()
        } else {
            FeilkoderMottaker.KanIkkeLagreMottaker.left()
        }
    }

    override fun oppdaterMottaker(
        mottaker: OppdaterMottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        val mottakerValidert = mottaker.toDomain().getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it.joinToString { "," }).left()
        }
        val kanEndre = kanEndreForMottaker(mottakerValidert)
        return if (kanEndre) {
            mottakerRepo.oppdaterMottaker(mottakerValidert).right()
        } else {
            FeilkoderMottaker.KanIkkeOppdatereMottaker.left()
        }
    }

    override fun slettMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        val mottaker = mottakerRepo.hentMottaker(mottakerIdentifikator)
        return if (mottaker == null) {
            log.info("Fant ikke mottaker for type ${mottakerIdentifikator.referanseType} id: ${mottakerIdentifikator.referanseId} ingenting å slette")
            return Unit.right()
        } else {
            // Kun revurderinger kan ha flere brev registrert på seg av andre typer. Fant bare [INFORMASJON_VIKTIG] -> [Dokument.MedMetadata.Informasjon.Viktig] med duplikater for revurderinger i prod
            val dokument = when (mottaker.referanseType) {
                ReferanseTypeMottaker.SØKNAD ->
                    dokumentRepo.hentForSøknad(mottaker.referanseId)

                // TODO: usikkert om noen knytning direkte mot vedtak skal forekomme da vedtaket ikke er opprettet når vi legger knytningen mot mottaker
                ReferanseTypeMottaker.VEDTAK ->
                    dokumentRepo.hentForVedtak(mottaker.referanseId)

                ReferanseTypeMottaker.REVURDERING ->
                    dokumentRepo.hentForRevurdering(mottaker.referanseId).filter {
                        when (it) {
                            is Dokument.MedMetadata.Informasjon.Annet -> false
                            is Dokument.MedMetadata.Informasjon.Viktig -> false
                            is Dokument.MedMetadata.Vedtak -> true
                        }
                    }

                ReferanseTypeMottaker.KLAGE ->
                    dokumentRepo.hentForKlage(mottaker.referanseId)
            }
            if (dokument.isNotEmpty()) {
                log.info("Kan ikke slette mottaker da det finnes et brev for referansen")
                return FeilkoderMottaker.BrevFinnesIDokumentBasen.left()
            }
            log.info("Sletter mottaker med id: ${mottaker.id} sakid ${mottaker.sakId} type ${mottaker.referanseType} id: ${mottaker.referanseId}")
            mottakerRepo.slettMottaker(mottaker.id).right()
        }
    }
}

data class DistribueringsadresseRequest(
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
) {
    fun toDomain() = Distribueringsadresse(
        adresselinje1 = this.adresselinje1,
        adresselinje2 = this.adresselinje2,
        adresselinje3 = this.adresselinje3,
        postnummer = this.postnummer!!,
        poststed = this.poststed!!,
    )
}

fun String.isUuid(): Boolean =
    try {
        UUID.fromString(this)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

sealed interface MottakerRequest {
    val navn: String
    val foedselsnummer: String?
    val orgnummer: String?
    val adresse: DistribueringsadresseRequest
    val sakId: String
    val referanseId: String
    val referanseType: String

    fun validerFelles(): List<String> {
        val feil = mutableListOf<String>()

        if (navn.isBlank()) {
            feil += "Navn er blank"
        }

        feil += validerFnrEllerOrgnummer(this)
        feil += validerAdrese(adresse)

        if (sakId.isBlank()) {
            feil += "sakId mangler"
        }

        if (referanseId.isBlank()) {
            feil += "referanseId mangler"
        } else if (!referanseId.isUuid()) {
            feil += "referanseId er ikke en gyldig UUID"
        }

        if (referanseType.isBlank()) {
            feil += "referanseType mangler"
        } else {
            runCatching {
                ReferanseTypeMottaker.valueOf(referanseType.uppercase())
            }.getOrElse {
                feil += "Ugyldig referanseType: $referanseType"
            }
        }

        return feil
    }
}

private fun validerFnrEllerOrgnummer(req: MottakerRequest): List<String> {
    val feil = mutableListOf<String>()
    val foedselsnummer = req.foedselsnummer
    val orgnummer = req.orgnummer
    val hasFnr = !foedselsnummer.isNullOrBlank()
    val hasOrgnr = !orgnummer.isNullOrBlank()

    when {
        hasFnr && hasOrgnr -> {
            feil += "Kan ikke ha både fødselsnummer og organisasjonsnummer"
        }

        !hasFnr && !hasOrgnr -> {
            feil += "Enten fødselsnummer eller organisasjonsnummer må angis"
        }

        hasFnr -> {
            if (Fnr.tryCreate(foedselsnummer) == null) {
                feil += "Ugyldig fødselsnummer"
            }
        }

        hasOrgnr -> {
            if (orgnummer.length != 9) {
                feil += "Organisasjonsnummer må være 9 siffer langt"
            }
        }
    }
    return feil
}

private fun validerAdrese(adresse: DistribueringsadresseRequest): List<String> {
    val feil = mutableListOf<String>()

    if (adresse.poststed.isNullOrBlank()) {
        feil += "Poststed er tom"
    }
    if (adresse.postnummer.isNullOrBlank()) {
        feil += "Postnummer er tom"
    } else if (adresse.postnummer.length != 4) {
        feil += "Postnummer må være 4 siffer langt"
    }
    return feil
}

data class OppdaterMottaker(
    val id: String,
    override val navn: String,
    override val foedselsnummer: String? = null,
    override val orgnummer: String? = null,
    override val adresse: DistribueringsadresseRequest,
    override val sakId: String,
    override val referanseId: String,
    override val referanseType: String,
) : MottakerRequest {

    private fun erGyldig(): List<String> {
        val feil = mutableListOf<String>()
        if (!id.isUuid()) {
            feil += "MottakerId er ikke en gyldig UUID"
        }
        feil.addAll(this.validerFelles())

        return feil
    }

    fun toDomain(): Either<List<String>, MottakerDomain> {
        val erGyldig = this.erGyldig()
        return if (erGyldig.isEmpty()) {
            if (foedselsnummer == null) {
                MottakerOrgnummerDomain(
                    navn = navn,
                    orgnummer = orgnummer,
                    adresse = adresse.toDomain(),
                    sakId = UUID.fromString(sakId),
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType),
                ).right()
            } else {
                MottakerFnrDomain(
                    navn = navn,
                    foedselsnummer = Fnr.tryCreate(foedselsnummer),
                    adresse = adresse.toDomain(),
                    sakId = UUID.fromString(sakId),
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType),
                ).right()
            }
        } else {
            erGyldig.left()
        }
    }
}

data class LagreMottaker(
    override val navn: String,
    override val foedselsnummer: String? = null,
    override val orgnummer: String? = null,
    override val adresse: DistribueringsadresseRequest,
    override val sakId: String,
    override val referanseId: String,
    override val referanseType: String,
) : MottakerRequest {
    private fun erGyldig(): List<String> {
        val feil = mutableListOf<String>()
        feil.addAll(this.validerFelles())

        return feil
    }

    fun toDomain(): Either<List<String>, MottakerDomain> {
        val erGyldig = this.erGyldig()
        return if (erGyldig.isEmpty()) {
            if (foedselsnummer == null) {
                MottakerOrgnummerDomain(
                    navn = navn,
                    orgnummer = orgnummer,
                    adresse = adresse.toDomain(),
                    sakId = UUID.fromString(sakId),
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType),
                ).right()
            } else {
                MottakerFnrDomain(
                    navn = navn,
                    foedselsnummer = Fnr.tryCreate(foedselsnummer),
                    adresse = adresse.toDomain(),
                    sakId = UUID.fromString(sakId),
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType),
                ).right()
            }
        } else {
            erGyldig.left()
        }
    }
}

class MottakerIdentifikator(
    val referanseType: ReferanseTypeMottaker,
    val referanseId: UUID,
)

sealed interface MottakerDomain {
    val id: UUID
    val navn: String
    val adresse: Distribueringsadresse // toDokDistRequestJson + DokDistAdresseJson
    val sakId: UUID
    val referanseId: UUID
    val referanseType: ReferanseTypeMottaker
}

data class MottakerOrgnummerDomain(
    override val id: UUID = UUID.randomUUID(),
    override val navn: String,
    override val adresse: Distribueringsadresse,
    override val sakId: UUID,
    override val referanseId: UUID,
    override val referanseType: ReferanseTypeMottaker,
    val orgnummer: String?,
) : MottakerDomain

data class MottakerFnrDomain(
    override val id: UUID = UUID.randomUUID(),
    override val navn: String,
    override val adresse: Distribueringsadresse,
    override val sakId: UUID,
    override val referanseId: UUID,
    override val referanseType: ReferanseTypeMottaker,
    val foedselsnummer: Fnr?,
) : MottakerDomain

enum class ReferanseTypeMottaker {
    SØKNAD,
    VEDTAK, // Tror ikke denne går å støtte da behandlingen er knyttet opp mot vedtak men som kun opprettes etter iverksatt så vi har ingen binding til den før det er for sent
    REVURDERING,
    KLAGE, // special case
}
