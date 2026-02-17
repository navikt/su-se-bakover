package no.nav.su.se.bakover.service.brev

import arrow.core.getOrElse
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.mottaker.MottakerDokumentkontekst
import no.nav.su.se.bakover.domain.mottaker.MottakerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerOrgnummerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import no.nav.su.se.bakover.domain.mottaker.ReferanseTypeMottaker
import java.util.UUID

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
            brevtype = MottakerDokumentkontekst.VEDTAK,
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
            brevtype = MottakerDokumentkontekst.FORHANDSVARSEL,
        ),
        sakId = sakId,
    )
}

private fun <D : Dokument.MedMetadata> lagreDokumentMedKopiInternal(
    brevService: BrevService,
    mottakerService: MottakerService,
    mottakerIdentifikator: MottakerIdentifikator,
    sakId: UUID,
): (D, TransactionContext) -> Unit = { dokument, tx ->
    fun hentMottaker() = mottakerService.hentMottaker(
        mottakerIdentifikator,
        sakId,
        tx,
    ).getOrElse { null }

    fun identifikatorForMottaker(mottaker: MottakerDomain): String {
        return when (mottaker) {
            is MottakerFnrDomain -> mottaker.foedselsnummer.toString()
            is MottakerOrgnummerDomain -> mottaker.orgnummer
        }
    }

    val kopi =
        when (dokument) {
            is Dokument.MedMetadata.Vedtak -> {
                val mottaker = hentMottaker()
                if (mottaker == null) {
                    null
                } else {
                    dokument.copy(
                        id = UUID.randomUUID(),
                        tittel = dokument.tittel + "(KOPI)",
                        erKopi = true,
                        ekstraMottaker = identifikatorForMottaker(mottaker),
                        navnEkstraMottaker = mottaker.navn,
                        distribueringsadresse = mottaker.adresse,
                    )
                }
            }

            is Dokument.MedMetadata.Informasjon.Viktig -> {
                val mottaker = hentMottaker()
                if (mottaker == null) {
                    null
                } else {
                    dokument.copy(
                        id = UUID.randomUUID(),
                        tittel = dokument.tittel + "(KOPI)",
                        erKopi = true,
                        ekstraMottaker = identifikatorForMottaker(mottaker),
                        navnEkstraMottaker = mottaker.navn,
                        distribueringsadresse = mottaker.adresse,
                    )
                }
            }

            is Dokument.MedMetadata.Informasjon.Annet -> null
        }

    if (kopi != null) {
        brevService.lagreDokument(kopi, tx)
    }

    brevService.lagreDokument(dokument, tx)
}
