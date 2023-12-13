package dokument.domain

import arrow.core.NonEmptyList
import dokument.domain.hendelser.DistribuertDokumentHendelse
import dokument.domain.hendelser.DokumentHendelse
import dokument.domain.hendelser.GenerertDokumentHendelse
import dokument.domain.hendelser.JournalførtDokumentHendelse
import java.util.UUID

data class DokumentHendelseSerie(
    val sakId: UUID,
    val dokumenter: NonEmptyList<DokumentHendelse>,
) : List<DokumentHendelse> by dokumenter {
    /** Dette vil være hendelsen som første til dokumentgenereringen og denne dokumenthendelsesserien*/
    val relatertHendelse = dokumenter[0].relatertHendelse

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
