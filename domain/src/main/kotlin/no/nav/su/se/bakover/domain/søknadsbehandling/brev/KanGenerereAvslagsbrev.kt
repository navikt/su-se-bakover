@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Søknadsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package no.nav.su.se.bakover.domain.søknadsbehandling

import behandling.søknadsbehandling.domain.avslag.Avslag
import behandling.søknadsbehandling.domain.avslag.ErAvslag
import no.nav.su.se.bakover.common.domain.tid.zoneIdOslo
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.brev.Satsoversikt
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import satser.domain.SatsFactory
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon.Companion.harEPS
import vilkår.common.domain.Avslagsgrunn
import vilkår.formue.domain.firstOrThrowIfMultipleOrEmpty
import vilkår.vurderinger.domain.harForventetInntektStørreEnn0

sealed interface KanGenerereAvslagsbrev :
    KanGenerereBrev,
    ErAvslag {

    override fun lagBrevutkastCommandForSaksbehandler(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Saksbehandler,
        fritekst: String,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekst,
        saksbehandler = utførtAv,
        // I noen tilfeller kan den være underkjent og da viser vi attestant fra attesteringen, selvom den kan endres senere.
        attestant = attesteringer.prøvHentSisteAttestering()?.attestant,
    )

    override fun lagBrevutkastCommandForAttestant(
        satsFactory: SatsFactory,
        utførtAv: NavIdentBruker.Attestant,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekstTilBrev,
        saksbehandler = saksbehandler,
        attestant = utførtAv,
    )

    override fun lagBrevCommand(
        satsFactory: SatsFactory,
    ) = lagDokumentCommand(
        satsFactory = satsFactory,
        fritekst = fritekstTilBrev,
        saksbehandler = saksbehandler,
        attestant = attesteringer.hentSisteIverksatteAttesteringOrNull()!!.attestant,
    )

    private fun lagDokumentCommand(
        satsFactory: SatsFactory,
        saksbehandler: NavIdentBruker.Saksbehandler,
        attestant: NavIdentBruker.Attestant?,
        fritekst: String,
    ): IverksettSøknadsbehandlingDokumentCommand.Avslag {
        val bosituasjon = grunnlagsdata.bosituasjon
        val harEktefelle = grunnlagsdata.bosituasjon.ifNotEmpty { harEPS() } ?: false
        val formuegrunnlag = if (avslagsgrunner.contains(Avslagsgrunn.FORMUE)) {
            vilkårsvurderinger.formue.grunnlag.firstOrThrowIfMultipleOrEmpty()
        } else {
            null
        }

        return IverksettSøknadsbehandlingDokumentCommand.Avslag(
            avslag = Avslag(
                avslagsgrunner = avslagsgrunner,
                harEktefelle = harEktefelle,
                beregning = beregning,
                formuegrunnlag = formuegrunnlag,
                // TODO("håndter_formue egentlig knyttet til formuegrenser")
                halvtGrunnbeløpPerÅr = satsFactory.grunnbeløp(opprettet.toLocalDate(zoneIdOslo))
                    .halvtGrunnbeløpPerÅrAvrundet(),
            ),
            saksbehandler = saksbehandler,
            attestant = attestant,
            forventetInntektStørreEnn0 = vilkårsvurderinger.uføreVilkår()
                .getOrNull()?.grunnlag?.harForventetInntektStørreEnn0(),
            fritekst = fritekst,
            saksnummer = saksnummer,
            fødselsnummer = fnr,
            satsoversikt = Satsoversikt.fra(bosituasjon, satsFactory, sakstype),
            sakstype = sakstype,
        )
    }
}
