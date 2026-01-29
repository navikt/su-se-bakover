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
    data class UgyldigMottakerRequest(val feil: List<String>) : FeilkoderMottaker
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
            return FeilkoderMottaker.UgyldigMottakerRequest(it).left()
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
            return FeilkoderMottaker.UgyldigMottakerRequest(it).left()
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

fun String.isUuid(): Boolean =
    try {
        UUID.fromString(this)
        true
    } catch (_: IllegalArgumentException) {
        false
    }

sealed interface MottakerRequest {
    val navn: String
    val foedselsnummer: String
    val adresse: Distribueringsadresse
    val sakId: String
    val referanseId: String
    val referanseType: String
}

data class OppdaterMottaker(
    val id: String,
    override val navn: String,
    override val foedselsnummer: String,
    override val adresse: Distribueringsadresse,
    override val sakId: String,
    override val referanseId: String,
    override val referanseType: String,
) : MottakerRequest {

    private fun erGyldig(): List<String> {
        val feil = mutableListOf<String>()
        if (!id.isUuid()) {
            feil += "MottakerId er ikke en gyldig UUID"
        }
        if (navn.isBlank()) {
            feil += "Navn er blank"
        }

        if (foedselsnummer.isBlank()) {
            feil += "Fødselsnummer er ikke angitt"
        }

        if (Fnr.tryCreate(foedselsnummer) == null) {
            feil += "Ugyldig fødselsnummer"
        }

        if (sakId.isBlank()) {
            feil += "sakId mangler"
        }

        if (referanseId.isBlank()) {
            feil += "referanseId mangler"
        }

        if (!referanseId.isUuid()) {
            feil += "referanseId er ikke en gyldig UUID"
        }

        if (referanseType.isBlank()) {
            feil += "referanseType mangler"
        } else {
            runCatching { ReferanseTypeMottaker.valueOf(referanseType.uppercase()) }
                .getOrElse { feil += "Ugyldig referanseType: $referanseType" }
        }

        return feil
    }

    fun toDomain(): Either<List<String>, MottakerDomain> {
        val erGyldig = this.erGyldig()
        return if (erGyldig.isEmpty()) {
            MottakerDomain(
                id = UUID.fromString(id),
                navn = navn,
                foedselsnummer = Fnr(foedselsnummer),
                adresse = adresse,
                sakId = UUID.fromString(sakId),
                referanseId = UUID.fromString(referanseId),
                referanseType = ReferanseTypeMottaker.valueOf(referanseType),
            ).right()
        } else {
            erGyldig.left()
        }
    }
}

data class LagreMottaker(
    override val navn: String,
    override val foedselsnummer: String,
    override val adresse: Distribueringsadresse,
    override val sakId: String,
    override val referanseId: String,
    override val referanseType: String,
) : MottakerRequest {
    private fun erGyldig(): List<String> {
        val feil = mutableListOf<String>()

        if (navn.isBlank()) {
            feil += "Navn er blank"
        }

        if (foedselsnummer.isBlank()) {
            feil += "Fødselsnummer er ikke angitt"
        }

        if (Fnr.tryCreate(foedselsnummer) == null) {
            feil += "Ugyldig fødselsnummer"
        }

        if (sakId.isBlank()) {
            feil += "sakId mangler"
        }

        if (referanseId.isBlank()) {
            feil += "referanseId mangler"
        }

        if (!referanseId.isUuid()) {
            feil += "referanseId er ikke en gyldig UUID"
        }

        if (referanseType.isBlank()) {
            feil += "referanseType mangler"
        }

        return feil
    }

    fun toDomain(): Either<List<String>, MottakerDomain> {
        val erGyldig = this.erGyldig()
        return if (erGyldig.isEmpty()) {
            MottakerDomain(
                navn = navn,
                foedselsnummer = Fnr(foedselsnummer),
                adresse = adresse,
                sakId = UUID.fromString(sakId),
                referanseId = UUID.fromString(referanseId),
                referanseType = ReferanseTypeMottaker.valueOf(referanseType),
            ).right()
        } else {
            erGyldig.left()
        }
    }
}

class MottakerIdentifikator(
    val referanseType: ReferanseTypeMottaker,
    val referanseId: UUID,
)

data class MottakerDomain(
    val id: UUID = UUID.randomUUID(),
    val navn: String,
    val foedselsnummer: Fnr,
    val adresse: Distribueringsadresse, // toDokDistRequestJson + DokDistAdresseJson
    val sakId: UUID,
    val referanseId: UUID,
    val referanseType: ReferanseTypeMottaker,
)

enum class ReferanseTypeMottaker {
    SØKNAD,
    VEDTAK,
    REVURDERING,
    KLAGE,
}
