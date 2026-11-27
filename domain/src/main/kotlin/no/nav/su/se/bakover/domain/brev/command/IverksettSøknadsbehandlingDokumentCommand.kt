package no.nav.su.se.bakover.domain.brev.command

import beregning.domain.Beregning
import dokument.domain.GenererDokumentCommand
import dokument.domain.generer.DokumentMal
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.harFradrag
import dokument.domain.generer.bygger.maler.InnvilgetSøknadsbehandlingPdfInnhold
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.beregning.LagBrevinnholdForBeregning
import no.nav.su.se.bakover.domain.brev.toBrev

sealed interface IverksettSøknadsbehandlingDokumentCommand : GenererDokumentCommand {
    /**
     * Når vi innvilger en søknad, så sender vi et enkeltvedtak.
     */
    data class Innvilgelse(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        val beregning: Beregning,
        val harEktefelle: Boolean,
        val forventetInntektStørreEnn0: Boolean,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val attestant: NavIdentBruker.Attestant?,
        val satsoversikt: Satsoversikt,
        val fritekst: String,
    ) : IverksettSøknadsbehandlingDokumentCommand {
        override val dokumentMal = DokumentMal.Søknadsbehandling.InnvilgetVedtak

        fun tilPdfInnhold(
            personalia: PersonaliaPdfInnhold,
            saksbehandlerNavn: String,
            attestantNavn: String,
        ): InnvilgetSøknadsbehandlingPdfInnhold {
            val tildato = beregning.periode.tilOgMed
            val beregningsperioder = LagBrevinnholdForBeregning(beregning).brevInnhold
            return InnvilgetSøknadsbehandlingPdfInnhold(
                personalia = personalia,
                fradato = beregning.periode.fraOgMed.formatMonthYear(),
                tildato = tildato.formatMonthYear(),
                søkedato = tildato.minusMonths(1).formatMonthYear(),
                forventetInntektStørreEnn0 = forventetInntektStørreEnn0,
                harEktefelle = harEktefelle,
                beregningsperioder = beregningsperioder,
                saksbehandlerNavn = saksbehandlerNavn,
                attestantNavn = attestantNavn,
                fritekst = fritekst,
                satsoversikt = satsoversikt.toBrev(),
                harFradrag = beregningsperioder.harFradrag(),
                sakstype = sakstype,
            )
        }
    }

    /**
     * Når vi avslår en søknad, så sender vi et enkeltvedtak.
     */
    data class Avslag(
        override val fødselsnummer: Fnr,
        override val saksnummer: Saksnummer,
        override val sakstype: Sakstype,
        val avslag: behandling.søknadsbehandling.domain.avslag.Avslag,
        val saksbehandler: NavIdentBruker.Saksbehandler,
        val attestant: NavIdentBruker.Attestant?,
        // Kun relevant for uføre
        val forventetInntektStørreEnn0: Boolean?,
        val satsoversikt: Satsoversikt?,
        val fritekst: String,
    ) : IverksettSøknadsbehandlingDokumentCommand
}
