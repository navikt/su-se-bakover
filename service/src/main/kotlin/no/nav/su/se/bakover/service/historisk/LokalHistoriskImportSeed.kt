package no.nav.su.se.bakover.service.historisk

import no.nav.su.se.bakover.domain.historisk.HistoriskImportRepo
import no.nav.su.se.bakover.domain.historisk.InfotrygdTabeller
import no.nav.su.se.bakover.domain.historisk.NyHistoriskTabellimport
import no.nav.su.se.bakover.domain.historisk.aldersvedtak.HistoriskAlderProjeksjonRepo
import java.util.UUID

/**
 * Oppretter lokal importhistorikk og en ferdig projeksjon uten å kjøre Infotrygd-konverteringen.
 */
object LokalHistoriskImportSeed {
    fun seed(
        historiskImportRepo: HistoriskImportRepo,
        historiskAlderProjeksjonRepo: HistoriskAlderProjeksjonRepo,
    ) {
        val importId = seedImporter(historiskImportRepo)
        val stønader = LokalHistoriskAlderSeedData.stønader
        val projeksjonId = historiskAlderProjeksjonRepo.startProjeksjon(importId)

        historiskAlderProjeksjonRepo.lagreBatch(
            projeksjonId = projeksjonId,
            importId = importId,
            stønader = stønader,
        )
        historiskAlderProjeksjonRepo.fullførProjeksjon(
            projeksjonId = projeksjonId,
            antallStønader = stønader.size,
        )
    }

    internal fun seedImporter(historiskImportRepo: HistoriskImportRepo): UUID {
        historiskImportRepo.hentPågåendeImport()?.let {
            historiskImportRepo.markerFeilet(it.id, "Seed reset ved oppstart")
        }
        historiskImportRepo.hentAlleImporter().forEach { historiskImportRepo.slettImport(it.id) }

        val tabeller = listOf(
            NyHistoriskTabellimport(
                tabellnavn = InfotrygdTabeller.T_STONAD,
                forventetAntall = 0L,
                kolonner = listOf("STONAD_ID", "PERSON_LOPENR"),
            ),
        )
        val import = historiskImportRepo.opprettImport(tabeller)
        historiskImportRepo.fullførImport(import.id)

        val feiletTabeller = listOf(
            NyHistoriskTabellimport(
                tabellnavn = InfotrygdTabeller.T_STONAD,
                forventetAntall = 0L,
                kolonner = listOf("STONAD_ID", "PERSON_LOPENR"),
            ),
        )
        val feiletImport = historiskImportRepo.opprettImport(feiletTabeller)
        historiskImportRepo.markerFeilet(
            feiletImport.id,
            "Antallsavvik(tabellnavn=T_STONAD, forventet=0, faktisk=1)",
        )

        return import.id
    }
}
