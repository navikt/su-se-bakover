package no.nav.su.se.bakover.domain.mottaker

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import dokument.domain.Brevtype
import dokument.domain.DokumentRepo
import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

interface MottakerRepo {
    fun hentMottaker(mottakerIdentifikator: MottakerIdentifikator, transactionContext: TransactionContext? = null): MottakerDomain?
    fun lagreMottaker(mottaker: MottakerDomain)
    fun oppdaterMottaker(mottaker: MottakerDomain)
    fun slettMottaker(mottakerId: UUID)
}

interface MottakerService {
    fun hentMottaker(mottakerIdentifikator: MottakerIdentifikator, sakId: UUID, transactionContext: TransactionContext? = null): Either<FeilkoderMottaker, MottakerDomain?>
    fun lagreMottaker(mottaker: LagreMottaker, sakId: UUID): Either<FeilkoderMottaker, MottakerDomain>
    fun oppdaterMottaker(mottaker: OppdaterMottaker, sakId: UUID): Either<FeilkoderMottaker, Unit>
    fun slettMottaker(mottakerIdentifikator: MottakerIdentifikator, sakId: UUID): Either<FeilkoderMottaker, Unit>
}

private val tillatteBrevtyperForMottaker = setOf(
    Brevtype.VEDTAK,
    Brevtype.FORHANDSVARSEL,
    Brevtype.OVERSENDELSE_KA,
)

private fun Brevtype.erTillattForMottaker(): Boolean = this in tillatteBrevtyperForMottaker

private fun String.tilBrevtypeForMottaker(): Brevtype? =
    Brevtype.fraString(this)
        ?.takeIf { it.erTillattForMottaker() }

private fun ugyldigBrevtypeMelding(brevtype: String): String =
    "Ugyldig brevtype for mottaker: $brevtype. Tillatte verdier: ${tillatteBrevtyperForMottaker.joinToString { it.name }}"

private fun ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
    referanseType: ReferanseTypeMottaker,
    brevtype: Brevtype,
): String = "Ugyldig kombinasjon referanseType=$referanseType og brevtype=$brevtype"

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
    private val vedtakRepo: VedtakRepo,
) : MottakerService {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    private fun erGyldigBrevtype(
        brevtype: Brevtype,
    ): Boolean = brevtype.erTillattForMottaker()

    private fun erGyldigKombinasjonReferanseTypeOgBrevtype(
        referanseType: ReferanseTypeMottaker,
        brevtype: Brevtype,
    ): Boolean {
        return when (referanseType) {
            ReferanseTypeMottaker.SØKNAD -> brevtype == Brevtype.VEDTAK
            ReferanseTypeMottaker.REVURDERING -> brevtype == Brevtype.VEDTAK || brevtype == Brevtype.FORHANDSVARSEL
            ReferanseTypeMottaker.KLAGE -> brevtype == Brevtype.VEDTAK || brevtype == Brevtype.OVERSENDELSE_KA
        }
    }

    /**
     * Alle dokumenter som kun har sakid men ingen annen id kan ikke ha flere mottakere da de er "automatiske"
     * Eller manuelle brev som er opprettet direkte på saken uten annen tilknytning og kan ikke unikt identifiseres
     * hvis vi skal støtte frie brev med flere mottakere mot som feks [lagreOgSendOpplastetPdfPåSak] må man ha en ekstra referanseid på dem
     */
    override fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
        transactionContext: TransactionContext?,
    ): Either<FeilkoderMottaker, MottakerDomain?> {
        if (!erGyldigBrevtype(mottakerIdentifikator.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(ugyldigBrevtypeMelding(mottakerIdentifikator.brevtype.name)).left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(mottakerIdentifikator.referanseType, mottakerIdentifikator.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerIdentifikator.referanseType,
                    brevtype = mottakerIdentifikator.brevtype,
                ),
            ).left()
        }
        val hentetMottaker = mottakerRepo.hentMottaker(mottakerIdentifikator, transactionContext)?.let {
            if (it.sakId != sakId) {
                return FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker.left()
            } else {
                it
            }
        }
        return hentetMottaker.right()
    }

    private fun kanEndreForMottaker(mottaker: MottakerDomain): Boolean {
        return when (mottaker.referanseType) {
            ReferanseTypeMottaker.SØKNAD ->
                !vedtakRepo.finnesVedtakForSøknadsbehandlingId(SøknadsbehandlingId(mottaker.referanseId))

            ReferanseTypeMottaker.REVURDERING ->
                when (mottaker.brevtype) {
                    Brevtype.VEDTAK ->
                        !vedtakRepo.finnesVedtakForRevurderingId(RevurderingId(mottaker.referanseId))

                    Brevtype.FORHANDSVARSEL ->
                        dokumentRepo.hentForRevurdering(mottaker.referanseId).none {
                            it.brevtype == Brevtype.FORHANDSVARSEL
                        }

                    else -> false
                }

            ReferanseTypeMottaker.KLAGE ->
                dokumentRepo.hentForKlage(mottaker.referanseId).isEmpty()
        }
    }

    override fun lagreMottaker(
        mottaker: LagreMottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, MottakerDomain> {
        val mottakerValidert = mottaker.toDomain(sakId).getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it.joinToString(separator = ",")).left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(mottakerValidert.referanseType, mottakerValidert.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerValidert.referanseType,
                    brevtype = mottakerValidert.brevtype,
                ),
            ).left()
        }
        val kanEndre = kanEndreForMottaker(mottakerValidert)
        return if (kanEndre) {
            mottakerRepo.lagreMottaker(mottakerValidert)
            mottakerValidert.right()
        } else {
            FeilkoderMottaker.KanIkkeLagreMottaker.left()
        }
    }

    override fun oppdaterMottaker(
        mottaker: OppdaterMottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        val mottakerValidert = mottaker.toDomain(sakId).getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it.joinToString(separator = ",")).left()
        }
        if (!erGyldigBrevtype(mottakerValidert.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(ugyldigBrevtypeMelding(mottakerValidert.brevtype.name)).left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(mottakerValidert.referanseType, mottakerValidert.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerValidert.referanseType,
                    brevtype = mottakerValidert.brevtype,
                ),
            ).left()
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
        if (!erGyldigBrevtype(mottakerIdentifikator.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(ugyldigBrevtypeMelding(mottakerIdentifikator.brevtype.name)).left()
        }
        if (!erGyldigKombinasjonReferanseTypeOgBrevtype(mottakerIdentifikator.referanseType, mottakerIdentifikator.brevtype)) {
            return FeilkoderMottaker.UgyldigMottakerRequest(
                ugyldigKombinasjonReferanseTypeOgBrevtypeMelding(
                    referanseType = mottakerIdentifikator.referanseType,
                    brevtype = mottakerIdentifikator.brevtype,
                ),
            ).left()
        }

        val mottaker = mottakerRepo.hentMottaker(mottakerIdentifikator)
        return if (mottaker == null) {
            log.info("Fant ikke mottaker for type ${mottakerIdentifikator.referanseType} id: ${mottakerIdentifikator.referanseId} ingenting å slette")
            return Unit.right()
        } else {
            if (mottaker.sakId != sakId) {
                return FeilkoderMottaker.ForespurtSakIdMatcherIkkeMottaker.left()
            }
            val dokument = when (mottaker.referanseType) {
                ReferanseTypeMottaker.SØKNAD ->
                    dokumentRepo.hentForSøknad(mottaker.referanseId)

                ReferanseTypeMottaker.REVURDERING ->
                    dokumentRepo.hentForRevurdering(mottaker.referanseId).filter { dokument ->
                        when (mottaker.brevtype) {
                            Brevtype.VEDTAK -> dokument.brevtype == Brevtype.VEDTAK
                            Brevtype.FORHANDSVARSEL ->
                                dokument.brevtype == Brevtype.FORHANDSVARSEL
                            else -> false
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

    // TILBAKEKREVING,
    KLAGE,
}
