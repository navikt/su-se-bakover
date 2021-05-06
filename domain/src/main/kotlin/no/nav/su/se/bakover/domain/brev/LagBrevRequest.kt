package no.nav.su.se.bakover.domain.brev

import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.behandling.AvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.VurderAvslagGrunnetBeregning
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
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
                fradato = beregning.periode.fraOgMed.formatMonthYear(),
                tildato = beregning.periode.tilOgMed.formatMonthYear(),
                // TODO CHM 05.05.2021: Wrap sats-tingene i et eget objekt, hent fra beregning?
                sats = beregning.getSats().toString().toLowerCase(),
                satsGrunn = behandlingsinformasjon.getSatsgrunn().orNull()!!,
                satsBeløp = beregning.getSats().månedsbeløpSomHeltall(beregning.periode.tilOgMed),
                satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed).ddMMyyyy(),
                harEktefelle = behandlingsinformasjon.harEktefelle(),
                beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = fritekst,
            )
        }
    }

    data class AvslagBrevRequest(
        private val person: Person,
        private val avslag: Avslag,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val fritekst: String
    ) : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold.AvslagsBrevInnhold {
            return BrevInnhold.AvslagsBrevInnhold(
                personalia = personalia,
                avslagsgrunner = avslag.avslagsgrunner,
                harEktefelle = avslag.harEktefelle,
                halvGrunnbeløp = avslag.halvGrunnbeløp.toInt(),
                beregningsperioder = avslag.beregning?.let { LagBrevinnholdForBeregning(it).brevInnhold } ?: emptyList(),
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                sats = avslag.beregning?.getSats()?.name?.toLowerCase(),
                satsGjeldendeFraDato = avslag.beregning?.getSats()?.datoForSisteEndringAvSats(avslag.beregning.periode.tilOgMed)?.ddMMyyyy(),
                fritekst = fritekst
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
                satsBeløp = beregning.getSats().månedsbeløpSomHeltall(beregning.periode.tilOgMed),
                satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed).ddMMyyyy(),
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

    data class VedtakIngenEndring(
        private val person: Person,
        private val saksbehandlerNavn: String,
        private val attestantNavn: String,
        private val beregning: Beregning,
        private val fritekst: String,
        private val harEktefelle: Boolean,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person

        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
            return BrevInnhold.VedtakIngenEndring(
                personalia = personalia,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold,
                fritekst = fritekst,
                sats = beregning.getSats(),
                satsGjeldendeFraDato = beregning.getSats().datoForSisteEndringAvSats(beregning.periode.tilOgMed).ddMMyyyy(),
                harEktefelle = harEktefelle,
            )
        }
    }

    data class Forhåndsvarsel(
        private val person: Person,
        private val fritekst: String,
    ) : LagBrevRequest {
        override fun getPerson(): Person = person

        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia): BrevInnhold {
            return BrevInnhold.Forhåndsvarsel(
                personalia = personalia,
                fritekst = fritekst,
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
                    satsGjeldendeFraDato = revurdertBeregning.getSats().datoForSisteEndringAvSats(revurdertBeregning.periode.tilOgMed).ddMMyyyy(),
                    harEktefelle = harEktefelle,
                )
            }
        }
    }
}

// TODO Hente Locale fra brukerens målform
fun LocalDate.formatMonthYear(): String =
    this.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("nb-NO")))
