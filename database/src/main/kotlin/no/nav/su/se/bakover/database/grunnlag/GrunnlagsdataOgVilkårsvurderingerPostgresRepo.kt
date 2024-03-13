package no.nav.su.se.bakover.database.grunnlag

import behandling.revurdering.domain.GrunnlagsdataOgVilkårsvurderingerRevurdering
import behandling.revurdering.domain.VilkårsvurderingerRevurdering
import behandling.søknadsbehandling.domain.GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling
import behandling.søknadsbehandling.domain.VilkårsvurderingerSøknadsbehandling
import no.nav.su.se.bakover.common.domain.BehandlingsId
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening
import no.nav.su.se.bakover.domain.vilkår.pensjonsVilkår
import vilkår.bosituasjon.domain.grunnlag.Bosituasjon
import vilkår.vurderinger.domain.EksterneGrunnlag
import vilkår.vurderinger.domain.Grunnlagsdata
import vilkår.vurderinger.domain.GrunnlagsdataOgVilkårsvurderinger

/**
 * TODO - må splittes opp i generell + revurdering + søknadsbehandlings deler
 */
internal class GrunnlagsdataOgVilkårsvurderingerPostgresRepo(
    private val dbMetrics: DbMetrics,
    private val bosituasjongrunnlagPostgresRepo: BosituasjongrunnlagPostgresRepo,
    private val fradragsgrunnlagPostgresRepo: FradragsgrunnlagPostgresRepo,
    private val uføreVilkårsvurderingPostgresRepo: UføreVilkårsvurderingPostgresRepo,
    private val formueVilkårsvurderingPostgresRepo: FormueVilkårsvurderingPostgresRepo,
    private val utenlandsoppholdVilkårsvurderingPostgresRepo: UtenlandsoppholdVilkårsvurderingPostgresRepo,
    private val opplysningspliktVilkårsvurderingPostgresRepo: OpplysningspliktVilkårsvurderingPostgresRepo,
    private val pensjonVilkårsvurderingPostgresRepo: PensjonVilkårsvurderingPostgresRepo,
    private val familiegjenforeningVilkårsvurderingPostgresRepo: FamiliegjenforeningVilkårsvurderingPostgresRepo,
    private val lovligOppholdVilkårsvurderingPostgresRepo: LovligOppholdVilkårsvurderingPostgresRepo,
    private val flyktningVilkårsvurderingPostgresRepo: FlyktningVilkårsvurderingPostgresRepo,
    private val fastOppholdINorgeVilkårsvurderingPostgresRepo: FastOppholdINorgeVilkårsvurderingPostgresRepo,
    private val personligOppmøteVilkårsvurderingPostgresRepo: PersonligOppmøteVilkårsvurderingPostgresRepo,
    private val institusjonsoppholdVilkårsvurderingPostgresRepo: InstitusjonsoppholdVilkårsvurderingPostgresRepo,
) {
    fun lagre(
        behandlingId: BehandlingsId,
        grunnlagsdataOgVilkårsvurderinger: GrunnlagsdataOgVilkårsvurderinger,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreGrunnlagsdataOgVilkårsvurderinger") {
            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.uføreVilkår()
                .onRight {
                    uføreVilkårsvurderingPostgresRepo.lagre(
                        behandlingId = behandlingId,
                        vilkår = it,
                        tx = tx,
                    )
                }

            lovligOppholdVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.lovligOppholdVilkår(),
                tx = tx,
            )

            formueVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.formueVilkår(),
                tx = tx,
            )

            utenlandsoppholdVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.utenlandsoppholdVilkår(),
                tx = tx,
            )
            bosituasjongrunnlagPostgresRepo.lagreBosituasjongrunnlag(
                behandlingId = behandlingId,
                grunnlag = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.bosituasjon,
                tx = tx,
            )
            fradragsgrunnlagPostgresRepo.lagreFradragsgrunnlag(
                behandlingId = behandlingId,
                fradragsgrunnlag = grunnlagsdataOgVilkårsvurderinger.grunnlagsdata.fradragsgrunnlag,
                tx = tx,
            )
            opplysningspliktVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.opplysningspliktVilkår(),
                tx = tx,
            )
            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.pensjonsVilkår()
                .onRight {
                    pensjonVilkårsvurderingPostgresRepo.lagre(
                        behandlingId = behandlingId,
                        vilkår = it,
                        tx = tx,
                    )
                }
            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.familiegjenforening().map {
                familiegjenforeningVilkårsvurderingPostgresRepo.lagre(behandlingId = behandlingId, vilkår = it, tx = tx)
            }
            grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.flyktningVilkår()
                .onRight {
                    flyktningVilkårsvurderingPostgresRepo.lagre(
                        behandlingId = behandlingId,
                        vilkår = it,
                        tx = tx,
                    )
                }
            fastOppholdINorgeVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.fastOppholdVilkår(),
                tx = tx,
            )
            personligOppmøteVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.personligOppmøteVilkår(),
                tx = tx,
            )
            institusjonsoppholdVilkårsvurderingPostgresRepo.lagre(
                behandlingId = behandlingId,
                vilkår = grunnlagsdataOgVilkårsvurderinger.vilkårsvurderinger.institusjonsopphold,
                tx = tx,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun hentForRevurdering(
        revurderingId: RevurderingId,
        session: Session,
        sakstype: Sakstype,
    ): GrunnlagsdataOgVilkårsvurderingerRevurdering {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForRevurderingId") {
            val grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(revurderingId, session),
                // for revurdering kan man bare ha fullstendige bosituasjoner
                bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(
                    revurderingId,
                    session,
                ) as List<Bosituasjon.Fullstendig>,
            )
            val vilkårsvurderinger = when (sakstype) {
                Sakstype.ALDER -> {
                    VilkårsvurderingerRevurdering.Alder(
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        pensjon = pensjonVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        familiegjenforening = familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                            revurderingId,
                            session,
                        ),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                    )
                }

                Sakstype.UFØRE -> {
                    VilkårsvurderingerRevurdering.Uføre(
                        uføre = uføreVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        flyktning = flyktningVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(revurderingId, session),
                        institusjonsopphold = institusjonsoppholdVilkårsvurderingPostgresRepo.hent(
                            behandlingId = revurderingId,
                            session = session,
                        ),
                    )
                }
            }
            GrunnlagsdataOgVilkårsvurderingerRevurdering(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                // TODO jah: Må håndtere eksterneGrunnlag for revurdering hvis vi implementerer det.
            )
        }
    }

    fun hentForSøknadsbehandling(
        søknadsbehandlingId: SøknadsbehandlingId,
        session: Session,
        sakstype: Sakstype,
        eksterneGrunnlag: EksterneGrunnlag,
    ): GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForSøknadsbehandlingId") {
            val grunnlagsdata = Grunnlagsdata.createTillatUfullstendigBosituasjon(
                fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(søknadsbehandlingId, session),
                bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(søknadsbehandlingId, session),
            )

            val vilkårsvurderinger = when (sakstype) {
                Sakstype.ALDER -> {
                    VilkårsvurderingerSøknadsbehandling.Alder(
                        formue = formueVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        pensjon = pensjonVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        familiegjenforening = familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                            behandlingId = søknadsbehandlingId,
                            session = session,
                        ),
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        institusjonsopphold = institusjonsoppholdVilkårsvurderingPostgresRepo.hent(
                            behandlingId = søknadsbehandlingId,
                            session = session,
                        ),
                    )
                }

                Sakstype.UFØRE -> {
                    VilkårsvurderingerSøknadsbehandling.Uføre(
                        uføre = uføreVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                        institusjonsopphold = institusjonsoppholdVilkårsvurderingPostgresRepo.hent(
                            behandlingId = søknadsbehandlingId,
                            session = session,
                        ),
                        flyktning = flyktningVilkårsvurderingPostgresRepo.hent(søknadsbehandlingId, session),
                    )
                }
            }
            GrunnlagsdataOgVilkårsvurderingerSøknadsbehandling(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = eksterneGrunnlag,
            )
        }
    }
}
