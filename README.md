# Agenda Fit — projeto base do Diamante

API de grade de aulas de uma academia em Spring Boot. Este é o **ponto de partida** do Projeto Diamante:
o CRUD de aulas já funciona, e a sua equipe acrescenta paginação, buscas, HATEOAS, documentação
e segurança com JWT e roles.

## O que já vem pronto

- Entidades `User`, `GymClass` e `Enrollment` e os enums `Role`, `Modality` e `Level`
- `DataLoader` com 60 aulas nos próximos 10 dias, inscrições de exemplo e 3 usuários
- CRUD em `/classes` devolvendo JSON simples, sem paginação
- Validação e erros no formato `{"error": "..."}` (`GlobalExceptionHandler`)
- `SecurityConfig` com tudo liberado e o `PasswordEncoder` (BCrypt)
- Secret do JWT no `application.yml` (`jwt.secret` e `jwt.expiration-seconds`)
- Dependências de Security, OAuth2 Resource Server, HATEOAS e springdoc no `pom.xml`
- H2 em memória e CORS liberado

Stack: Java 25, Spring Boot 4.1 e Maven.

## Usuários do DataLoader

| E-mail | Senha | Role |
|--------|-------|------|
| admin@fit.com | admin123 | ADMIN |
| ana@fit.com | ana123 | USER |
| bruno@fit.com | bruno123 | USER |

As senhas estão gravadas com BCrypt. As aulas com capacidade 2 já começam lotadas.

## Como rodar

Abra a pasta no IntelliJ como projeto Maven e execute `AgendaFitApplication`,
ou rode `mvn spring-boot:run` no terminal. A API sobe em http://localhost:8080.

O arquivo `test.http` tem exemplos das rotas do CRUD.

Para conferir o contrato, use o app: https://github.com/joaocarloslima/agenda-fit-app

## Equipe

| Nome | RM |
|------|----|
|      |    |
