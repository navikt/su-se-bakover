package no.nav.su.se.bakover.test

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.sak.SakFactory
import no.nav.su.se.bakover.domain.søknad.Søknad
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FnrWrapper
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.test.søknad.journalpostIdSøknad
import no.nav.su.se.bakover.test.søknad.oppgaveIdSøknad
import no.nav.su.se.bakover.test.søknad.søknadId
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.søknad.søknadsinnholdAlder
import vilkår.vurderinger.domain.Grunnlagsdata
import java.time.Clock
import java.util.LinkedList
import java.util.UUID

val sakId: UUID = UUID.randomUUID()

fun Sak.hentGjeldendeVilkårOgGrunnlag(
    periode: Periode,
    clock: Clock,
): GrunnlagsdataOgVilkårsvurderingerRevurdering {
    return hentGjeldendeVedtaksdata(
        periode = periode,
        clock = clock,
    ).fold(
        {
            GrunnlagsdataOgVilkårsvurderingerRevurdering(
                Grunnlagsdata.IkkeVurdert,
                VilkårsvurderingerRevurdering.Uføre.ikkeVurdert(),
            )
        },
        {
            it.grunnlagsdataOgVilkårsvurderinger
        },
    )
}

/**
 * @param sakId forkastes dersom sakInfo sendes inn.
 */
fun nySakUføre(
    clock: Clock = fixedClock,
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    sakInfo: SakInfo = SakInfo(
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        type = Sakstype.UFØRE,
    ),
    søknadsInnhold: SøknadsinnholdUføre = søknadinnholdUføre(personopplysninger = FnrWrapper(sakInfo.fnr)),
    søknadInnsendtAv: NavIdentBruker = veileder,
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return nySak(
        clock = clock,
        sakInfo = sakInfo,
        søknadsinnhold = søknadsInnhold,
        søknadInnsendtAv = søknadInnsendtAv,
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
    søknadsInnhold: SøknadsinnholdAlder = søknadsinnholdAlder(personopplysninger = FnrWrapper(sakInfo.fnr)),
    søknadInnsendtAv: NavIdentBruker = veileder,
): Pair<Sak, Søknad.Journalført.MedOppgave> {
    return nySak(
        clock = clock,
        sakInfo = sakInfo,
        søknadsinnhold = søknadsInnhold,
        søknadInnsendtAv = søknadInnsendtAv,
    )
}

fun nySak(
    clock: Clock = fixedClock,
    sakInfo: SakInfo,
    søknadsinnhold: SøknadInnhold,
    søknadInnsendtAv: NavIdentBruker = veileder,
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
            fnr = sakInfo.fnr,
            søknadInnhold = søknadsinnhold,
            innsendtAv = søknadInnsendtAv,
        ).let {
            val søknad = it.søknad.journalfør(journalpostIdSøknad).medOppgave(oppgaveIdSøknad)
            val sak = it.toSak(sakInfo.saksnummer, Hendelsesversjon(1))
            sak.copy(søknader = listOf(søknad)) to søknad
        }
    }
}

fun sakInfo(
    sakId: UUID = no.nav.su.se.bakover.test.sakId,
    saksnummer: Saksnummer = no.nav.su.se.bakover.test.saksnummer,
    fnr: Fnr = no.nav.su.se.bakover.test.fnr,
    type: Sakstype = Sakstype.UFØRE,
): SakInfo = SakInfo(sakId, saksnummer, fnr, type)
