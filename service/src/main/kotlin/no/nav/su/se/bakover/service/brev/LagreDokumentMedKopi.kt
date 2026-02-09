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
    if (dokument is Dokument.MedMetadata.Vedtak) {
        val mottaker = mottakerService.hentMottaker(
            mottakerIdentifikator,
            sakId,
            tx,
        ).getOrElse { null }

        if (mottaker != null) {
            val identifikator = when (mottaker) {
                is MottakerFnrDomain -> mottaker.foedselsnummer.toString()
                is MottakerOrgnummerDomain -> mottaker.orgnummer
            }

            val kopi = dokument.copy(
                id = UUID.randomUUID(),
                tittel = dokument.tittel + "(KOPI)",
                erKopi = true,
                ekstraMottaker = identifikator,
                navnEkstraMottaker = mottaker.navn,
                distribueringsadresse = mottaker.adresse,
            )
            brevService.lagreDokument(kopi, tx)
        }
    }
    brevService.lagreDokument(dokument, tx)
}
