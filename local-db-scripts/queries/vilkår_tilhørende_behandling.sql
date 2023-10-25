-- Kan brukes for å hente alle vilkår tilhørende en behandling eller revurdering.
WITH Var AS (SELECT '<behandlingId>'::uuid AS bId)
SELECT 'flyktning', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_flyktning
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'familiegjenforening', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_familiegjenforening
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'fast opphold', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_fastopphold
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'lovlig opphold', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_lovligopphold
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'institusjonsopphold', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_institusjonsopphold
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'personlig oppmøte', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_personlig_oppmøte
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'pensjon', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_pensjon
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'opplysningsplikt', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_opplysningsplikt
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'formue', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_formue
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'utland', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_utland
WHERE behandlingId = (SELECT bId FROM Var)
UNION ALL
SELECT 'uføre', resultat, fraogmed, tilogmed
FROM vilkårsvurdering_uføre
WHERE behandlingId = (SELECT bId FROM Var);
