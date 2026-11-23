-- Fase 3: o plano de voo que o aluno insere no MCDU fica gravado na sessão.
-- Tudo nulo = o aluno voou sem plano.

ALTER TABLE sessao ADD COLUMN plano_origem CHAR(4);
ALTER TABLE sessao ADD COLUMN plano_destino CHAR(4);
ALTER TABLE sessao ADD COLUMN plano_pista VARCHAR(3);
ALTER TABLE sessao ADD COLUMN plano_fl INT;
ALTER TABLE sessao ADD COLUMN plano_zfw_kg INT;
ALTER TABLE sessao ADD COLUMN plano_combustivel_kg INT;
ALTER TABLE sessao ADD COLUMN plano_v1 INT;
ALTER TABLE sessao ADD COLUMN plano_vr INT;
ALTER TABLE sessao ADD COLUMN plano_v2 INT;
