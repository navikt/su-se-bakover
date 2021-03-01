package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

interface LagBrevRequest {
    fun getPerson(): Person
    fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold

    data class InnvilgetVedtak(
        private val person: Person,
        private val beregning: Beregning,
        private val behandlingsinformasjon: Behandlingsinformasjon,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.InnvilgetVedtak {
            return BrevInnhold.InnvilgetVedtak(
                personalia = personalia,
                fradato = beregning.getPeriode().getFraOgMed().formatMonthYear(),
                tildato = beregning.getPeriode().getTilOgMed().formatMonthYear(),
                sats = beregning.getSats().toString().toLowerCase(),
                satsGrunn = behandlingsinformasjon.getSatsgrunn()!!,
                satsBeløp = beregning.getSats().månedsbeløp(beregning.getPeriode().getTilOgMed()),
                harEktefelle = behandlingsinformasjon.ektefelle != Behandlingsinformasjon.EktefellePartnerSamboer.IngenEktefelle,
                beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn
            )
        }
    }

    sealed class Revurdering : LagBrevRequest {
        data class Inntekt(
            private val person: Person,
            private val saksbehandlerNavn: String,
            private val revurdertBeregning: Beregning,
            private val fritekst: String?,
            private val vedtattBeregning: Beregning,
            private val harEktefelle: Boolean,
        ) : Revurdering() {
            override fun getPerson(): Person = person

            override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
                return BrevInnhold.RevurderingAvInntekt(
                    personalia = personalia,
                    saksbehandlerNavn = saksbehandlerNavn,
                    beregningsperioder = LagBrevinnholdForBeregning(revurdertBeregning).brevInnhold,
                    fritekst = fritekst,
                    sats = revurdertBeregning.getSats(),
                    harEktefelle = harEktefelle
                )
            }
        }
    }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
