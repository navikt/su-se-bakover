package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

interface LagBrevRequest {
    fun getPerson(): Person
    fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold

    data class InnvilgetVedtak(
        private val person: Person,
        private val behandling: Behandling,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.InnvilgetVedtak {
            val beregning = behandling.beregning()!!
            return BrevInnhold.InnvilgetVedtak(
                personalia = personalia,
                fradato = beregning.getPeriode().getFraOgMed().formatMonthYear(),
                tildato = beregning.getPeriode().getTilOgMed().formatMonthYear(),
                sats = beregning.getSats().toString().toLowerCase(),
                satsGrunn = behandling.behandlingsinformasjon().bosituasjon!!.getSatsgrunn(),
                harEktefelle = behandling.behandlingsinformasjon().ektefelle != Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                beregning = LagBrevinnholdForBeregning(beregning).get(),
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            )
        }
    }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
