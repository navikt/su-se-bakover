package no.nav.su.se.bakover.domain.mottaker

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.person.Fnr
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

interface MottakerRepo {
    fun hentMottaker(dokumentId: UUID, sessionContext: SessionContext? = null): MottakerDomain?
    fun lagreMottaker(mottaker: MottakerDomain, dokumentId: UUID)
    fun oppdaterMottaker(mottaker: MottakerDomain, dokumentId: UUID)
    fun slettMottaker(mottakerId: UUID, dokumentId: UUID)
}

interface MottakerService {
    fun hentMottaker(dokumentId: UUID, sessionContext: SessionContext? = null): MottakerDomain?
    fun lagreMottaker(mottaker: Mottaker, dokumentId: UUID)
    fun oppdaterMottaker(mottaker: Mottaker, dokumentId: UUID)
    fun slettMottaker(
        mottakerId: UUID,
        dokumentId: UUID,
    )
}

class MottakerServiceImpl(
    private val mottakerRepo: MottakerRepo,
) : MottakerService {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    // TODO: Mangler validring her på CRUD operasjoner om tilknyttet dokument eller behandling kan endres på, viktig for å sikre at mottaker blir lagret til ettertiden
    override fun hentMottaker(
        dokumentId: UUID,
        sessionContext: SessionContext?,
    ): MottakerDomain? =
        mottakerRepo.hentMottaker(dokumentId, sessionContext)

    override fun lagreMottaker(
        mottaker: Mottaker,
        dokumentId: UUID,
    ) {
        val mottakerValidert = mottaker.toDomain().getOrElse { return }
        // TODO: sjekke at det ikke finnes en på dokumentet allerede
        mottakerRepo.lagreMottaker(mottakerValidert, dokumentId)
    }

    override fun oppdaterMottaker(
        mottaker: Mottaker,
        dokumentId: UUID,
    ) {
        val mottakerValidert = mottaker.toDomain().getOrElse { return }

        mottakerRepo.oppdaterMottaker(mottakerValidert, dokumentId)
    }

    override fun slettMottaker(
        mottakerId: UUID,
        dokumentId: UUID,
    ) {
        val mottaker = mottakerRepo.hentMottaker(dokumentId)
        if (mottaker == null) {
            log.info("Fant ikke mottaker for dokumentId=$dokumentId ingenting å slette")
            return
        } else {
            mottakerRepo.slettMottaker(mottakerId, dokumentId)
        }
    }
}

data class Mottaker(
    val id: String,
    val navn: String,
    val foedselsnummer: String,
    val adresse: Adresse,
    val dokumentId: String,
) {
    private fun erGyldig(): List<String> =
        if (navn.isBlank()) {
            listOf("Navn er blank")
        } else if (foedselsnummer.isBlank()) {
            listOf("Fødselsnummer er ikke angitt")
        } else {
            val erGyldig = adresse.erGyldig()
            erGyldig
        }

    fun toDomain(): Either<List<String>, MottakerDomain> {
        val erGyldig = this.erGyldig()
        if (erGyldig.isEmpty()) {
            return MottakerDomain(
                id = UUID.fromString(id),
                navn = navn,
                foedselsnummer = Fnr(foedselsnummer),
                adresse = adresse,
                dokumentId = UUID.fromString(dokumentId),
            ).right()
        } else {
            return erGyldig.left()
        }
    }
}

data class MottakerDomain(
    val id: UUID = UUID.randomUUID(),
    val navn: String,
    val foedselsnummer: Fnr,
    val adresse: Adresse,
    val dokumentId: UUID,
)

data class Adresse(
    val adresseType: String, // TODO: må være PDL type? se PdlAdresseformat
    val adresselinje1: String? = null,
    val adresselinje2: String? = null,
    val adresselinje3: String? = null,
    val postnummer: String? = null,
    val poststed: String? = null,
    val landkode: String,
    val land: String,
) {
    fun erGyldig(): List<String> =
        if (adresseType.isBlank() || landkode.isBlank() || land.isBlank()) {
            listOf(
                "Adressetype ($adresseType), landkode ($landkode) eller land ($land) er blank. Sjekk om det er verge i saken. Da vet vi ikke hvor brevet skal.",
            )
        } else if (adresseType == "NORSKPOSTADRESSE") {
            if (!(postnummer.isNullOrBlank() || poststed.isNullOrBlank())) {
                emptyList()
            } else {
                listOf("Postnummer eller poststed er ikke angitt")
            }
        } else if (adresseType == "UTENLANDSKPOSTADRESSE") {
            if (adresselinje1.isNullOrBlank()) {
                listOf("Adresselinje1 er ikke angitt")
            } else if (!postnummer.isNullOrBlank() || !poststed.isNullOrBlank()) {
                listOf("Postnummer og poststed skal ikke brukes på utenlandsk adresse")
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
}
