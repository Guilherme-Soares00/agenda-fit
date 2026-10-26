# Agenda Fit API

API REST para consultar aulas de academia, gerenciar inscrições e administrar a grade com autenticação JWT e perfis USER e ADMIN.

## Tecnologias

Java 25, Spring Boot 4.1.1, Maven, Spring MVC, Spring Data JPA, Spring HATEOAS, Spring Security, OAuth2 Resource Server, Bean Validation, springdoc OpenAPI e H2 em memória.

## Integrantes

| Nome | RM |
|---|---|
|Guilherme Soares |RM568227 |
|Gabriel Stuani |RM566682 |

## Como executar

Pré-requisitos: JDK 25 e Maven.

```bash
mvn spring-boot:run
```

No IntelliJ IDEA, abra o `pom.xml` como projeto Maven, utilize o JDK 25 e execute `br.com.fiap.agendafit.AgendaFitApplication`.

A aplicação inicia em http://localhost:8080 sem variáveis de ambiente, Docker ou configuração de banco. O H2 é recriado a cada execução, com 60 aulas nos próximos 10 dias, inscrições de exemplo e três usuários. As aulas com capacidade 2 começam lotadas.

## Documentação

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI: http://localhost:8080/v3/api-docs

No Swagger, execute o login e informe o token no botão **Authorize**. As operações protegidas utilizam `Authorization: Bearer <token>`.

## Aplicativo de teste

Frontend React Native/Expo: [Agenda Fit App](https://github.com/joaocarloslima/agenda-fit-app).

## Usuários de teste

| E-mail | Senha | Role |
|---|---|---|
| admin@fit.com | admin123 | ADMIN |
| ana@fit.com | ana123 | USER |
| bruno@fit.com | bruno123 | USER |

As senhas são armazenadas com BCrypt. O secret em `application.yml` e as contas iniciais são exclusivos para desenvolvimento e demonstração. Em outro ambiente, substitua o secret e as credenciais antes de disponibilizar a aplicação.

## Autenticação

```bash
curl -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' -d '{"name":"Student","email":"student@fit.com","password":"student123"}'
curl -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"email":"student@fit.com","password":"student123"}'
```

O cadastro sempre cria USER. E-mails são normalizados para minúsculas. O login devolve `token` e `expiresIn`, um número em segundos com valor padrão `3600`. O JWT HS256 contém `sub`, `name`, `role`, `iat` e `exp`. A senha aceita no máximo 72 bytes UTF-8, limite do BCrypt, sem truncamento silencioso.

O arquivo `test.http` permite autenticar o administrador pelo cliente HTTP do IntelliJ.

## Endpoints

| Método | Rota | Acesso | Sucesso |
|---|---|---|---|
| POST | `/auth/register` | Público | 201 |
| POST | `/auth/login` | Público | 200 |
| GET | `/classes` | Público | 200 |
| GET | `/classes/example` | Público | 200 |
| GET | `/classes/search` | Público | 200 |
| GET | `/classes/{id}` | Público | 200 |
| POST | `/classes` | ADMIN | 201 e Location |
| PUT | `/classes/{id}` | ADMIN | 200 |
| DELETE | `/classes/{id}` | ADMIN | 204 |
| POST | `/classes/{id}/enrollments` | Autenticado | 201 |
| GET | `/enrollments/me` | Autenticado | 200 |
| DELETE | `/enrollments/{id}` | Dono ou ADMIN | 204 |

## Paginação, filtros e links

As listagens utilizam `page` e `size`, começando na página 0, com tamanho padrão 10 e máximo 100. Aulas são ordenadas por `startsAt`; inscrições, por `createdAt`. A ordenação pode ser ajustada com `sort=campo,asc` ou `sort=campo,desc`.

- Aulas: `id`, `title`, `modality`, `level`, `instructor`, `startsAt`, `durationMinutes`, `capacity`.
- Inscrições: `id`, `createdAt`, `gymClass.startsAt`.

`/classes/example` aceita `modality`, `level` e `instructor`, com igualdade e nome do instrutor sem distinção de caixa. `/classes/search` aceita `title` por conteúdo sem distinção de caixa, `modality`, `level`, `startsAfter`, `startsBefore` e `availableOnly=true`. Datas são locais ISO, com limites inclusivos.

```bash
curl 'http://localhost:8080/classes?page=0&size=10&sort=startsAt,asc'
curl 'http://localhost:8080/classes/example?modality=YOGA&level=BEGINNER&instructor=carla%20mendes'
curl 'http://localhost:8080/classes/search?title=yoga&availableOnly=true&size=5'
```

Modalidades: `YOGA`, `PILATES`, `SPINNING`, `FUNCTIONAL`, `DANCE`. Níveis: `BEGINNER`, `INTERMEDIATE`, `ADVANCED`.

As respostas usam HAL: `_embedded.classes` ou `_embedded.enrollments`, `_links` e `page`. Páginas vazias não possuem `_embedded`. Links de navegação preservam os filtros. A API processa Bearer também em consultas públicas, expondo `enroll`, `update`, `delete` e `create` somente quando permitidos. Inscrições possuem links `class` e `cancel`, sendo este condicionado ao dono ou ADMIN.

O CORS permite clientes web sem cookies, incluindo cabeçalhos de autorização e exposição de `Location`. Clientes mobile devem usar um endereço da API acessível ao dispositivo.

## Consistência e erros

Inscrições repetidas e aulas lotadas retornam 409. A inscrição utiliza bloqueio pessimista na aula e unicidade por usuário/aula. Atualizações não podem reduzir a capacidade abaixo das inscrições existentes. Excluir uma aula remove suas inscrições na mesma transação.

As respostas de erro seguem `{"error":"..."}`: 400 para entrada inválida, 401 para autenticação ausente ou inválida, 403 para acesso proibido, 404 para recurso inexistente e 409 para conflito. Falhas inesperadas retornam uma mensagem genérica.

## Testes

```bash
mvn clean verify
```

Os testes exercitam HTTP real em porta aleatória, JWT, segurança, cadastro concorrente, CRUD, paginação, QBE, Specifications, HAL, autorização por dono, concorrência pela última vaga e quantidade de consultas para evitar N+1.

## Estrutura

```text
br.com.fiap.agendafit
├── assembler
├── config
├── controller
├── data
├── dto
├── exception
├── model
├── repository
├── security
└── service
```
