package no.nav.su.se.bakover.service.brev

import arrow.core.getOrElse
import dokument.domain.Brevtype
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import dokument.domain.distribuering.Distribueringsadresse
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.mottaker.MottakerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerOrgnummerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import java.util.UUID

private data class MottakerKopiData(
    val identifikator: String,
    val navn: String,
    val adresse: Distribueringsadresse,
)

fun lagreVedtaksbrevMedKopi(
    brevService: BrevService,
    mottakerService: MottakerService,
    referanseType: ReferanseTypeMottaker,
    referanseId: UUID,
    sakId: UUID,
): (Dokument.MedMetadata.Vedtak, TransactionContext) -> Unit {
    return lagreDokumentMedKopiInternal(
        brevService = brevService,
        mottakerService = mottakerService,
        mottakerIdentifikator = MottakerIdentifikator(
            referanseType = referanseType,
            referanseId = referanseId,
            brevtype = Brevtype.VEDTAK,
        ),
        sakId = sakId,
    )
}

fun lagreForhandsvarselMedKopi(
    brevService: BrevService,
    mottakerService: MottakerService,
    referanseType: ReferanseTypeMottaker,
    referanseId: UUID,
    sakId: UUID,
): (Dokument.MedMetadata.Informasjon.Viktig, TransactionContext) -> Unit {
    return lagreDokumentMedKopiInternal(
        brevService = brevService,
        mottakerService = mottakerService,
        mottakerIdentifikator = MottakerIdentifikator(
            referanseType = referanseType,
            referanseId = referanseId,
            brevtype = Brevtype.FORHANDSVARSEL,
        ),
        sakId = sakId,
    )
}

fun lagreKlagebrevMedKopi(
    brevService: BrevService,
    mottakerService: MottakerService,
    referanseId: UUID,
    sakId: UUID,
): (Dokument.MedMetadata, TransactionContext) -> Unit {
    return lagreDokumentMedKopiInternal(
        brevService = brevService,
        mottakerService = mottakerService,
        mottakerIdentifikator = MottakerIdentifikator(
            referanseType = ReferanseTypeMottaker.KLAGE,
            referanseId = referanseId,
            brevtype = Brevtype.OVERSENDELSE_KA,
        ),
        sakId = sakId,
    )
}

private fun lagreDokumentMedKopiInternal(
    brevService: BrevService,
    mottakerService: MottakerService,
    mottakerIdentifikator: MottakerIdentifikator,
    sakId: UUID,
): (Dokument.MedMetadata, TransactionContext) -> Unit = { dokument, tx ->
    fun hentMottaker(): MottakerDomain? {
        return mottakerService.hentMottaker(
            mottakerIdentifikator = mottakerIdentifikator,
            sakId = sakId,
            transactionContext = tx,
        ).getOrElse { null }
    }

    fun identifikatorForMottaker(mottaker: MottakerDomain): String {
        return when (mottaker) {
            is MottakerFnrDomain -> mottaker.foedselsnummer.toString()
            is MottakerOrgnummerDomain -> mottaker.orgnummer
        }
    }

    fun hentMottakerKopiData(): List<MottakerKopiData> {
        return hentMottaker()
            ?.let { mottaker ->
                listOf(
                    MottakerKopiData(
                        identifikator = identifikatorForMottaker(mottaker),
                        navn = mottaker.navn,
                        adresse = mottaker.adresse,
                    ),
                )
            } ?: emptyList()
    }

    fun Dokument.MedMetadata.Vedtak.kopiForMottaker(mottaker: MottakerKopiData): Dokument.MedMetadata.Vedtak {
        return copy(
            id = UUID.randomUUID(),
            tittel = "$tittel (KOPI)",
            erKopi = true,
            ekstraMottaker = mottaker.identifikator,
            navnEkstraMottaker = mottaker.navn,
            distribueringsadresse = mottaker.adresse,
        )
    }

    fun Dokument.MedMetadata.Informasjon.Viktig.kopiForMottaker(
        mottaker: MottakerKopiData,
    ): Dokument.MedMetadata.Informasjon.Viktig {
        return copy(
            id = UUID.randomUUID(),
            tittel = "$tittel (KOPI)",
            erKopi = true,
            ekstraMottaker = mottaker.identifikator,
            navnEkstraMottaker = mottaker.navn,
            distribueringsadresse = mottaker.adresse,
        )
    }

    fun Dokument.MedMetadata.Informasjon.Annet.kopiForMottaker(
        mottaker: MottakerKopiData,
    ): Dokument.MedMetadata.Informasjon.Annet {
        return copy(
            id = UUID.randomUUID(),
            tittel = "$tittel (KOPI)",
            erKopi = true,
            ekstraMottaker = mottaker.identifikator,
            navnEkstraMottaker = mottaker.navn,
            distribueringsadresse = mottaker.adresse,
        )
    }

    fun opprettKopiHvisMottakerFinnes(
        dokument: Dokument.MedMetadata,
    ): List<Dokument.MedMetadata> {
        return hentMottakerKopiData().map { mottaker ->
            when (dokument) {
                is Dokument.MedMetadata.Informasjon.Annet -> dokument.kopiForMottaker(mottaker)
                is Dokument.MedMetadata.Informasjon.Viktig -> dokument.kopiForMottaker(mottaker)
                is Dokument.MedMetadata.Vedtak -> dokument.kopiForMottaker(mottaker)
            }
        }
    }

    val kopier =
        when (dokument) {
            is Dokument.MedMetadata.Vedtak ->
                opprettKopiHvisMottakerFinnes(dokument)

            is Dokument.MedMetadata.Informasjon.Viktig ->
                opprettKopiHvisMottakerFinnes(dokument)

            is Dokument.MedMetadata.Informasjon.Annet ->
                opprettKopiHvisMottakerFinnes(dokument)
        }

    kopier.forEach { kopi ->
        brevService.lagreDokument(kopi, tx)
    }

    brevService.lagreDokument(dokument, tx)
}
