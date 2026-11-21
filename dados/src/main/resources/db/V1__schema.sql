-- Esquema inicial: cadastros (pessoas, cartões, posições) e histórico de treino (sessões e eventos).

CREATE TABLE pessoa (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome      VARCHAR(40) NOT NULL,
    matricula VARCHAR(20) NOT NULL UNIQUE,
    perfil    VARCHAR(10) NOT NULL CHECK (perfil IN ('ALUNO', 'INSTRUTOR')),
    email     VARCHAR(80),
    ativo     BOOLEAN DEFAULT TRUE NOT NULL
);

-- etiqueta e volume_id podem ficar vazios (NULL não conta para o UNIQUE)
CREATE TABLE cartao (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    etiqueta       VARCHAR(10) UNIQUE,
    part_number    VARCHAR(30) NOT NULL,
    volume_id      VARCHAR(40) UNIQUE,
    sha256         CHAR(64),
    gravado_em     DATE,
    ultima_leitura TIMESTAMP,
    leituras       INT DEFAULT 0 NOT NULL,
    status         VARCHAR(10) DEFAULT 'ATIVO' NOT NULL CHECK (status IN ('ATIVO', 'BLOQUEADO', 'PERDIDO')),
    observacao     VARCHAR(60)
);

CREATE TABLE posicao (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    nome          VARCHAR(16) NOT NULL UNIQUE,
    altitude_ft   INT NOT NULL,
    velocidade_kt INT NOT NULL,
    proa_graus    INT NOT NULL,
    distancia_nm  DECIMAL(4, 1) NOT NULL,
    destino       CHAR(4) DEFAULT 'SBKP' NOT NULL
);

-- Pessoa e posição usadas numa sessão não podem sumir (as FKs sem CASCADE garantem isso)
CREATE TABLE sessao (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    aluno_id     BIGINT NOT NULL REFERENCES pessoa (id),
    instrutor_id BIGINT REFERENCES pessoa (id),
    modo         VARCHAR(10) NOT NULL CHECK (modo IN ('SOLO', 'INSTRUTOR')),
    posicao_id   BIGINT REFERENCES posicao (id),
    cartuchos    VARCHAR(200),
    inicio       TIMESTAMP NOT NULL,
    fim          TIMESTAMP,
    duracao_s    DOUBLE PRECISION,
    crash        BOOLEAN DEFAULT FALSE NOT NULL,
    nota         DECIMAL(3, 1),
    observacao   VARCHAR(60)
);

CREATE TABLE evento_sessao (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    sessao_id BIGINT NOT NULL REFERENCES sessao (id) ON DELETE CASCADE,
    tempo_s   DOUBLE PRECISION NOT NULL,
    descricao VARCHAR(120) NOT NULL
);

CREATE INDEX idx_evento_sessao ON evento_sessao (sessao_id);
