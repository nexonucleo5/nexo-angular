package com.nexo.domain;

/**
 * Documentos que a secretaria cobra na matrícula.
 *
 * <p>Antes a documentação era um estado só (COMPLETA/PENDENTE/INCOMPLETA) escolhido
 * na mão: dava para saber que faltava algo, mas não O QUE faltava — e a secretária
 * tinha que ligar para o responsável sem saber o que pedir. Aqui cada documento é
 * um item, e o estado da matrícula passa a ser consequência da lista.
 *
 * <p>{@code obrigatorio} separa o que trava a matrícula do que é desejável: sem
 * laudo médico a matrícula se efetiva; sem certidão de nascimento, não.
 */
public enum TipoDocumento {

    CERTIDAO_NASCIMENTO("Certidão de nascimento", true, true),
    RG("RG", false, true),
    CPF("CPF", true, true),
    COMPROVANTE_RESIDENCIA("Comprovante de residência", true, false),
    HISTORICO_ESCOLAR("Histórico escolar", true, false),
    FOTO_3X4("Foto 3x4", false, false),
    CARTEIRA_VACINACAO("Carteira de vacinação", false, false),
    LAUDO_MEDICO("Laudo médico", false, false);

    private final String rotulo;
    private final boolean obrigatorio;
    private final boolean permanente;

    TipoDocumento(String rotulo, boolean obrigatorio, boolean permanente) {
        this.rotulo = rotulo;
        this.obrigatorio = obrigatorio;
        this.permanente = permanente;
    }

    public String getRotulo() { return rotulo; }
    public boolean isObrigatorio() { return obrigatorio; }

    /**
     * Documento que não vence: acompanha o aluno na rematrícula em vez de ser
     * pedido de novo todo ano. Certidão e CPF não mudam; comprovante de residência,
     * foto e vacinação, sim — e é por isso que estes voltam à fila a cada ano.
     */
    public boolean isPermanente() { return permanente; }

    public static long totalObrigatorios() {
        return java.util.Arrays.stream(values()).filter(TipoDocumento::isObrigatorio).count();
    }
}
