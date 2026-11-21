-- Fase 3: Guarulhos e Congonhas. Cada posição passa a dizer em qual pista do destino ela fica.
-- As que já existem são todas da pista 15 de Viracopos.

ALTER TABLE posicao ADD COLUMN pista VARCHAR(3) DEFAULT '15' NOT NULL;

-- Na final: 5 NM antes da cabeceira, 1600 ft acima do aeroporto, na proa da pista.
-- No chão: a altitude é a elevação do aeroporto (SBGR 2459 ft, SBSP 2631 ft).
INSERT INTO posicao (nome, altitude_ft, velocidade_kt, proa_graus, distancia_nm, destino, pista) VALUES
    ('FINAL GRU 10L', 4059, 150, 94, 5, 'SBGR', '10L'),
    ('FINAL CGH 17R', 4231, 150, 168, 5, 'SBSP', '17R'),
    ('PISTA GRU 10L', 2459, 0, 94, 0, 'SBGR', '10L'),
    ('PISTA CGH 17R', 2631, 0, 168, 0, 'SBSP', '17R');
