# Nexo Backend (Java/Spring)

Backend definido pela Task 2 (`nexo/arquitetura_java.md`): Spring Boot 3.5, JWT com refresh token rotacionado, H2 em arquivo (dev) e todos os endpoints do contrato.

## Requisitos
- JDK 17+ (testado com JDK 25)
- Maven 3.9+ (ou o wrapper da sua IDE)

## Rodando

```bash
cd backend
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`. Na primeira execução, o seed (`nexo.seed.enabled=true`) popula o banco com dados equivalentes aos mocks do Angular.

### Usuários de desenvolvimento (senha: `123456`)
| Login       | Role      | Nome                |
|-------------|-----------|---------------------|
| `aluno`     | ALUNO     | Gabriel Mendes      |
| `professor` | PROFESSOR | Prof. Roberto Alves |
| `diretor`   | DIRETOR   | Diretor Silva       |

Alunos cadastrados via `POST /api/alunos` recebem e-mail institucional (`nome.sobrenome@nexo.escola.com`) e senha provisória gerados **no servidor**.

## Estrutura
```
com.nexo
├── api/          envelopes padrão (paginação, erro) e DTOs compartilhados
├── config/       SecurityConfig (JWT stateless + CORS) e DataSeeder
├── domain/       entidades JPA (Usuario, Aluno, Turma, Matricula, Frequencia, ...)
├── repository/   Spring Data JPA
├── security/     JwtService, filtro Bearer, principal autenticado
├── service/      regras de negócio (auth, cadastro, evasão, relatórios, configurações)
└── web/          controllers REST (contrato do arquitetura_java.md)
```

## Convenções do contrato
- Listagens paginadas: `{ content, page, size, totalElements, totalPages }`
- Erros: `{ timestamp, status, error, message, fields }`
- Datas em ISO-8601 UTC — formatação fica no Angular
- Autorização por role via `@PreAuthorize` (`ALUNO`, `PROFESSOR`, `DIRETOR`)

## Banco
H2 em arquivo (`backend/data/nexo.mv.db`), console em `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:file:./data/nexo`, usuário `nexo`, senha `nexo`). Para produção, trocar o datasource por PostgreSQL/MySQL — as entidades usam apenas JPA padrão.
