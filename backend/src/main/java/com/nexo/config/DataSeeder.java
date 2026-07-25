package com.nexo.config;

import com.nexo.domain.*;
import com.nexo.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;

/**
 * Popula o banco de desenvolvimento com dados equivalentes aos mocks
 * que hoje vivem hardcoded nos componentes Angular. Roda apenas com
 * seed.enabled=true e banco vazio.
 */
@Configuration
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true")
public class DataSeeder {

    @org.springframework.context.annotation.Bean
    CommandLineRunner seed(UsuarioRepository usuarios, TurmaRepository turmas, AlunoRepository alunos,
                           ProfessorRepository professores, MatriculaRepository matriculas,
                           FrequenciaRepository frequencias, NotaRepository notas,
                           AvaliacaoRepository avaliacoes, QuestaoRepository questoes,
                           ConversaRepository conversas, MensagemRepository mensagens,
                           AvisoRepository avisos, DuvidaRepository duvidas,
                           ObservacaoPedagogicaRepository observacoes,
                           EventoAuditoriaRepository eventos,
                           AtividadeAlunoRepository atividadesAluno,
                           AulaAgendadaRepository aulasAgendadas,
                           AtividadeProfessorRepository atividadesProfessor,
                           DesafioRepository desafios,
                           DesafioAlunoRepository desafiosAluno,
                           ChatMensagemRepository chatMensagens,
                           PasswordEncoder encoder) {
        return args -> new Seeder(usuarios, turmas, alunos, professores, matriculas, frequencias, notas,
                avaliacoes, questoes, conversas, mensagens, avisos, duvidas, observacoes, eventos,
                atividadesAluno, aulasAgendadas, atividadesProfessor, desafios, desafiosAluno,
                chatMensagens, encoder).executar();
    }

    record Seeder(UsuarioRepository usuarios, TurmaRepository turmas, AlunoRepository alunos,
                  ProfessorRepository professores, MatriculaRepository matriculas,
                  FrequenciaRepository frequencias, NotaRepository notas,
                  AvaliacaoRepository avaliacoes, QuestaoRepository questoes,
                  ConversaRepository conversas, MensagemRepository mensagens,
                  AvisoRepository avisos, DuvidaRepository duvidas,
                  ObservacaoPedagogicaRepository observacoes,
                  EventoAuditoriaRepository eventos,
                  AtividadeAlunoRepository atividadesAluno,
                  AulaAgendadaRepository aulasAgendadas,
                  AtividadeProfessorRepository atividadesProfessor,
                  DesafioRepository desafios,
                  DesafioAlunoRepository desafiosAluno,
                  ChatMensagemRepository chatMensagens,
                  PasswordEncoder encoder) {

        @Transactional
        void executar() {
            if (usuarios.count() > 0) return;

            String senhaPadrao = encoder.encode("123456");

            // ── Usuários dos 3 perfis (mesmos personas do AuthService antigo) ──
            Usuario diretor = usuario("diretor", senhaPadrao, "Diretor Silva", "Administração Escolar", Role.DIRETOR);
            Usuario professorUsr = usuario("professor", senhaPadrao, "Prof. Roberto Alves", "Professor de História", Role.PROFESSOR);
            Usuario alunoUsr = usuario("aluno", senhaPadrao, "Gabriel Mendes", "2º Ano - Ensino Médio", Role.ALUNO);

            Professor professor = new Professor();
            professor.setUsuario(professorUsr);
            professor.setNome(professorUsr.getNome());
            professor.setDisciplina("História");
            professor.setEmail("roberto.alves@nexo.escola.com");
            metricasDocente(professor, "1º A, 2º A, 3º C", 2, 1.5, 51, 4.9, 51, 53);
            professores.save(professor);

            Professor profMatematica = new Professor();
            profMatematica.setNome("Profa. Carla Nunes");
            profMatematica.setDisciplina("Matemática");
            profMatematica.setEmail("carla.nunes@nexo.escola.com");
            metricasDocente(profMatematica, "1º A, 1º B, 2º A", 3, 1.8, 47, 4.8, 47, 50);
            professores.save(profMatematica);

            // Demais docentes do corpo (monitoramento docente)
            professores.save(docente("Profa. Juliana Costa Santos", "Biologia", "juliana.santos@nexo.escola.com",
                    "1º C, 2º C, 3º B", 5, 2.1, 43, 4.6, 43, 48));
            professores.save(docente("Profa. Mariana Oliveira", "Química", "mariana.oliveira@nexo.escola.com",
                    "2º B, 3º B, 3º A", 6, 2.8, 41, 4.4, 44, 50));
            professores.save(docente("Prof. Carlos Eduardo Silva", "Física", "carlos.silva@nexo.escola.com",
                    "2º B, 3º A", 12, 4.2, 28, 4.2, 38, 50));

            // ── Turmas ──
            Turma t9a = turma("9º Ano A", "Manhã");
            Turma t9b = turma("9º Ano B", "Manhã");
            Turma t1em = turma("1º Ano EM", "Tarde");
            Turma t2em = turma("2º Ano EM", "Tarde");

            // Professor logado (Roberto) leciona 3 turmas; Carla leciona a outra
            t9a.setProfessor(professor);
            t9b.setProfessor(professor);
            t2em.setProfessor(professor);
            t1em.setProfessor(profMatematica);
            turmas.save(t9a);
            turmas.save(t9b);
            turmas.save(t2em);
            turmas.save(t1em);

            // ── Alunos ──
            String[][] dadosAlunos = {
                    {"Gabriel Mendes", "39053344705", "2em", "88"},
                    {"Ana Beatriz Souza", "52998224725", "9a", "92"},
                    {"Carlos Eduardo Lima", "11144477735", "9a", "45"},
                    {"Mariana Ferreira", "16899535009", "9a", "78"},
                    {"João Pedro Santos", "71428793860", "9b", "25"},
                    {"Larissa Oliveira", "87748248800", "9b", "83"},
                    {"Rafael Costa", "27332996002", "9b", "52"},
                    {"Beatriz Almeida", "07068093868", "1em", "95"},
                    {"Lucas Martins", "85350996060", "1em", "38"},
                    {"Julia Rodrigues", "35657833039", "1em", "72"},
                    {"Pedro Henrique Silva", "96076994004", "2em", "60"},
                    {"Camila Barbosa", "51867080020", "2em", "90"},
            };

            Random random = new Random(42);
            LocalDate hoje = LocalDate.now();

            for (String[] d : dadosAlunos) {
                Turma turma = switch (d[2]) {
                    case "9a" -> t9a;
                    case "9b" -> t9b;
                    case "1em" -> t1em;
                    default -> t2em;
                };
                Aluno aluno = new Aluno();
                aluno.setNome(d[0]);
                aluno.setCpf(d[1]);
                aluno.setTurma(turma);
                aluno.setEngajamento(Integer.parseInt(d[3]));
                String primeiro = d[0].split(" ")[0].toLowerCase();
                String ultimo = d[0].substring(d[0].lastIndexOf(' ') + 1).toLowerCase();
                aluno.setEmailInstitucional(primeiro + "." + ultimo + "@nexo.escola.com");
                aluno.setEmailResponsavel("resp." + primeiro + "@gmail.com");
                aluno.setTelefoneResponsavel(String.format("(11) 9%04d-%04d",
                        random.nextInt(10000), random.nextInt(10000)));
                aluno.setFoto("assets/imagensProjeto/gabrielZapelini.png");
                aluno.setUltimoAcessoEm(hoje.minusDays(random.nextInt(11)).atStartOfDay(ZoneOffset.UTC).toInstant());
                // Quanto menor o engajamento, mais intervenções pedagógicas registradas
                int qtdIntervencoes = aluno.getEngajamento() < 50 ? 1 + random.nextInt(3) : random.nextInt(2);
                aluno.setIntervencoes(qtdIntervencoes);
                if (qtdIntervencoes > 0) {
                    aluno.setUltimaIntervencaoEm(hoje.minusDays(1 + random.nextInt(8)).atStartOfDay(ZoneOffset.UTC).toInstant());
                }
                // Gamificação: XP correlacionado ao engajamento
                aluno.setXpTotal(1500 + aluno.getEngajamento() * 35 + random.nextInt(400));
                aluno.setXpSemana(300 + random.nextInt(700));
                aluno.setMetaSemanalXp(1000);
                aluno.setOfensivaDias(random.nextInt(14));
                aluno.setTarefasHoje(5);
                aluno.setTarefasFeitasHoje(3 + random.nextInt(3));
                if (d[0].equals("Gabriel Mendes")) aluno.setUsuario(alunoUsr);
                alunos.save(aluno);

                // Matrícula
                Matricula matricula = new Matricula();
                matricula.setAluno(aluno);
                matricula.setTurma(turma);
                int sorte = random.nextInt(10);
                matricula.setStatus(sorte < 7 ? Matricula.Status.ATIVA
                        : sorte < 9 ? Matricula.Status.PENDENTE : Matricula.Status.TRANCADA);
                matricula.setDocumentacao(sorte < 6 ? Matricula.Documentacao.COMPLETA
                        : sorte < 9 ? Matricula.Documentacao.PENDENTE : Matricula.Documentacao.INCOMPLETA);
                matricula.setDataMatricula(hoje.minusMonths(5).plusDays(random.nextInt(30)));
                matriculas.save(matricula);

                // Notas — alunos com engajamento baixo tiram notas menores
                double base = 3.5 + (aluno.getEngajamento() / 100.0) * 6.0;
                for (String disciplina : List.of("História", "Matemática", "Português")) {
                    Nota nota = new Nota();
                    nota.setAluno(aluno);
                    nota.setTurma(turma);
                    nota.setDisciplina(disciplina);
                    nota.setPeriodo("2026-1");
                    nota.setP1(nota10(base + random.nextGaussian()));
                    nota.setP2(nota10(base + random.nextGaussian()));
                    nota.setT1(nota10(base + 0.5 + random.nextGaussian()));
                    nota.setParticipacao(nota10(aluno.getEngajamento() / 10.0));
                    notas.save(nota);
                }

                // Frequência das últimas 6 semanas (dias úteis)
                double chancePresenca = 0.55 + (aluno.getEngajamento() / 100.0) * 0.44;
                for (int dia = 1; dia <= 42; dia++) {
                    LocalDate data = hoje.minusDays(dia);
                    if (data.getDayOfWeek() == DayOfWeek.SATURDAY || data.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
                    Frequencia f = new Frequencia();
                    f.setAluno(aluno);
                    f.setTurma(turma);
                    f.setData(data);
                    f.setPresente(random.nextDouble() < chancePresenca);
                    frequencias.save(f);
                }
            }

            // ── Avaliações ──
            avaliacao("Prova Bimestral — Revolução Industrial", "História", t9a, "Prova",
                    Avaliacao.Status.EM_CORRECAO, hoje.minusDays(3), 28, 12);
            avaliacao("Trabalho — Brasil Colônia", "História", t9b, "Trabalho",
                    Avaliacao.Status.PUBLICADA, hoje.plusDays(4), 10, 0);
            avaliacao("Simulado ENEM — 1ª aplicação", "Geral", t2em, "Simulado",
                    Avaliacao.Status.CORRIGIDA, hoje.minusDays(15), 30, 0);
            avaliacao("Prova — Idade Média", "História", t1em, "Prova",
                    Avaliacao.Status.EM_CORRECAO, hoje.minusDays(1), 25, 25);

            // ── Questões ──
            questao("Explique as principais causas da Primeira Guerra Mundial.", "História",
                    Questao.Tipo.DISSERTATIVA, Questao.Dificuldade.DIFICIL);
            questao("Qual evento marcou o início da Idade Contemporânea?", "História",
                    Questao.Tipo.OBJETIVA, Questao.Dificuldade.MEDIA);
            questao("Resolva: 2x + 5 = 15. Qual o valor de x?", "Matemática",
                    Questao.Tipo.OBJETIVA, Questao.Dificuldade.FACIL);

            // ── Comunicação ──
            Conversa c1 = conversa("Dúvida sobre o trabalho de História", "Ana Beatriz Souza", "Aluna");
            mensagem(c1, "Ana Beatriz Souza", false, "Professor, o trabalho pode ser em dupla?");
            mensagem(c1, professorUsr.getNome(), true, "Pode sim, Ana. Máximo de 2 integrantes.");
            Conversa c2 = conversa("Reunião de responsáveis", "Marcos Lima (responsável)", "Responsável");
            mensagem(c2, "Marcos Lima (responsável)", false, "Boa tarde, gostaria de agendar um horário para conversar sobre o Carlos.");

            aviso("Semana de provas", "As avaliações bimestrais ocorrem de 20 a 24 deste mês. Consultem o calendário.", diretor.getNome(), "Todos");
            aviso("Entrega de trabalhos de História", "Prazo final na sexta-feira, pela plataforma.", professorUsr.getNome(), "9º Ano A");

            Aluno ana = alunos.findAll().stream().filter(a -> a.getNome().startsWith("Ana")).findFirst().orElseThrow();
            Aluno carlos = alunos.findAll().stream().filter(a -> a.getNome().startsWith("Carlos")).findFirst().orElseThrow();

            duvida(ana, "História", "Não entendi a diferença entre mercantilismo e capitalismo.", null, Duvida.Status.ABERTA);
            duvida(carlos, "Matemática", "Como resolvo equações de 2º grau incompletas?",
                    "Use a fatoração ou isole o x². Veja o material da aula 12.", Duvida.Status.RESPONDIDA);

            ObservacaoPedagogica obs = new ObservacaoPedagogica();
            obs.setAluno(carlos);
            obs.setAutorNome(professorUsr.getNome());
            obs.setTexto("Aluno apresentou queda de rendimento no último mês. Encaminhado para acompanhamento.");
            observacoes.save(obs);

            eventos.save(new EventoAuditoria(diretor.getNome(), EventoAuditoria.Tipo.LOGIN, "Login realizado", null, "127.0.0.1"));
            eventos.save(new EventoAuditoria(professorUsr.getNome(), EventoAuditoria.Tipo.ALTERACAO, "Notas lançadas", "Turma 9º Ano A — História", "127.0.0.1"));

            // ── Atividades recentes (gamificação) do aluno logado ──
            Aluno gabriel = alunos.findByUsuarioId(alunoUsr.getId()).orElseThrow();
            Instant agora = Instant.now();
            atividadesAluno.save(new AtividadeAluno(gabriel, "Exercícios de Frações", "Matemática", 150, 100, "🧮", agora.minus(2, ChronoUnit.HOURS)));
            atividadesAluno.save(new AtividadeAluno(gabriel, "Revisão de Ecossistemas", "Biologia", 80, 45, "🧬", agora.minus(5, ChronoUnit.HOURS)));
            atividadesAluno.save(new AtividadeAluno(gabriel, "Redação sobre Brasil Colônia", "História", 200, 70, "📜", agora.minus(1, ChronoUnit.DAYS)));
            atividadesAluno.save(new AtividadeAluno(gabriel, "Quiz de Vocabulário", "Inglês", 120, 90, "🇺🇸", agora.minus(2, ChronoUnit.DAYS)));

            // ── Grade de horários (próximas aulas) do professor Roberto ──
            aulasAgendadas.save(new AulaAgendada(professor, t9a, "História", hoje, "14:00", "204", 3));
            aulasAgendadas.save(new AulaAgendada(professor, t9b, "História", hoje, "15:50", "101", 3));
            aulasAgendadas.save(new AulaAgendada(professor, t2em, "História", hoje.plusDays(1), "08:00", "205", 3));
            aulasAgendadas.save(new AulaAgendada(professor, t9a, "História", hoje.plusDays(1), "10:00", "202", 3));

            // ── Feed de atividades recentes do professor ──
            atividadesProfessor.save(new AtividadeProfessor(professor, "correcao",
                    "Prova Bimestral — 9º Ano A corrigida", "9º Ano A", "bi-pencil-square", "text-purple", agora.minus(2, ChronoUnit.HOURS)));
            atividadesProfessor.save(new AtividadeProfessor(professor, "aviso",
                    "Aviso publicado: Entrega de Trabalho", "9º Ano B", "bi-megaphone", "text-blue", agora.minus(3, ChronoUnit.HOURS)));
            atividadesProfessor.save(new AtividadeProfessor(professor, "diario",
                    "Frequência registrada", "2º Ano EM", "bi-journal-check", "text-green", agora.minus(5, ChronoUnit.HOURS)));
            atividadesProfessor.save(new AtividadeProfessor(professor, "avaliacao",
                    "Nova avaliação criada: Revolução Industrial", "9º Ano A", "bi-file-earmark-plus", "text-orange", agora.minus(1, ChronoUnit.DAYS)));

            // ── Desafios (catálogo + progresso do aluno logado) ──
            Desafio d1 = desafios.save(new Desafio("Quiz: Fotossíntese", "Biologia", "MEDIO", 150, 15));
            Desafio d2 = desafios.save(new Desafio("Funções Trigonométricas", "Matemática", "DIFICIL", 250, 30));
            desafios.save(new Desafio("Present Perfect", "Inglês", "FACIL", 100, 20));
            desafios.save(new Desafio("Revolução Industrial", "História", "MEDIO", 180, 25));
            desafios.save(new Desafio("Ecossistemas e Biomas", "Biologia", "FACIL", 120, 15));
            desafios.save(new Desafio("Derivadas e Integrais", "Matemática", "DIFICIL", 300, 40));

            desafiosAluno.save(new DesafioAluno(gabriel, d1, "CONCLUIDO", 100));
            desafiosAluno.save(new DesafioAluno(gabriel, d2, "PROGRESSO", 40));

            // ── Chat inicial professor ↔ diretor ──
            chatMensagens.save(new ChatMensagem(professorUsr.getId(), professorUsr.getNome(), diretor.getId(),
                    "Diretor, poderia liberar a sala 204 para a aula de reforço?", agora.minus(3, ChronoUnit.HOURS)));
            chatMensagens.save(new ChatMensagem(diretor.getId(), diretor.getNome(), professorUsr.getId(),
                    "Claro, professor. Sala reservada para quinta às 16h.", agora.minus(2, ChronoUnit.HOURS)));
        }

        private Usuario usuario(String login, String hash, String nome, String cargo, Role role) {
            Usuario u = new Usuario();
            u.setLogin(login);
            u.setSenhaHash(hash);
            u.setNome(nome);
            u.setCargo(cargo);
            u.setFoto("assets/imagensProjeto/gabrielZapelini.png");
            u.setRole(role);
            return usuarios.save(u);
        }

        private Turma turma(String nome, String turno) {
            Turma t = new Turma();
            t.setNome(nome);
            t.setAnoLetivo(2026);
            t.setTurno(turno);
            return turmas.save(t);
        }

        /** Aplica as métricas de produtividade + foto padrão a um docente. */
        private void metricasDocente(Professor p, String turmas, int pendentes, double tempo,
                                     int interacoes, double avaliacao, int concluidas, int total) {
            p.setFoto("assets/imagensProjeto/gabrielZapelini.png");
            p.setTurmas(turmas);
            p.setCorrecoesPendentes(pendentes);
            p.setTempoRespostaDias(tempo);
            p.setInteracoesSemana(interacoes);
            p.setAvaliacao(avaliacao);
            p.setTarefasConcluidas(concluidas);
            p.setTarefasTotal(total);
        }

        private Professor docente(String nome, String disciplina, String email, String turmas,
                                  int pendentes, double tempo, int interacoes, double avaliacao,
                                  int concluidas, int total) {
            Professor p = new Professor();
            p.setNome(nome);
            p.setDisciplina(disciplina);
            p.setEmail(email);
            metricasDocente(p, turmas, pendentes, tempo, interacoes, avaliacao, concluidas, total);
            return p;
        }

        private void avaliacao(String titulo, String disciplina, Turma turma, String tipo,
                               Avaliacao.Status status, LocalDate data, int entregas, int pendentes) {
            Avaliacao a = new Avaliacao();
            a.setTitulo(titulo);
            a.setDisciplina(disciplina);
            a.setTurma(turma);
            a.setTipo(tipo);
            a.setStatus(status);
            a.setData(data);
            a.setEntregas(entregas);
            a.setPendentesCorrecao(pendentes);
            avaliacoes.save(a);
        }

        private void questao(String enunciado, String disciplina, Questao.Tipo tipo, Questao.Dificuldade dif) {
            Questao q = new Questao();
            q.setEnunciado(enunciado);
            q.setDisciplina(disciplina);
            q.setTipo(tipo);
            q.setDificuldade(dif);
            questoes.save(q);
        }

        private Conversa conversa(String assunto, String participante, String papel) {
            Conversa c = new Conversa();
            c.setAssunto(assunto);
            c.setParticipanteNome(participante);
            c.setParticipantePapel(papel);
            c.setCaixa(Conversa.Caixa.ENTRADA);
            return conversas.save(c);
        }

        private void mensagem(Conversa conversa, String autor, boolean minha, String texto) {
            Mensagem m = new Mensagem();
            m.setConversa(conversa);
            m.setAutorNome(autor);
            m.setMinha(minha);
            m.setTexto(texto);
            m.setLida(minha);
            mensagens.save(m);
        }

        private void aviso(String titulo, String conteudo, String autor, String destino) {
            Aviso a = new Aviso();
            a.setTitulo(titulo);
            a.setConteudo(conteudo);
            a.setAutorNome(autor);
            a.setDestino(destino);
            avisos.save(a);
        }

        private void duvida(Aluno aluno, String disciplina, String pergunta, String resposta, Duvida.Status status) {
            Duvida d = new Duvida();
            d.setAluno(aluno);
            d.setDisciplina(disciplina);
            d.setPergunta(pergunta);
            d.setResposta(resposta);
            d.setStatus(status);
            if (status == Duvida.Status.RESPONDIDA) d.setRespondidaEm(java.time.Instant.now());
            duvidas.save(d);
        }

        private static double nota10(double v) {
            return Math.max(0, Math.min(10, Math.round(v * 10.0) / 10.0));
        }
    }
}
