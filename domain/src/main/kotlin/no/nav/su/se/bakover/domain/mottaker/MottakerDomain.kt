package no.nav.su.se.bakover.domain.mottaker

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.person.Fnr
import java.util.UUID

sealed interface FeilkoderMottaker {
    data object KanIkkeLagreMottaker : FeilkoderMottaker
    data object KanIkkeOppdatereMottaker : FeilkoderMottaker
    data object BrevFinnesIDokumentBasen : FeilkoderMottaker
    data object ForespurtSakIdMatcherIkkeMottaker : FeilkoderMottaker
    data class UgyldigMottakerRequest(val feil: String) : FeilkoderMottaker
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
    val referanseId: String
    val referanseType: String
    val brevtype: String

    fun validerFelles(): List<String> {
        val feil = mutableListOf<String>()

        if (navn.isBlank()) {
            feil += "Navn er blank"
        }

        feil += validerFnrEllerOrgnummer(this)
        feil += validerAdresse(adresse)

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

        if (brevtype.isBlank()) {
            feil += "brevtype mangler"
        } else {
            if (brevtype.tilBrevtypeForMottaker() == null) {
                feil += ugyldigBrevtypeMelding(brevtype)
            }
        }

        return feil
    }
}

fun ugyldigBrevtypeMelding(brevtype: String): String =
    "Ugyldig brevtype for mottaker: $brevtype. Tillatte verdier: ${tillatteBrevtyperForMottaker.joinToString { it.name }}"

fun ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
    referanseType: ReferanseTypeMottaker,
    brevtype: Brevtype,
): String = "Ugyldig kombinasjon referanseType=$referanseType og brevtype=$brevtype"

data class OppdaterMottaker(
    val id: String,
    override val navn: String,
    override val foedselsnummer: String? = null,
    override val orgnummer: String? = null,
    override val adresse: DistribueringsadresseRequest,
    override val referanseId: String,
    override val referanseType: String,
    override val brevtype: String,
) : MottakerRequest {

    private fun erGyldig(): List<String> {
        val feil = mutableListOf<String>()
        if (!id.isUuid()) {
            feil += "MottakerId er ikke en gyldig UUID"
        }
        feil.addAll(this.validerFelles())

        return feil
    }

    fun toDomain(sakId: UUID): Either<List<String>, MottakerDomain> {
        val erGyldig = this.erGyldig()
        return if (erGyldig.isEmpty()) {
            val brevtypeForMottaker = brevtype.tilBrevtypeForMottaker()
                ?: error("Forventet gyldig brevtype for mottaker. brevtype=$brevtype")
            if (foedselsnummer == null) {
                MottakerOrgnummerDomain(
                    id = UUID.fromString(id),
                    navn = navn,
                    orgnummer = orgnummer!!,
                    adresse = adresse.toDomain(),
                    sakId = sakId,
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType.uppercase()),
                    brevtype = brevtypeForMottaker,
                ).right()
            } else {
                MottakerFnrDomain(
                    id = UUID.fromString(id),
                    navn = navn,
                    foedselsnummer = Fnr.tryCreate(foedselsnummer)!!,
                    adresse = adresse.toDomain(),
                    sakId = sakId,
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType.uppercase()),
                    brevtype = brevtypeForMottaker,
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
    override val referanseId: String,
    override val referanseType: String,
    override val brevtype: String,
) : MottakerRequest {
    private fun erGyldig(): List<String> {
        val feil = mutableListOf<String>()
        feil.addAll(this.validerFelles())

        return feil
    }

    fun toDomain(sakId: UUID): Either<List<String>, MottakerDomain> {
        val erGyldig = this.erGyldig()
        return if (erGyldig.isEmpty()) {
            val brevtypeForMottaker = brevtype.tilBrevtypeForMottaker()
                ?: error("Forventet gyldig brevtype for mottaker. brevtype=$brevtype")
            if (foedselsnummer == null) {
                MottakerOrgnummerDomain(
                    navn = navn,
                    orgnummer = orgnummer!!,
                    adresse = adresse.toDomain(),
                    sakId = sakId,
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType.uppercase()),
                    brevtype = brevtypeForMottaker,
                ).right()
            } else {
                MottakerFnrDomain(
                    navn = navn,
                    foedselsnummer = Fnr.tryCreate(foedselsnummer)!!,
                    adresse = adresse.toDomain(),
                    sakId = sakId,
                    referanseId = UUID.fromString(referanseId),
                    referanseType = ReferanseTypeMottaker.valueOf(referanseType.uppercase()),
                    brevtype = brevtypeForMottaker,
                ).right()
            }
        } else {
            erGyldig.left()
        }
    }
}

val tillatteBrevtyperForMottaker = setOf(
    Brevtype.VEDTAK,
    Brevtype.FORHANDSVARSEL,
    Brevtype.OVERSENDELSE_KA,
)

fun Brevtype.erTillattForMottaker(): Boolean = this in tillatteBrevtyperForMottaker

private fun String.tilBrevtypeForMottaker(): Brevtype? =
    Brevtype.fraString(this)
        ?.takeIf { it.erTillattForMottaker() }

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
            if (orgnummer.length != 9 || !orgnummer.all { it.isDigit() }) {
                feil += "Organisasjonsnummer må være 9 siffer langt"
            }
        }
    }
    return feil
}

private fun validerAdresse(adresse: DistribueringsadresseRequest): List<String> {
    val feil = mutableListOf<String>()

    if (adresse.poststed.isNullOrBlank()) {
        feil += "Poststed er tom"
    }
    if (adresse.postnummer.isNullOrBlank()) {
        feil += "Postnummer er tom"
    } else if (adresse.postnummer.length != 4 || !adresse.postnummer.all { it.isDigit() }) {
        feil += "Postnummer må være 4 siffer langt"
    }
    return feil
}

class MottakerIdentifikator(
    val referanseType: ReferanseTypeMottaker,
    val referanseId: UUID,
    val brevtype: Brevtype,
)

sealed interface MottakerDomain {
    val id: UUID
    val navn: String
    val adresse: Distribueringsadresse // toDokDistRequestJson + DokDistAdresseJson
    val sakId: UUID
    val referanseId: UUID
    val referanseType: ReferanseTypeMottaker
    val brevtype: Brevtype
}

data class MottakerOrgnummerDomain(
    override val id: UUID = UUID.randomUUID(),
    override val navn: String,
    override val adresse: Distribueringsadresse,
    override val sakId: UUID,
    override val referanseId: UUID,
    override val referanseType: ReferanseTypeMottaker,
    override val brevtype: Brevtype,
    val orgnummer: String,
) : MottakerDomain

data class MottakerFnrDomain(
    override val id: UUID = UUID.randomUUID(),
    override val navn: String,
    override val adresse: Distribueringsadresse,
    override val sakId: UUID,
    override val referanseId: UUID,
    override val referanseType: ReferanseTypeMottaker,
    override val brevtype: Brevtype,
    val foedselsnummer: Fnr,
) : MottakerDomain

enum class ReferanseTypeMottaker {
    SØKNAD,
    REVURDERING,

    KLAGE,

    DØDSBO_TILBAKEKREVING,
}
