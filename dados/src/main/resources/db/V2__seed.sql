-- Dados iniciais. Sem acento: a fonte pixel só tem ASCII.

INSERT INTO pessoa (nome, matricula, perfil, email, ativo) VALUES
    ('Ana Souza', 'A001', 'ALUNO', 'ana@exemplo.com', TRUE),
    ('Bruno Lima', 'A002', 'ALUNO', NULL, TRUE),
    ('Carla Mendes', 'I001', 'INSTRUTOR', 'carla@exemplo.com', TRUE),
    ('Diego Rocha', 'I002', 'INSTRUTOR', NULL, TRUE);

-- Um cadastro manual por cartão, ainda sem o identificador do volume:
-- quando o cartão de verdade for lido, ele "adota" o cadastro com o mesmo part number.
INSERT INTO cartao (etiqueta, part_number, status) VALUES
    ('SD-01', 'PLT-A320-CEO', 'ATIVO'),
    ('SD-02', 'PLT-A320-NEO', 'ATIVO'),
    ('SD-03', 'PRP-A320-CEO', 'ATIVO'),
    ('SD-04', 'PRP-A320-NEO', 'ATIVO'),
    ('SD-05', 'CKP-A320-CEO', 'ATIVO'),
    ('SD-06', 'CKP-A320-NEO', 'ATIVO');

-- No chão a altitude é a do campo (Viracopos, 2170 ft); em voo, no prolongamento da pista 15
INSERT INTO posicao (nome, altitude_ft, velocidade_kt, proa_graus, distancia_nm, destino) VALUES
    ('PISTA 15', 2170, 0, 148, 0, 'SBKP'),
    ('EM VOO 8000', 8000, 250, 148, 10, 'SBKP'),
    ('NA FINAL 5NM', 3770, 150, 148, 5, 'SBKP');
