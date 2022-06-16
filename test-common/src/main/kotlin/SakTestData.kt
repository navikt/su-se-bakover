package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.util.UUID

val sakId: UUID = UUID.randomUUID()

fun Sak.hentGjeldendeVilkårOgGrunnlag(
    periode: Periode,
    clock: Clock,
): GrunnlagsdataOgVilkårsvurderinger.Revurdering {
    return hentGjeldendeVedtaksdata(
        periode = periode,
        clock = clock,
    ).fold(
        {
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                Grunnlagsdata.IkkeVurdert,
                Vilkårsvurderinger.Revurdering.Uføre.ikkeVurdert(),
            )
        },
        {
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = it.grunnlagsdata,
                vilkårsvurderinger = it.vilkårsvurderinger,
            )
        },
    )
}

fun nySakUføre(
    clock: Clock = fixedClock,
    søknadsInnhold: SøknadInnhold = søknadinnhold(fnr),
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return nySak(
        clock = clock,
        søknadsinnhold = søknadsInnhold,
    )
}

fun nySakAlder(
    clock: Clock = fixedClock,
    søknadsInnhold: SøknadInnhold = søknadsinnholdAlder(),
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return nySak(
        clock = clock,
        søknadsinnhold = søknadsInnhold,
    )
}

fun nySak(
    clock: Clock = fixedClock,
    søknadsinnhold: SøknadInnhold,
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return SakFactory(
        clock = clock,
    ).let { sakFactory ->
        sakFactory.nySakMedNySøknad(
            fnr = søknadsinnhold.personopplysninger.fnr,
            søknadInnhold = søknadsinnhold,
        ).let {
            val søknad = it.søknad.journalfør(journalpostIdSøknad).medOppgave(oppgaveIdSøknad)
            val sak = it.toSak(saksnummer)
            sak.copy(søknader = listOf(søknad)) to søknad
        }
    }
}
