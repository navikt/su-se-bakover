package no.nav.su.se.bakover.domain.mottaker

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
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
    fun hentMottaker(mottakerIdentifikator: MottakerIdentifikator, sakId: UUID): MottakerDomain?
    fun lagreMottaker(mottaker: Mottaker, sakId: UUID): Either<FeilkoderMottaker, Unit>
    fun oppdaterMottaker(mottaker: Mottaker, sakId: UUID): Either<FeilkoderMottaker, Unit>
    fun slettMottaker(mottakerIdentifikator: MottakerIdentifikator, sakId: UUID)
}

sealed interface FeilkoderMottaker {
    data object KanIkkeLagerMottaker : FeilkoderMottaker
    data class UgyldigMottakerRequest(val feil: List<String>) : FeilkoderMottaker
}

class MottakerServiceImpl(
    private val mottakerRepo: MottakerRepo,
    private val dokumentRepo: DokumentRepo,
) : MottakerService {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    // TODO: Mangler validring her på CRUD operasjoner om tilknyttet dokument eller behandling kan endres på, viktig for å sikre at mottaker blir lagret til ettertiden
    /**
     * Alle dokumenter som kun har sakid men ingen annen id kan ikke ha flere mottakere da de er "automatiske"
     * Eller manuelle brev som er opprettet direkte på saken uten annen tilknytning og kan ikke unikt identifiseres
     * hvis vi skal støtte frie brev med flere mottakere mot man som feks [lagreOgSendOpplastetPdfPåSak] må man ha en ekstra referanseid på dem
     */
    override fun hentMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
    ): MottakerDomain? {
        return mottakerRepo.hentMottaker(mottakerIdentifikator)
    }

    override fun lagreMottaker(
        mottaker: Mottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        val mottakerValidert = mottaker.toDomain().getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it).left()
        }

        // TODO: må sjekke at det ikke finnes et dokument for denne behandlingen i dokumentbasen? holder det?
        when (mottakerValidert.referanseType) {
            ReferanseType.SØKNAD -> TODO()
            ReferanseType.VEDTAK -> TODO()
            ReferanseType.REVURDERING -> TODO()
            ReferanseType.KLAGE -> TODO()
        }

        return if (true) {
            mottakerRepo.lagreMottaker(mottakerValidert).right()
        } else {
            FeilkoderMottaker.KanIkkeLagerMottaker.left()
        }
    }

    override fun oppdaterMottaker(
        mottaker: Mottaker,
        sakId: UUID,
    ): Either<FeilkoderMottaker, Unit> {
        val mottakerValidert = mottaker.toDomain().getOrElse {
            return FeilkoderMottaker.UgyldigMottakerRequest(it).left()
        }
        // TODO: må sjekke samme som over i lagreMottaker.....
        return mottakerRepo.oppdaterMottaker(mottakerValidert).right()
    }

    override fun slettMottaker(
        mottakerIdentifikator: MottakerIdentifikator,
        sakId: UUID,
    ) {
        val mottaker = mottakerRepo.hentMottaker(mottakerIdentifikator)
        if (mottaker == null) {
            log.info("Fant ikke mottaker for type ${mottakerIdentifikator.referanseType} id: ${mottakerIdentifikator.referanseId} ingenting å slette")
            return
        } else {
            log.info("Sletter mottaker med id: ${mottaker.id} sakid ${mottaker.sakId} type ${mottaker.referanseType} id: ${mottaker.referanseId}")
            mottakerRepo.slettMottaker(mottaker.id)
        }
    }
}

data class Mottaker(
    val id: String?,
    val navn: String,
    val foedselsnummer: String,
    val adresse: Distribueringsadresse,
    val sakId: String,
    val referanseId: String,
    val referanseType: String,
    val dokumentId: String? = null,
) {
    private fun erGyldig(): List<String> {
        val feil = mutableListOf<String>()

        if (navn.isBlank()) {
            feil += "Navn er blank"
        }

        if (foedselsnummer.isBlank()) {
            feil += "Fødselsnummer er ikke angitt"
        }

        if (sakId.isBlank()) {
            feil += "sakId mangler"
        }

        if (referanseId.isBlank()) {
            feil += "referanseId mangler"
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
                id = UUID.fromString(id),
                navn = navn,
                foedselsnummer = Fnr(foedselsnummer),
                adresse = adresse,
                dokumentId = UUID.fromString(dokumentId),
                sakId = UUID.fromString(sakId),
                referanseId = UUID.fromString(referanseId),
                referanseType = ReferanseType.valueOf(referanseType),
            ).right()
        } else {
            erGyldig.left()
        }
    }
}

class MottakerIdentifikator(
    val referanseType: ReferanseType,
    val referanseId: UUID,
)

data class MottakerDomain(
    val id: UUID = UUID.randomUUID(),
    val navn: String,
    val foedselsnummer: Fnr,
    val adresse: Distribueringsadresse, // toDokDistRequestJson + DokDistAdresseJson
    val sakId: UUID,
    val referanseId: UUID,
    val referanseType: ReferanseType,
    val dokumentId: UUID? = null,
)

enum class ReferanseType {
    SØKNAD,
    VEDTAK,
    REVURDERING,
    KLAGE,
}
