update klage set type = 'vilkårsvurdert_utfylt_til_vurdering' where type = 'vilkårsvurdert_utfylt';
update klage set type = 'vilkårsvurdert_bekreftet_til_vurdering' where type = 'vilkårsvurdert_bekreftet';
update klage set type = 'til_attestering_til_vurdering' where type = 'til_attestering'