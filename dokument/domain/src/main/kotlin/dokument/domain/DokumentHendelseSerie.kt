package dokument.domain

import arrow.core.NonEmptyList
import dokument.domain.brev.BrevbestillingId
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.hendelse.domain.HendelseFil
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

/**
 * En serie med dokumenter vil ha opptil 3 tilstander i rekkefølge: Generert, Journalført og Distribuert.
 */
data class DokumentHendelseSerie(
    val sakId: UUID,
    val dokumenter: NonEmptyList<DokumentHendelse>,
) : List<DokumentHendelse> by dokumenter {
    /** Dette vil være hendelsen som førte til dokumentgenereringen og denne dokumenthendelsesserien*/
    val relatertHendelse = dokumenter[0].relatertHendelse

    val dokumentId: UUID = generertDokument().dokumentUtenFil.id

    fun leggTilHendelse(hendelse: DokumentHendelse): DokumentHendelseSerie {
        return DokumentHendelseSerie(sakId, dokumenter + hendelse)
    }

    fun dokumenttilstand(): Dokumenttilstand {
        return when (val dokument = this.dokumenter.lastOrNull()) {
            is GenerertDokumentHendelse -> {
                if (dokument.skalSendeBrev) {
                    Dokumenttilstand.GENERERT
                } else {
                    Dokumenttilstand.SKAL_IKKE_GENERERE
                }
            }

            is JournalførtDokumentHendelse -> Dokumenttilstand.JOURNALFØRT
            is DistribuertDokumentHendelse -> Dokumenttilstand.SENDT
        }
    }

    /**
     * Legger på journalpostId og brevbestillingId dersom disse finnes.
     */
    fun tilDokumentMedMetadata(
        hentDokumentForHendelseId: (HendelseId) -> HendelseFil?,
    ): Dokument.MedMetadata {
        val generertDokumentHendelse = generertDokument()
        val fil = hentDokumentForHendelseId(generertDokumentHendelse.hendelseId)!!
        return generertDokumentHendelse.dokumentUtenFil.toDokumentMedMetadata(
            pdf = fil.fil,
            journalpostId = journalpostIdOrNull(),
            brevbestillingId = brevbestillingIdOrNull(),
        )
    }

    fun generertDokument(): GenerertDokumentHendelse {
        // init garanterer element 0 er GenerertDokumentHendelse
        return dokumenter.first() as GenerertDokumentHendelse
    }

    fun journalpostIdOrNull(): JournalpostId? {
        return journalpostHendelseOrNull()?.journalpostId
    }

    fun harJournalført(): Boolean = journalpostHendelseOrNull() != null

    fun journalpostHendelseOrNull(): JournalførtDokumentHendelse? {
        // init garanterer at et evt. element 1 er JournalførtDokumentHendelse
        return dokumenter.getOrNull(1) as JournalførtDokumentHendelse?
    }

    fun brevbestillingIdOrNull(): BrevbestillingId? {
        return distribuertDokumentHendelse()?.brevbestillingId
    }

    fun harBestiltBrev(): Boolean = distribuertDokumentHendelse() != null

    fun distribuertDokumentHendelse(): DistribuertDokumentHendelse? {
        // init garanterer at et evt. element 2 er DistribuertDokumentHendelse
        return dokumenter.getOrNull(2) as DistribuertDokumentHendelse?
    }

    fun hentDokumentIdForJournalpostId(journalpostId: JournalpostId): UUID? {
        return journalpostHendelseOrNull()?.let {
            if (it.journalpostId == journalpostId) {
                dokumentId
            } else {
                null
            }
        }
    }

    init {
        dokumenter.map { it.sakId }.distinct().let {
            require(listOf(sakId) == it) {
                "Forventer at alle dokumenter er relatert til samme sak. Forventet $sakId, men var $it}"
            }
        }
        dokumenter.zipWithNext { a, b ->
            require(a.versjon < b.versjon) {
                "Forventer at dokumenter er sortert etter versjon. Var ${a.versjon} og ${b.versjon}"
            }
        }
        dokumenter.zipWithNext { a, b ->
            require(a.hendelseId == b.relatertHendelse) {
                "Forventer at neste dokument, peker på forrige, men var aId: ${a.hendelseId}, aRelatertHendelse: ${a.relatertHendelse} og bId: ${b.hendelseId}, bRelatertHendelse: ${b.relatertHendelse}"
            }
        }
        if (dokumenter.isNotEmpty()) {
            require(dokumenter[0] is GenerertDokumentHendelse) {
                "Forventer at første dokument er generert. Var ${dokumenter[0]}"
            }
        }
        if (dokumenter.size > 1) {
            require(dokumenter[1] is JournalførtDokumentHendelse) {
                "Forventer at det andre dokumentet er Journalført. Var ${dokumenter[1]}"
            }
        }
        if (dokumenter.size > 2) {
            require(dokumenter[2] is DistribuertDokumentHendelse) {
                "Forventer at det siste dokumenter er distribuert. Var ${dokumenter[2]}"
            }
        }
        require(dokumenter.size <= 3) {
            "Forventer at det er maks 3 dokumenter i serien. Var ${dokumenter.size}"
        }
    }
}
