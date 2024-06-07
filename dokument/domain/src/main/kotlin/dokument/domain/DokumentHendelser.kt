package dokument.domain

import arrow.core.nonEmptyListOf
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import no.nav.su.se.bakover.common.domain.extensions.singleOrNullOrThrow
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

/**
 * Forventer at alle dokumenter i serien er relatert til samme sak.
 */
data class DokumentHendelser(
    val sakId: UUID,
    val serier: List<DokumentHendelseSerie>,
) : List<DokumentHendelseSerie> by serier {

    init {
        serier.map { it.sakId }.distinct().let {
            if (it.isNotEmpty()) {
                require(listOf(sakId) == it) {
                    "Forventer at alle dokumenter er relatert til samme sak. Forventet $sakId, men var $it}"
                }
            }
        }
    }

    fun hentSerieForDokumentId(dokumentId: UUID): DokumentHendelseSerie? {
        return serier.singleOrNullOrThrow {
            it.dokumentId == dokumentId
        }
    }

    fun hentSerieForRelatertHendelse(hendelseId: HendelseId): DokumentHendelseSerie? {
        return serier.singleOrNullOrThrow {
            it.dokumenter.map { it.relatertHendelse }.contains(hendelseId)
        }
    }

    fun hentSerieForHendelseId(hendelseId: HendelseId): DokumentHendelseSerie? {
        return serier.singleOrNullOrThrow {
            it.dokumenter.map { it.hendelseId }.contains(hendelseId)
        }
    }

    fun hentDokumentIdForJournalpostId(journalpostId: JournalpostId): UUID? {
        return serier.mapNotNull { it.hentDokumentIdForJournalpostId(journalpostId) }.singleOrNull()
    }

    fun hentGenererte(): List<GenerertDokumentHendelse> {
        return serier.flatMap { it.dokumenter }.filterIsInstance<GenerertDokumentHendelse>()
    }

    fun tilDokumenterMedMetadata(
        hentDokumentForHendelseId: (HendelseId) -> HendelseFil?,
    ): List<Dokument.MedMetadata> {
        return serier.map {
            it.tilDokumentMedMetadata(
                hentDokumentForHendelseId = hentDokumentForHendelseId,
            )
        }
    }

    companion object {
        fun empty(sakId: UUID) = DokumentHendelser(sakId = sakId, serier = emptyList())

        fun create(sakId: UUID, dokumenter: List<DokumentHendelse>): DokumentHendelser {
            dokumenter.map { it.sakId }.distinct().let {
                if (it.isNotEmpty()) {
                    require(listOf(sakId) == it) {
                        "Forventer at alle dokumenter er relatert til samme sak. Forventet $sakId, men var $it}"
                    }
                }
            }
            val sorterteDokumenter = dokumenter.sortedBy { it.versjon }

            return sorterteDokumenter.fold(mapOf<HendelseId, DokumentHendelseSerie>()) { acc, hendelse ->
                val hendelseId = hendelse.hendelseId
                when (hendelse) {
                    is GenerertDokumentHendelse -> acc.plus(
                        hendelseId to DokumentHendelseSerie(
                            sakId = sakId,
                            dokumenter = nonEmptyListOf(hendelse),
                        ),
                    )

                    is JournalførtDokumentHendelse -> acc.plus(
                        hendelseId to acc[hendelse.relatertHendelse]!!.leggTilHendelse(hendelse),
                    ).minus(hendelse.relatertHendelse)

                    is DistribuertDokumentHendelse -> acc.plus(
                        hendelseId to acc[hendelse.relatertHendelse]!!.leggTilHendelse(hendelse),
                    ).minus(hendelse.relatertHendelse)
                }
            }.values.toList().let {
                DokumentHendelser(sakId = sakId, serier = it)
            }
        }
    }
}
