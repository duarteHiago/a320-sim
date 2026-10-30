package br.a320sim.core.bus;

/** Catálogo dos labels do barramento. Prefixo = quem publica. */
public final class Labels {

    private Labels() {
    }

    // Verdade física (modelo de voo). Só os sensores deveriam ler estes labels.
    public static final String TRUE_IAS = "TRUE.IAS";
    public static final String TRUE_ALT = "TRUE.ALT";
    public static final String TRUE_VS = "TRUE.VS";
    public static final String TRUE_PITCH = "TRUE.PITCH";
    public static final String TRUE_ROLL = "TRUE.ROLL";
    public static final String TRUE_HDG = "TRUE.HDG";
    public static final String TRUE_FPA = "TRUE.FPA";
    public static final String TRUE_AOA = "TRUE.AOA";
    public static final String TRUE_GS = "TRUE.GS";
    public static final String TRUE_POS_X = "TRUE.POS_X";
    public static final String TRUE_POS_Y = "TRUE.POS_Y";

    // Sensores de hardware do avião (trem de pouso, radioaltímetro)
    public static final String SIM_ON_GROUND = "SIM.ON_GROUND";
    public static final String SIM_RADIO_ALT = "SIM.RADIO_ALT";
    public static final String SIM_CRASHED = "SIM.CRASHED";
    public static final String SIM_TIME = "SIM.TIME";
    public static final String SIM_FREEZE = "SIM.FREEZE";
    public static final String SIM_IR_INSTANT_ALIGN = "SIM.IR_INSTANT_ALIGN";

    // Comandos do piloto (manche, manete, alavancas)
    public static final String CTL_PITCH = "SIM.CTL.PITCH";
    public static final String CTL_ROLL = "SIM.CTL.ROLL";
    public static final String CTL_YAW = "SIM.CTL.YAW";
    public static final String CTL_BRAKES = "SIM.CTL.BRAKES";
    public static final String CTL_FLAPS = "SIM.CTL.FLAPS";
    public static final String CTL_GEAR_DOWN = "SIM.CTL.GEAR_DOWN";
    public static final String SIM_THRUST_LEVER = "SIM.THRUST_LEVER";

    // Painel FCU (botões contam cliques; quem lê compara com o último valor visto)
    public static final String PANEL_SPD = "PANEL.SPD";
    public static final String PANEL_HDG = "PANEL.HDG";
    public static final String PANEL_ALT = "PANEL.ALT";
    public static final String PANEL_VS = "PANEL.VS";
    public static final String PANEL_AP_PUSH = "PANEL.AP_PUSH";
    public static final String PANEL_ATHR_PUSH = "PANEL.ATHR_PUSH";
    public static final String PANEL_AP_DISC = "PANEL.AP_DISC";

    // Sensores (ADIRU): dados de ar (ADR) e inerciais (IR)
    public static final String ADR_IAS = "ADR.IAS";
    public static final String ADR_ALT = "ADR.ALT";
    public static final String ADR_VS = "ADR.VS";
    public static final String ADR_AOA = "ADR.AOA";
    public static final String IR_ALIGN_STATUS = "IR.ALIGN_STATUS";
    public static final String IR_ALIGN_PROGRESS = "IR.ALIGN_PROGRESS";
    public static final String IR_PITCH = "IR.PITCH";
    public static final String IR_ROLL = "IR.ROLL";
    public static final String IR_HDG = "IR.HDG";
    public static final String IR_FPA = "IR.FPA";
    public static final String IR_GS = "IR.GS";
    public static final String IR_POS_X = "IR.POS_X";
    public static final String IR_POS_Y = "IR.POS_Y";

    // Fly-by-wire (ELAC)
    public static final String FCTL_LAW = "FCTL.LAW";
    public static final String FCTL_PITCH_RATE = "FCTL.PITCH_RATE";
    public static final String FCTL_ROLL_RATE = "FCTL.ROLL_RATE";
    public static final String FCTL_PROT = "FCTL.PROT";

    // Motores (FADEC)
    public static final String ENG_TYPE = "ENG.TYPE";
    public static final String ENG_PRIMARY_PARAM = "ENG.PRIMARY_PARAM";
    public static final String ENG1_N1 = "ENG.1.N1";
    public static final String ENG2_N1 = "ENG.2.N1";
    public static final String ENG1_EPR = "ENG.1.EPR";
    public static final String ENG2_EPR = "ENG.2.EPR";

    // Piloto automático (FCU/FMGC)
    public static final String AP_MODES = "AP.MODES";
    public static final String AP_ENGAGED = "AP.ENGAGED";
    public static final String AP_LAT_MODE = "AP.LAT_MODE";
    public static final String AP_VERT_MODE = "AP.VERT_MODE";
    public static final String AP_PITCH_CMD = "AP.PITCH_CMD";
    public static final String AP_ROLL_CMD = "AP.ROLL_CMD";
    public static final String AP_ATHR_ACTIVE = "AP.ATHR_ACTIVE";
    public static final String AP_ATHR_CMD = "AP.ATHR_CMD";

    // Telas (DMC)
    public static final String DMC_STYLE = "DMC.STYLE";

    // Alarmes (FWC)
    public static final String FWC_WARNINGS = "FWC.WARNINGS";
    public static final String FWC_CAUTIONS = "FWC.CAUTIONS";
    public static final String FWC_MASTER_WARN = "FWC.MASTER_WARN";
    public static final String FWC_MASTER_CAUT = "FWC.MASTER_CAUT";

    // Estação do instrutor
    public static final String IOS_RACK = "IOS.RACK";
    public static final String IOS_MESSAGE = "IOS.MESSAGE";
    public static final String IOS_MESSAGE_ID = "IOS.MESSAGE_ID";
    public static final String IOS_FAILURES = "IOS.FAILURES";

    /** Labels que o aluno controla (manche, manete, alavancas, FCU) e que viajam do cockpit para o host. */
    public static boolean isPilotInput(String label) {
        return label.startsWith("SIM.CTL.") || label.startsWith("PANEL.") || label.equals(SIM_THRUST_LEVER);
    }
}
