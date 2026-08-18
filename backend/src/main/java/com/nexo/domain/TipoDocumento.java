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

    CERTIDAO_NASCIMENTO("Certidão de nascimento", true),
    RG("RG", false),
    CPF("CPF", true),
    COMPROVANTE_RESIDENCIA("Comprovante de residência", true),
    HISTORICO_ESCOLAR("Histórico escolar", true),
    FOTO_3X4("Foto 3x4", false),
    CARTEIRA_VACINACAO("Carteira de vacinação", false),
    LAUDO_MEDICO("Laudo médico", false);

    private final String rotulo;
    private final boolean obrigatorio;

    TipoDocumento(String rotulo, boolean obrigatorio) {
        this.rotulo = rotulo;
        this.obrigatorio = obrigatorio;
    }

    public String getRotulo() { return rotulo; }
    public boolean isObrigatorio() { return obrigatorio; }

    public static long totalObrigatorios() {
        return java.util.Arrays.stream(values()).filter(TipoDocumento::isObrigatorio).count();
    }
}
