package no.nav.su.se.bakover.test

import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.SakFactory
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.søknadsinnholdAlder
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.time.Clock
import java.util.LinkedList
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
    sakInfo: SakInfo = SakInfo(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        type = Sakstype.UFØRE,
    ),
    søknadsInnhold: SøknadsinnholdUføre = søknadinnhold(sakInfo.fnr),
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return nySak(
        clock = clock,
        sakInfo = sakInfo,
        søknadsinnhold = søknadsInnhold,
    )
}

fun nySakAlder(
    clock: Clock = fixedClock,
    sakInfo: SakInfo = SakInfo(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        type = Sakstype.ALDER,
    ),
    søknadsInnhold: SøknadsinnholdAlder = søknadsinnholdAlder(),
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return nySak(
        clock = clock,
        sakInfo = sakInfo,
        søknadsinnhold = søknadsInnhold,
    )
}

fun nySak(
    clock: Clock = fixedClock,
    sakInfo: SakInfo,
    søknadsinnhold: SøknadInnhold,
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return SakFactory(
        clock = clock,
        uuidFactory = object : UUIDFactory() {
            val ids = LinkedList(listOf(sakInfo.sakId, søknadId))
            override fun newUUID(): UUID {
                return ids.pop()
            }
        },
    ).let { sakFactory ->
        sakFactory.nySakMedNySøknad(
            fnr = fnr,
            søknadInnhold = søknadsinnhold,
        ).let {
            val søknad = it.søknad.journalfør(journalpostIdSøknad).medOppgave(oppgaveIdSøknad)
            val sak = it.toSak(sakInfo.saksnummer)
            sak.copy(søknader = listOf(søknad)) to søknad
        }
    }
}
