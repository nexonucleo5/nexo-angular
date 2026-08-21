package com.nexo.service;

import com.nexo.api.ApiException;
import com.nexo.domain.EventoAuditoria;
import com.nexo.domain.Materia;
import com.nexo.domain.Professor;
import com.nexo.domain.Role;
import com.nexo.repository.MateriaRepository;
import com.nexo.repository.ProfessorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Cadastro de professor: dados básicos + matérias que leciona. Reutiliza o
 * mesmo CredenciaisService do cadastro de aluno para gerar o acesso.
 */
@Service
public class ProfessorService {

    /** Não se contrata menor de idade para lecionar. */
    private static final int IDADE_MINIMA_PROFESSOR = 18;
    private static final int IDADE_MAXIMA_PROFESSOR = 100;

    /**
     * Teto de matérias por docente. Sem ele dava para marcar o catálogo inteiro e
     * criar um professor que leciona tudo — o que, além de irreal, desmonta a regra
     * de "só mexe na matéria dele": quem leciona todas não é limitado por nada.
     */
    private static final int MAXIMO_MATERIAS = 3;

    private final ProfessorRepository professores;
    private final MateriaRepository materias;
    private final CredenciaisService credenciais;
    private final AuditoriaService auditoria;

    public ProfessorService(ProfessorRepository professores, MateriaRepository materias,
                            CredenciaisService credenciais, AuditoriaService auditoria) {
        this.professores = professores;
        this.materias = materias;
        this.credenciais = credenciais;
        this.auditoria = auditoria;
    }

    public record CadastroProfessor(String nome, String dataNascimento, String sexo, List<Long> materiaIds) {}

    public record ProfessorCriado(Long id, String nome, String disciplinas, String emailInstitucional,
                                  String senhaProvisoria) {}

    @Transactional
    public ProfessorCriado cadastrar(CadastroProfessor dados, String operador) {
        Map<String, String> erros = new LinkedHashMap<>();

        if (dados.nome() == null || dados.nome().trim().length() < 3) {
            erros.put("nome", "Informe o nome completo.");
        }
        LocalDate nascimento = DataNascimento.validar(dados.dataNascimento(),
                IDADE_MINIMA_PROFESSOR, IDADE_MAXIMA_PROFESSOR, erros);
        if (dados.sexo() == null || dados.sexo().isBlank()) {
            erros.put("sexo", "Selecione o sexo.");
        }

        List<Materia> selecionadas = List.of();
        if (dados.materiaIds() == null || dados.materiaIds().isEmpty()) {
            erros.put("materiaIds", "Selecione ao menos uma matéria.");
        } else if (dados.materiaIds().stream().distinct().count() > MAXIMO_MATERIAS) {
            erros.put("materiaIds", "Um professor leciona no máximo " + MAXIMO_MATERIAS + " matérias.");
        } else {
            selecionadas = materias.findByIdIn(dados.materiaIds());
            if (selecionadas.size() != dados.materiaIds().stream().distinct().count()) {
                erros.put("materiaIds", "Alguma matéria selecionada não existe.");
            }
        }
        if (!erros.isEmpty()) {
            throw ApiException.validation("Dados de cadastro inválidos.", erros);
        }

        Professor professor = new Professor();
        professor.setNome(dados.nome().trim());
        professor.setDataNascimento(nascimento);
        professor.setSexo(dados.sexo());
        professor.setMaterias(new LinkedHashSet<>(selecionadas));
        // Sem foto: o cliente mostra o avatar padrão até o docente enviar a dele.
        professor.setFoto(null);

        var acesso = credenciais.gerar(professor.getNome());
        professor.setEmail(acesso.login());
        professor.setUsuario(credenciais.criarUsuario(professor.getNome(), cargo(professor), Role.PROFESSOR, acesso));
        professores.save(professor);

        auditoria.registrar(operador, EventoAuditoria.Tipo.ALTERACAO,
                "Professor cadastrado", "Professor: " + professor.getNome(), null);

        return new ProfessorCriado(professor.getId(), professor.getNome(), professor.getDisciplinas(),
                acesso.login(), acesso.senhaProvisoria());
    }

    /** Cargo exibido no perfil, no mesmo formato dos docentes já existentes. */
    private static String cargo(Professor professor) {
        String titulo = "F".equalsIgnoreCase(professor.getSexo()) ? "Professora" : "Professor";
        return titulo + " de " + professor.getDisciplinas();
    }
}
