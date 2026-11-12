package no.nav.su.se.bakover.domain.oppgave

import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstema
import no.nav.su.se.bakover.common.domain.kodeverk.Behandlingstype
import no.nav.su.se.bakover.common.domain.kodeverk.Tema
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.idagOslo
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import java.time.Clock
import java.time.LocalDate

data class OppgaveV2Data(
    val beskrivelse: String,
    val kategorisering: Kategorisering,
    val bruker: Bruker? = null,
    val aktivDato: LocalDate? = null,
    val fristDato: LocalDate? = null,
    val prioritet: Prioritet? = null,
    val fordeling: Fordeling? = null,
    val nokkelord: Set<String> = emptySet(),
    val arkivreferanse: Arkivreferanse? = null,
    val tilknyttetSystem: String? = null,
    val meta: Meta? = null,
) {
    init {
        require(bruker != null || arkivreferanse != null) {
            "Minst en av bruker eller arkivreferanse må være satt"
        }
        requireGyldigFritekst("beskrivelse", beskrivelse)
        meta?.kommentar?.let { requireGyldigFritekst("meta.kommentar", it) }
        arkivreferanse?.let {
            require(it.saksnr != null || it.journalpostId != null) {
                "Arkivreferanse må inneholde minst saksnr eller journalpostId"
            }
        }
    }

    data class Kategorisering(
        val tema: Kode,
        val oppgavetype: Kode,
        val behandlingstema: Kode? = null,
        val behandlingstype: Kode? = null,
    )

    data class Kode(
        val kode: String,
    )

    data class Bruker(
        val ident: String,
        val type: Type,
    ) {
        enum class Type {
            PERSON,
            ARBEIDSGIVER,
            SAMHANDLER,
        }
    }

    data class Fordeling(
        val enhet: Enhet,
        val mappe: Mappe? = null,
        val medarbeider: Medarbeider? = null,
    ) {
        data class Enhet(
            val nr: String,
        )

        data class Mappe(
            val id: Long,
        )

        data class Medarbeider(
            val navident: String,
        )
    }

    data class Arkivreferanse(
        val saksnr: String? = null,
        val journalpostId: String? = null,
    )

    data class Meta(
        val kommentar: String? = null,
    )

    enum class Prioritet {
        NORMAL,
        HOY,
        LAV,
        KRITISK,
    }

    companion object {
        fun påminnelseOmEPSFyller67År(
            saksnummer: Saksnummer,
            bursdagEps: LocalDate,
            fnr: Fnr,
            clock: Clock,
            sakstype: Sakstype,
            journalpostId: JournalpostId? = null,
        ): OppgaveV2Data {
            val idag = idagOslo(clock)
            val aktiveringsdato = bursdagEps.minusDays(60)
            val fristdato = bursdagEps.minusDays(30)
            val behandlingstema = when (sakstype) {
                Sakstype.ALDER -> Behandlingstema.SU_ALDER
                Sakstype.UFØRE -> Behandlingstema.SU_UFØRE_FLYKTNING
            }
            return OppgaveV2Data(
                beskrivelse = " Saksnummer : $saksnummer" +
                    "\nBrukerens ektefelle eller samboer fyller 67 år den $bursdagEps. Vurder om brukerens og evenftuelt ektefellens eller samboerens sak må revurderes.",
                kategorisering = Kategorisering(
                    tema = Kode(Tema.SUPPLERENDE_STØNAD.value),
                    oppgavetype = Kode(Oppgavetype.FREMLEGGING.value),
                    behandlingstema = Kode(behandlingstema.value),
                    behandlingstype = Kode(Behandlingstype.REVURDERING.value),
                ),
                bruker = Bruker(fnr.toString(), Bruker.Type.PERSON),
                aktivDato = if (aktiveringsdato.isBefore(idag)) idag else aktiveringsdato,
                fristDato = if (fristdato.isBefore(idag)) idag else fristdato,
                prioritet = Prioritet.NORMAL,
                fordeling = Fordeling(enhet = Fordeling.Enhet("4815")),
                arkivreferanse = Arkivreferanse(saksnummer.toString(), journalpostId?.toString()),
            )
        }
    }

    private fun requireGyldigFritekst(feltnavn: String, verdi: String) {
        require(verdi.length in 2..2500) {
            "$feltnavn må være mellom 2 og 2500 tegn"
        }
        require(!verdi.contains("---")) {
            "$feltnavn kan ikke inneholde ---"
        }
    }
}
