package no.nav.su.se.bakover.database.grunnlag

import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.domain.grunnlag.Bosituasjon
import no.nav.su.se.bakover.domain.grunnlag.EksterneGrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import java.util.UUID

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
        behandlingId: UUID,
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
        behandlingId: UUID,
        session: Session,
        sakstype: Sakstype,
    ): GrunnlagsdataOgVilkårsvurderinger.Revurdering {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForRevurderingId") {
            val grunnlagsdata = Grunnlagsdata.create(
                fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandlingId, session),
                // for revurdering kan man bare ha fullstendige bosituasjoner
                bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(
                    behandlingId,
                    session,
                ) as List<Bosituasjon.Fullstendig>,
            )
            val vilkårsvurderinger = when (sakstype) {
                Sakstype.ALDER -> {
                    Vilkårsvurderinger.Revurdering.Alder(
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        pensjon = pensjonVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        familiegjenforening = familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                            behandlingId,
                            session,
                        ),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    )
                }

                Sakstype.UFØRE -> {
                    Vilkårsvurderinger.Revurdering.Uføre(
                        uføre = uføreVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        flyktning = flyktningVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        institusjonsopphold = institusjonsoppholdVilkårsvurderingPostgresRepo.hent(
                            behandlingId = behandlingId,
                            session = session,
                        ),
                    )
                }
            }
            GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                // TODO jah: Må håndtere eksterneGrunnlag for revurdering hvis vi implementerer det.
            )
        }
    }

    fun hentForSøknadsbehandling(
        behandlingId: UUID,
        session: Session,
        sakstype: Sakstype,
        eksterneGrunnlag: EksterneGrunnlag,
    ): GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling {
        return dbMetrics.timeQuery("hentGrunnlagOgVilkårsvurderingerForSøknadsbehandlingId") {
            val grunnlagsdata = Grunnlagsdata.createTillatUfullstendigBosituasjon(
                fradragsgrunnlag = fradragsgrunnlagPostgresRepo.hentFradragsgrunnlag(behandlingId, session),
                bosituasjon = bosituasjongrunnlagPostgresRepo.hentBosituasjongrunnlag(behandlingId, session),
            )

            val vilkårsvurderinger = when (sakstype) {
                Sakstype.ALDER -> {
                    Vilkårsvurderinger.Søknadsbehandling.Alder(
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        pensjon = pensjonVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        familiegjenforening = familiegjenforeningVilkårsvurderingPostgresRepo.hent(
                            behandlingId = behandlingId,
                            session = session,
                        ),
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        institusjonsopphold = institusjonsoppholdVilkårsvurderingPostgresRepo.hent(
                            behandlingId = behandlingId,
                            session = session,
                        ),
                    )
                }

                Sakstype.UFØRE -> {
                    Vilkårsvurderinger.Søknadsbehandling.Uføre(
                        uføre = uføreVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        formue = formueVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        utenlandsopphold = utenlandsoppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        opplysningsplikt = opplysningspliktVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        lovligOpphold = lovligOppholdVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        fastOpphold = fastOppholdINorgeVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        personligOppmøte = personligOppmøteVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                        institusjonsopphold = institusjonsoppholdVilkårsvurderingPostgresRepo.hent(
                            behandlingId = behandlingId,
                            session = session,
                        ),
                        flyktning = flyktningVilkårsvurderingPostgresRepo.hent(behandlingId, session),
                    )
                }
            }
            GrunnlagsdataOgVilkårsvurderinger.Søknadsbehandling(
                grunnlagsdata = grunnlagsdata,
                vilkårsvurderinger = vilkårsvurderinger,
                eksterneGrunnlag = eksterneGrunnlag,
            )
        }
    }
}
