package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
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
        private val attestantNavn: String,
        private val fritekst: String,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.InnvilgetVedtak {
            return BrevInnhold.InnvilgetVedtak(
                personalia = personalia,
                fradato = beregning.getPeriode().getFraOgMed().formatMonthYear(),
                tildato = beregning.getPeriode().getTilOgMed().formatMonthYear(),
                sats = beregning.getSats().toString().toLowerCase(),
                // TODO jah: Vi burde sannsynligvis ikke ta inn BehandlingsInformasjon direkte her. Da kan vi heller validere ting lenger ut.
                satsGrunn = behandlingsinformasjon.getSatsgrunn().orNull()!!,
                satsBeløp = beregning.getSats().månedsbeløp(beregning.getPeriode().getTilOgMed()),
                harEktefelle = behandlingsinformasjon.harEktefelle(),
                beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = fritekst,
            )
        }
    }

    data class Opphørsvedtak(
        private val person: Person,
        private val beregning: Beregning,
        private val behandlingsinformasjon: Behandlingsinformasjon,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.Opphørsvedtak {
            val avslagsgrunn = VurderAvslagGrunnetBeregning.vurderAvslagGrunnetBeregning(beregning).let {
                when (it) {
                    is AvslagGrunnetBeregning.Ja -> listOf(it.avslagsgrunn)
                    is AvslagGrunnetBeregning.Nei -> throw IllegalStateException("Skal aldri havne på nei her")
                }
            }

            return BrevInnhold.Opphørsvedtak(
                personalia = personalia,
                sats = beregning.getSats().toString().toLowerCase(),
                satsBeløp = beregning.getSats().månedsbeløp(beregning.getPeriode().getTilOgMed()),
                harEktefelle = behandlingsinformasjon.harEktefelle(),
                beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = fritekst,
                avslagsgrunner = avslagsgrunn,
                avslagsparagrafer = avslagsgrunn.getDistinkteParagrafer(),
            )
        }
    }

    sealed class Revurdering : LagBrevRequest {
        data class Inntekt(
            private val person: Person,
            private val saksbehandlerNavn: String,
            private val attestantNavn: String,
            private val revurdertBeregning: Beregning,
            private val fritekst: String,
            private val harEktefelle: Boolean,
        ) : Revurdering() {
            override fun getPerson(): Person = person

            override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
                return BrevInnhold.RevurderingAvInntekt(
                    personalia = personalia,
                    saksbehandlerNavn = saksbehandlerNavn,
                    attestantNavn = attestantNavn,
                    beregningsperioder = LagBrevinnholdForBeregning(revurdertBeregning).brevInnhold,
                    fritekst = fritekst,
                    sats = revurdertBeregning.getSats(),
                    harEktefelle = harEktefelle,
                )
            }
        }
    }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
