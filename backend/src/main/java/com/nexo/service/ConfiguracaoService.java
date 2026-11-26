package com.nexo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.api.ApiException;
import com.nexo.domain.ConfiguracaoEscola;
import com.nexo.domain.ConfiguracaoUsuario;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.AlunoRepository;
import com.nexo.repository.ConfiguracaoEscolaRepository;
import com.nexo.repository.ConfiguracaoUsuarioRepository;
import com.nexo.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configurações por usuário, particionadas por seção (espelha o ConfiguracoesService do Angular).
 * O documento completo é a fonte de verdade do servidor; o PATCH faz merge raso por seção.
 */
@Service
public class ConfiguracaoService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ConfiguracaoService.class);

    private final ConfiguracaoUsuarioRepository configuracoes;
    private final UsuarioRepository usuarios;
    private final AlunoRepository alunos;
    private final ObjectMapper mapper;
    private final ConfiguracaoEscolaRepository regrasDaEscola;
    private final AuditoriaService auditoria;

    public ConfiguracaoService(ConfiguracaoUsuarioRepository configuracoes,
                               UsuarioRepository usuarios,
                               AlunoRepository alunos,
                               ObjectMapper mapper,
                               ConfiguracaoEscolaRepository regrasDaEscola,
                               AuditoriaService auditoria) {
        this.regrasDaEscola = regrasDaEscola;
        this.auditoria = auditoria;
        this.configuracoes = configuracoes;
        this.usuarios = usuarios;
        this.alunos = alunos;
        this.mapper = mapper;
    }

    @Transactional
    public Map<String, Map<String, Object>> obter(Long usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        ConfiguracaoUsuario config = configuracoes.findByUsuarioId(usuarioId)
                .orElseGet(() -> criarPadrao(usuario));

        // Merge com defaults para garantir seções/campos novos em versões futuras
        Map<String, Map<String, Object>> defaults = defaultsPara(usuario.getRole());
        Map<String, Map<String, Object>> salvas = ler(config.getJson(), usuarioId);
        salvas.forEach((secao, campos) -> defaults.merge(secao, campos, (d, s) -> {
            d.putAll(s);
            return d;
        }));
        // A regra da escola vale igual para todo diretor: o que estiver na conta dele (de
        // antes de a regra ser única, ou de qualquer outra origem) não decide nada, e a tela
        // nunca mostra um interruptor diferente do que o servidor aplica.
        if (usuario.getRole() == Role.DIRETOR && defaults.containsKey(SECAO_GESTAO)) {
            defaults.get(SECAO_GESTAO).put(CHAVE_APROVACAO_ALUNO, exigeAprovacaoManualDeAluno());
        }
        return defaults;
    }

    @Transactional
    public Map<String, Map<String, Object>> atualizarSecao(Long usuarioId, String secao, Map<String, Object> patch) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        Map<String, Map<String, Object>> defaults = defaultsPara(usuario.getRole());
        if (!defaults.containsKey(secao)) {
            throw ApiException.badRequest("Seção de configuração desconhecida para o perfil: " + secao);
        }

        ConfiguracaoUsuario config = configuracoes.findByUsuarioId(usuarioId)
                .orElseGet(() -> criarPadrao(usuario));

        Map<String, Map<String, Object>> atual = ler(config.getJson(), usuarioId);
        Map<String, Object> campos = atual.computeIfAbsent(secao, s -> new LinkedHashMap<>(defaults.get(secao)));
        campos.putAll(patch);

        validar(usuario.getRole(), secao, campos);

        if (SECAO_GESTAO.equals(secao) && patch.containsKey(CHAVE_APROVACAO_ALUNO)) {
            definirAprovacaoManualDeAluno((Boolean) patch.get(CHAVE_APROVACAO_ALUNO), usuario.getNome());
        }
        // A cópia na conta do diretor deixa de existir: a fonte é a regra da escola.
        if (SECAO_GESTAO.equals(secao)) campos.remove(CHAVE_APROVACAO_ALUNO);

        config.setJson(escrever(atual));
        config.setAtualizadaEm(Instant.now());
        configuracoes.save(config);
        if (usuario.getRole() == Role.ALUNO && SECAO_PRIVACIDADE.equals(secao)) {
            aplicarPrivacidadeNoAluno(usuarioId, campos);
        }
        return obter(usuarioId);
    }

    static final String SECAO_PRIVACIDADE = "privacidade";
    static final String SECAO_GESTAO = "gestao";
    static final String CHAVE_APROVACAO_ALUNO = "exigirAprovacaoManualAluno";

    static final String REGRA_APROVACAO_ALUNO = "aprovacao-manual-cadastro-aluno";

    /**
     * A escola exige aprovação manual para cadastrar aluno? É <b>uma regra só</b>, a mesma
     * para qualquer diretor que cadastre — antes era a opção de cada diretor, então um
     * segundo diretor com a opção desligada cadastrava direto e o controle não valia.
     *
     * <p>Na ausência de registro vale o padrão da tela: ligado. Só {@code "false"} de verdade
     * desliga — qualquer outro valor cai para o lado seguro, que é pedir aprovação. Leitura
     * pura: não grava nada.
     */
    @Transactional(readOnly = true)
    public boolean exigeAprovacaoManualDeAluno() {
        return regrasDaEscola.findById(REGRA_APROVACAO_ALUNO)
                .map(r -> !"false".equals(r.getValor()))
                .orElse(true);
    }

    /**
     * Liga ou desliga a regra para a escola toda. Quem mudou e o novo valor ficam na
     * auditoria: desligar o controle de cadastro é uma decisão que precisa ter dono.
     */
    private void definirAprovacaoManualDeAluno(boolean exigir, String quem) {
        boolean atual = exigeAprovacaoManualDeAluno();
        ConfiguracaoEscola regra = regrasDaEscola.findById(REGRA_APROVACAO_ALUNO)
                .orElseGet(() -> new ConfiguracaoEscola(REGRA_APROVACAO_ALUNO, "true", quem));
        regra.setValor(String.valueOf(exigir));
        regra.setAtualizadoPor(quem);
        regra.setAtualizadoEm(Instant.now());
        regrasDaEscola.save(regra);
        if (atual != exigir) {
            auditoria.registrar(quem, EventoAuditoria.Tipo.ALTERACAO,
                    exigir ? "Aprovação de cadastro de aluno passou a ser exigida"
                           : "Aprovação de cadastro de aluno deixou de ser exigida",
                    "Regra da escola", null);
        }
    }

    /** Chaves de privacidade que o servidor faz valer, por perfil. */
    private static final Map<Role, List<String>> PRIVACIDADE_APLICADA = Map.of(
            Role.ALUNO, List.of("perfilPublico", "exibirNoRanking"),
            Role.PROFESSOR, List.of("perfilVisivelAlunos"));

    /**
     * Entre os usuários dados, quem desligou "Perfil visível para alunos" (professor).
     * Lido do JSON direto: são poucos professores por escola e só o chat do aluno pergunta,
     * então não compensa espelhar em coluna como o ranking do aluno.
     */
    @Transactional(readOnly = true)
    public Set<Long> comPerfilOcultoParaAlunos(Collection<Long> usuarioIds) {
        if (usuarioIds.isEmpty()) return Set.of();
        Set<Long> ocultos = new HashSet<>();
        for (ConfiguracaoUsuario c : configuracoes.findByUsuarioIdIn(usuarioIds)) {
            Map<String, Object> priv = ler(c.getJson(), c.getUsuario().getId()).get(SECAO_PRIVACIDADE);
            if (priv != null && Boolean.FALSE.equals(priv.get("perfilVisivelAlunos"))) {
                ocultos.add(c.getUsuario().getId());
            }
        }
        return ocultos;
    }

    /**
     * Copia as chaves de privacidade do JSON para as colunas do aluno, que é onde o
     * ranking as lê. Na mesma transação do JSON: não existe estado em que a tela mostra
     * "desligado" e o ranking ainda mostra o aluno.
     */
    private void aplicarPrivacidadeNoAluno(Long usuarioId, Map<String, Object> campos) {
        alunos.findByUsuarioId(usuarioId).ifPresent(aluno -> {
            aluno.setPerfilPublico(!Boolean.FALSE.equals(campos.get("perfilPublico")));
            aluno.setExibirNoRanking(!Boolean.FALSE.equals(campos.get("exibirNoRanking")));
        });
    }

    /**
     * Aplica ao banco o que um JSON de configuração já gravado diz — para os alunos que
     * desligaram algo antes de essas chaves valerem para o servidor. Idempotente.
     */
    @Transactional
    public int sincronizarPrivacidadeDosAlunos() {
        int ajustados = 0;
        // Duas consultas no total: antes eram duas por aluno (a conta lazy e a configuração),
        // a cada arranque do servidor.
        Map<Long, ConfiguracaoUsuario> porUsuario = new HashMap<>();
        for (ConfiguracaoUsuario c : configuracoes.findAllDeAlunos()) {
            porUsuario.put(c.getUsuario().getId(), c);
        }
        for (var aluno : alunos.findAllComUsuario()) {
            var config = porUsuario.get(aluno.getUsuario().getId());
            if (config == null) continue;
            Map<String, Object> priv = ler(config.getJson(), aluno.getUsuario().getId()).get(SECAO_PRIVACIDADE);
            if (priv == null) continue;
            boolean ranking = !Boolean.FALSE.equals(priv.get("exibirNoRanking"));
            boolean publico = !Boolean.FALSE.equals(priv.get("perfilPublico"));
            if (aluno.isExibirNoRanking() != ranking || aluno.isPerfilPublico() != publico) {
                aluno.setExibirNoRanking(ranking);
                aluno.setPerfilPublico(publico);
                ajustados++;
            }
        }
        return ajustados;
    }

    /** Validações de negócio reforçadas no servidor (ex.: início < fim na Disponibilidade). */
    private void validar(Role role, String secao, Map<String, Object> campos) {
        // Chave de privacidade que o servidor faz valer só aceita booleano de verdade:
        // "false" (texto) ou null seriam lidos como "ligado" e expõem o usuário sem aviso.
        if (SECAO_PRIVACIDADE.equals(secao)) {
            for (String chave : PRIVACIDADE_APLICADA.getOrDefault(role, List.of())) {
                if (campos.containsKey(chave) && !(campos.get(chave) instanceof Boolean)) {
                    throw ApiException.validation("Valor inválido.",
                            Map.of(chave, "Use verdadeiro ou falso."));
                }
            }
        }
        // As chaves de gestão são lidas com Boolean.FALSE.equals: um "false" em texto
        // seria tratado como ligado, e o diretor veria o botão desligado com a regra ativa.
        if (SECAO_GESTAO.equals(secao)) {
            for (String chave : List.of("aprovarProfessoresAutomaticamente", CHAVE_APROVACAO_ALUNO)) {
                if (campos.containsKey(chave) && !(campos.get(chave) instanceof Boolean)) {
                    throw ApiException.validation("Valor inválido.", Map.of(chave, "Use verdadeiro ou falso."));
                }
            }
        }
        if ("disponibilidade".equals(secao)) {
            String inicio = String.valueOf(campos.getOrDefault("horarioInicio", "08:00"));
            String fim = String.valueOf(campos.getOrDefault("horarioFim", "18:00"));
            if (inicio.compareTo(fim) >= 0) {
                throw ApiException.validation("Horário inválido.",
                        Map.of("horarioInicio", "O horário de início deve ser anterior ao de fim."));
            }
        }
    }

    private ConfiguracaoUsuario criarPadrao(Usuario usuario) {
        ConfiguracaoUsuario config = new ConfiguracaoUsuario();
        config.setUsuario(usuario);
        config.setJson(escrever(defaultsPara(usuario.getRole())));
        return configuracoes.save(config);
    }

    /**
     * JSON ilegível vira configuração vazia — e então os defaults — em vez de derrubar a
     * tela, mas deixa rastro: o usuário perde as preferências sem aviso, e sem o log não
     * havia como saber que aconteceu nem de quem. Só o id e a classe da exceção vão para o
     * log; a mensagem do Jackson cita trechos do documento.
     */
    private Map<String, Map<String, Object>> ler(String json, Long usuarioId) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("JSON de configuração ilegível para usuario_id={} ({}); usando os padrões.",
                    usuarioId, e.getClass().getSimpleName());
            return new LinkedHashMap<>();
        }
    }

    private String escrever(Map<String, Map<String, Object>> dados) {
        try {
            return mapper.writeValueAsString(dados);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Defaults por perfil, conforme configuracao.md (Task 3). */
    static Map<String, Map<String, Object>> defaultsPara(Role role) {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();
        switch (role) {
            case ALUNO -> {
                m.put("notificacoes", secao("avisoTarefasNovas", true, "lembretesPrazos", true,
                        "mensagensProfessores", false, "notificacoesEmail", true));
                m.put("gamificacao", secao("exibirXpTempoReal", true, "animacoesConquista", true,
                        "rankingTurma", true));
                m.put("estudos", secao("modoFoco", false, "autoAvancarTarefas", true,
                        "lembreteEstudoDiario", true));
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
                m.put("acessibilidade", secao("fonteAmpliada", false, "altoContraste", false,
                        "leituraVozAlta", false));
                // visivelResponsaveis saiu: o sistema não tem conta nem dado de responsável,
                // então a chave não tinha o que controlar.
                m.put("privacidade", secao("perfilPublico", true, "exibirNoRanking", true));
            }
            case PROFESSOR -> {
                m.put("notificacoes", secao("novasEntregasAlunos", true, "mensagensAlunosResponsaveis", true,
                        "lembreteCorrecaoPendente", true, "notificacoesEmail", true));
                m.put("avaliacao", secao("sugestaoCorrecaoAutomatica", false, "exibirNotasImediatamente", true,
                        "permitirReenvioAtividade", false));
                Map<String, Object> disponibilidade = new LinkedHashMap<>();
                disponibilidade.put("aceitarContatoForaHorario", false);
                disponibilidade.put("horarioInicio", "08:00");
                disponibilidade.put("horarioFim", "18:00");
                m.put("disponibilidade", disponibilidade);
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
                // exibirContatoResponsaveis saiu pelo mesmo motivo do visivelResponsaveis do
                // aluno: não existe conta de responsável para ver o contato.
                m.put("privacidade", secao("perfilVisivelAlunos", true));
            }
            case DIRETOR -> {
                m.put("notificacoes", secao("novosCadastrosPendentes", true, "relatoriosPendentes", true,
                        "alertasSistema", true, "notificacoesEmail", true));
                Map<String, Object> institucional = new LinkedHashMap<>();
                institucional.put("nomeInstituicao", "Colégio Nexo");
                institucional.put("anoLetivoAtivo", 2026);
                m.put("institucional", institucional);
                m.put(SECAO_GESTAO, secao("aprovarProfessoresAutomaticamente", false,
                        CHAVE_APROVACAO_ALUNO, true));
                m.put("integracoes", secao("exportarRelatoriosAutomaticamente", false,
                        "sincronizarCalendarioInstitucional", true));
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
                // Sem seção de privacidade: a única chave (exibirDadosInstituicaoPublicamente)
                // não tinha o que controlar — não há página pública da instituição.
            }
            // Seções do dia a dia de quem administra o sistema — não as de gestão do
            // diretor. O trabalho aqui é conta e catálogo; a seção "documentos" da
            // antiga secretaria saiu junto com o checklist de documentos do aluno.
            case ADMIN -> {
                m.put("notificacoes", secao("novasContasCriadas", true, "contasInativas", true,
                        "conteudoDespublicado", true, "notificacoesEmail", true));
                Map<String, Object> catalogo = new LinkedHashMap<>();
                catalogo.put("publicarConteudoNovoAutomaticamente", true);
                catalogo.put("avisarMateriaComMenosDeNConteudos", 3);
                m.put("catalogo", catalogo);
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
            }
        }
        return m;
    }

    private static Map<String, Object> secao(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
