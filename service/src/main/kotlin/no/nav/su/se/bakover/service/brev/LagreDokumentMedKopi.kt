package no.nav.su.se.bakover.service.brev

import arrow.core.getOrElse
import dokument.domain.Dokument
import dokument.domain.brev.BrevService
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.mottaker.MottakerFnrDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerIdentifikator
import no.nav.su.se.bakover.domain.mottaker.MottakerOrgnummerDomain
import no.nav.su.se.bakover.domain.mottaker.MottakerService
import java.util.UUID

fun lagreDokumentMedKopi(
    brevService: BrevService,
    mottakerService: MottakerService,
    mottakerIdentifikator: MottakerIdentifikator,
    sakId: UUID,
): (Dokument.MedMetadata, TransactionContext) -> Unit = { dokument, tx ->
    fun hentMottaker() = mottakerService.hentMottaker(
        mottakerIdentifikator,
        sakId,
        tx,
    ).getOrElse { null }

    fun identifikatorForMottaker(mottaker: no.nav.su.se.bakover.domain.mottaker.MottakerDomain): String {
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
