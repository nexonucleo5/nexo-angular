package com.nexo.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.api.ApiException;
import com.nexo.domain.ConfiguracaoUsuario;
import com.nexo.domain.Role;
import com.nexo.domain.Usuario;
import com.nexo.repository.ConfiguracaoUsuarioRepository;
import com.nexo.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configurações por usuário, particionadas por seção (espelha o ConfiguracoesService do Angular).
 * O documento completo é a fonte de verdade do servidor; o PATCH faz merge raso por seção.
 */
@Service
public class ConfiguracaoService {

    private final ConfiguracaoUsuarioRepository configuracoes;
    private final UsuarioRepository usuarios;
    private final ObjectMapper mapper;

    public ConfiguracaoService(ConfiguracaoUsuarioRepository configuracoes,
                               UsuarioRepository usuarios,
                               ObjectMapper mapper) {
        this.configuracoes = configuracoes;
        this.usuarios = usuarios;
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
        Map<String, Map<String, Object>> salvas = ler(config.getJson());
        salvas.forEach((secao, campos) -> defaults.merge(secao, campos, (d, s) -> {
            d.putAll(s);
            return d;
        }));
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

        Map<String, Map<String, Object>> atual = ler(config.getJson());
        Map<String, Object> campos = atual.computeIfAbsent(secao, s -> new LinkedHashMap<>(defaults.get(secao)));
        campos.putAll(patch);

        validar(secao, campos);

        config.setJson(escrever(atual));
        config.setAtualizadaEm(Instant.now());
        configuracoes.save(config);
        return obter(usuarioId);
    }

    /** Validações de negócio reforçadas no servidor (ex.: início < fim na Disponibilidade). */
    private void validar(String secao, Map<String, Object> campos) {
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

    private Map<String, Map<String, Object>> ler(String json) {
        try {
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Map<String, Object>>>() {});
        } catch (Exception e) {
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
                m.put("privacidade", secao("perfilPublico", true, "exibirNoRanking", true,
                        "visivelResponsaveis", true));
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
                m.put("privacidade", secao("perfilVisivelAlunos", true, "exibirContatoResponsaveis", false));
            }
            case DIRETOR -> {
                m.put("notificacoes", secao("novosCadastrosPendentes", true, "relatoriosPendentes", true,
                        "alertasSistema", true, "notificacoesEmail", true));
                Map<String, Object> institucional = new LinkedHashMap<>();
                institucional.put("nomeInstituicao", "Colégio Nexo");
                institucional.put("anoLetivoAtivo", 2026);
                m.put("institucional", institucional);
                m.put("gestao", secao("aprovarProfessoresAutomaticamente", false,
                        "exigirAprovacaoManualAluno", true));
                m.put("integracoes", secao("exportarRelatoriosAutomaticamente", false,
                        "sincronizarCalendarioInstitucional", true));
                m.put("aparencia", secao("temaEscuro", true, "animacoesInterface", true));
                m.put("privacidade", secao("exibirDadosInstituicaoPublicamente", false));
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
