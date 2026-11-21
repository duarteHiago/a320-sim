# A320 SIM

Simulador de A320 com estética de console retrô. As "caixas pretas" do avião são carregadas por **cartuchos** (cartões SD), e o instrutor monta o avião escolhendo quais cartuchos encaixar.

![Vista pela janela na cabeceira da pista 15 de Viracopos](docs/img/janela-pista.png)

| Monte seu A320 | Em voo pela janela |
|---|---|
| ![Tela de montagem do rack com três cartuchos](docs/img/rack.png) | ![Vista pela janela a 8000 pés com HUD](docs/img/janela-voo.png) |
| **Painel neo (LCD), em voo** | **Painel ceo (CRT), sensores alinhando** |
| ![Painel neo com PFD, ND e E/WD](docs/img/painel-neo.png) | ![Painel ceo com bandeiras ATT/HDG e motor IAE em EPR](docs/img/painel-ceo.png) |

**Estação do instrutor**, conectada ao aluno pela rede:

![Estação do instrutor com mapa, espelho do aluno, falhas e comandos](docs/img/ios.png)

## Rack

| Slot | Caixas | ceo | neo |
|---|---|---|---|
| PLATAFORMA | Sensores (ADIRU) + Fly-by-wire (ELAC) | alinhamento lento (90s) | alinhamento rápido (20s) |
| PROPULSAO | Motores (FADEC) + Piloto automático | IAE V2500 (EPR), AP básico HDG/ALT | PW1100G (N1), AP completo + V/S + autothrust |
| COCKPIT | Telas (DMC) + Alarmes (FWC) | CRT, alarmes básicos | LCD, alarmes avançados |

Regra: **PROPULSAO neo exige COCKPIT neo**.

## Cartões SD

Cada cartão leva quatro arquivos na raiz:

| Arquivo | Para que serve |
|---|---|
| `cartridge.json` | o manifesto (grupo, versão, caixas, requisitos) |
| `caixas.jar` | o software das duas caixas daquele cartão |
| `checksum.sha256` | o SHA-256 de `cartridge.json` e `caixas.jar` (**integridade**) |
| `assinatura.sig` | a assinatura Ed25519 do checksum, feita com a chave da fábrica (**autenticidade**) |

O simulador varre os leitores a cada ~0,7 s; o **grupo vem do manifesto**, então não importa em qual leitor do hub o cartão entra.

**Assinatura dos cartões.** Antes de carregar qualquer classe do `caixas.jar`, o data loader confere a assinatura com a chave pública que vai dentro do programa e depois o hash de cada arquivo. Se algo não bate, o cartão é **RECUSADO** e nenhum código dele roda:

| Mensagem | Quando acontece |
|---|---|
| `RECUSADO: SEM ASSINATURA` | faltam `checksum.sha256` ou `assinatura.sig` |
| `RECUSADO: ASSINATURA INVALIDA` | assinado com outra chave, ou checksum editado |
| `RECUSADO: ARQUIVO CORROMPIDO` | o `.jar` ou o manifesto mudou depois de assinado |
| `RECUSADO: ARQUIVO FALTANDO` | um arquivo listado no checksum sumiu |
| `RECUSADO: SEM CHAVE PUBLICA` | o programa foi compilado sem a chave da fábrica |

Para conferir um cartão no terminal (Git Bash ou Linux): `sha256sum -c checksum.sha256` dentro da unidade.

**Chaves (uma vez só, para a equipe toda).** Quem grava os cartões roda:

```
gradlew :cartridges:gerarChaves
```

Isso cria `chaves/fabrica-privada.key` (**nunca vai para o Git**, já está no `.gitignore`; guarde uma cópia e passe para a equipe por fora do GitHub) e `host/src/main/resources/chave-publica-cartuchos.txt` (essa vai para o Git). Gerar chaves novas invalida todos os cartões já gravados. Sem a chave privada, `sdImages` gera imagens sem assinatura e `gravarSd` se recusa a gravar. Os testes usam uma chave própria e não dependem da chave da equipe.

**Gravar os 6 cartões** (uma vez, ou de novo quando o software das caixas mudar). Com o cartão no leitor, veja a letra dele no Explorer e rode:

```
gradlew :cartridges:gravarSd -Pcartao=PLT-A320-CEO -Pdrive=G
```

Repita para `PLT-A320-NEO`, `PRP-A320-CEO`, `PRP-A320-NEO`, `CKP-A320-CEO` e `CKP-A320-NEO`, e etiquete cada cartão. A tarefa só copia os quatro arquivos (não formata); ela recusa a unidade do sistema, a do projeto e qualquer unidade com mais de 64 GB. `gradlew :cartridges:sdImages` só gera as imagens em `cartridges/build/sd/`.

**Usando:**

- Na tela **MONTE SEU A320**, encaixar um cartão no leitor coloca ele direto no slot do grupo dele; tirar o cartão esvazia o slot. As setas continuam funcionando quando há mais de um cartão do mesmo grupo plugado.
- **Com o avião ligado**, tirar um cartão desliga as caixas dele na hora (ex.: sem o COCKPIT as telas apagam e os alarmes somem) e aparece **CARTUCHO REMOVIDO** no cockpit e no log do instrutor. Encaixar de volta (em qualquer leitor) faz o *data loading* e as caixas religam com self-test; os sensores precisam alinhar de novo. Cartão incompatível (ex.: COCKPIT ceo com PROPULSAO neo) é recusado.
- O `.jar` é copiado para `cache/` antes de carregar, então o Windows não trava o arquivo no cartão.

**Fonte dos cartuchos** (`-Dsim.cartridges=...`):

| Valor | Usa |
|---|---|
| `auto` (padrão) | cartões SD; se nenhum estiver plugado, as pastas `sd-dev/` |
| `sd` | só cartões SD |
| `dev` | só `sd-dev/` |

Outras opções: `-Dsim.sd.drives=G,H,I` limita a busca a essas letras; `-Dsim.sd.roots=pastaA;pastaB` usa pastas fingindo ser leitores (copie uma imagem de `cartridges/build/sd/` para dentro para "encaixar", apague para "arrancar").

## Módulos do projeto

- `core`: contratos (barramento, `AvionicsModule`, `BaseModule`, cartuchos)
- `flightmodel`: física simplificada (sustentação, arrasto, empuxo, peso, solo)
- `cartridges`: software das caixas
- `host`: o "supercomputador" (rack, cartuchos, loop de simulação, falhas, comandos do instrutor)
- `net`: comunicação TCP entre o PC do instrutor e o do aluno
- `cockpit`: o programa gráfico em LibGDX (título, rack, boot, cockpit, estação do instrutor)
- `sd-dev/`: pastas que fingem ser cartões SD durante o desenvolvimento (e a fonte das imagens gravadas nos cartões). Elas não têm `.jar`, então não passam pela assinatura: o software vem do próprio programa

## Rodar

```
gradlew :cockpit:run      # o simulador (escolha SOLO, INSTRUTOR ou ALUNO na tela de título)
gradlew :host:run --console=plain   # console de texto do host
gradlew test              # testes (física, voo com piloto automático, falhas, rede)
```

### Treinando em dois PCs (instrutor + aluno)

É o mesmo programa nos dois PCs; na tela de título cada um escolhe o seu papel.

1. **Instrutor:** escolhe **INSTRUTOR**, monta o rack e liga o avião. No topo da estação aparece **"AGUARDANDO ALUNO  IP: ..."**.
2. **Aluno:** escolhe **ALUNO**, digita o IP mostrado no PC do instrutor e aperta ENTER.
3. Quando conecta, o instrutor vê **"ALUNO: nome (ip)"** em verde.

- **Mesmo Wi-Fi/rede:** use o IP local do instrutor (ex.: `192.168.0.15`).
- **Casas diferentes:** os dois instalam a mesma VPN de jogos (**Radmin VPN**, **ZeroTier** ou **Tailscale**) e entram na mesma rede; use o IP que a VPN dá ao instrutor.
- Na primeira vez, o **Firewall do Windows** do instrutor pergunta se libera o Java: permita em redes privadas. A porta usada é a **7320**.

### Estação do instrutor

| Comando | O que faz |
|---|---|
| PAUSAR / CONTINUAR | congela a simulação |
| PISTA | volta o avião à cabeceira |
| EM VOO | 8000 ft, 250 kt, mantendo a proa |
| NA FINAL | alinhado com a pista 15, a 5 NM e 1600 ft |
| ALINHAR IR | alinhamento instantâneo dos sensores |
| CARTUCHOS | desliga o avião e volta ao rack |
| MOTOR 1 PARADO | apaga o motor 1 (alarme ENG 1 FAIL) |
| PANE NOS SENSORES | some velocidade, altitude e atitude; fly-by-wire cai para Lei Direta |
| PILOTO AUTOMATICO INOP | desengaja o AP e impede religar |
| MSG (ENTER) | escreve uma mensagem que aparece na tela do aluno |

O mapa mostra o avião do aluno, o rastro e a pista; o espelho mostra os dados reais e os alertas que o aluno está vendo.

### Comandos do cockpit

| Tecla | Ação |
|---|---|
| W/S, A/D (ou setas) | sidestick: picar/cabrar, rolar |
| Q/E | leme (direção no solo) |
| SHIFT / CTRL | manete +/- |
| 1, 2, 3, 4 | detentes IDLE, CL, FLX, TOGA |
| F / V | flaps baixa / recolhe |
| G | trem de pouso |
| B (segurar) | freio |
| ESPAÇO | desliga o piloto automático |
| M ou clique nas luzes | silencia MASTER WARN / CAUT |
| P | pausa (só no SOLO) |
| R | volta para a pista (só no SOLO) |
| T | reposiciona em voo, 8000 ft e 250 kt (só no SOLO) |
| I | alinhamento instantâneo dos sensores (só no SOLO) |
| C | alterna PAINEL (instrumentos) e JANELA (para-brisa) |
| F12 | screenshot (pasta `screenshots/`) |
| ESC | volta para o rack (SOLO) ou desconecta (ALUNO) |

### Controle de videogame (modo simplificado)

Plugue um controle (Xbox, PlayStation ou genérico), antes ou durante o voo, e ele assume sozinho. Aparece **CONTROLE CONECTADO** e o voo fica só no acelerar e dar direção:

| Controle | Ação |
|---|---|
| Analógico esquerdo | direção: para os lados inclina as asas (e vira a roda do nariz no chão); puxar sobe o nariz, empurrar desce |
| RT | acelera |
| LT | reduz; com a manete toda atrás, freia |
| A | desliga o piloto automático |
| Y | alterna PAINEL / JANELA |
| Start | pausa (SOLO) |
| Back | volta para o rack (SOLO) ou desconecta (ALUNO) |

Nos menus (abertura, título, rack, conexão) o controle também navega: direcional ou analógico para mover, **A** confirma, **B** volta.

Flaps e trem ficam **automáticos**: flaps 1 para decolar e recolhendo conforme ganha velocidade; na aproximação, flaps e trem baixam conforme o avião desacelera e desce. Desconectou o controle, o teclado volta no modo completo com a manete, os flaps e o trem onde estavam. Pausa, reposicionar e ESC continuam no teclado.

Mouse no FCU: rodinha sobre SPD/HDG/ALT/V/S ajusta (SHIFT = x10), clique em AP1 e A/THR.

### Decolagem de exemplo

1. Espere os sensores alinharem (ou aperte **I**)
2. **F** (flaps 1), **4** (TOGA)
3. A ~145 kt, segure **S** até o nariz subir ~10°
4. Já no ar: **G** (recolhe o trem), **V** (flaps 0), **2** (CL)
5. Ajuste ALT e HDG no FCU e clique **AP1** (no neo, também **A/THR**)

## Fases

0. Fundação: rack, cartuchos, barramento e console ✅
1. Modelo de voo simplificado ✅
2. Cockpit 2D (PFD, ND, ECAM), teclado e mouse, sons ✅ e vista pela janela estilo Mode 7 ✅
3. Cartuchos reais: leitores SD, `.jar` por cartão, encaixar/arrancar com o avião ligado ✅
4. Rede instrutor ↔ aluno + estação do instrutor (falhas, pausa, reposicionamento, mensagens) ✅
5. Estação do instrutor completa: clima, snapshots, falhas programadas, mais falhas
6. Polimento: mais cenário (nuvens, noite, aeroportos), fonte pixel, debriefing
