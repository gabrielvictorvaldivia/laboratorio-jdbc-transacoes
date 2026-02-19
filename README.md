# Technical Spike — Conexões JDBC, Controle Transacional e Pool Manual em Java Puro

> ⚠️ **Este repositório é um laboratório técnico descartável por design.**
> Não há intenção de produção, escalabilidade ou manutenção a longo prazo.
> O objetivo é investigar comportamento real, falhas e trade-offs — não construir um sistema.

---

## O que é um Technical Spike?

Um *technical spike* é um experimento de duração limitada cujo único entregável é **conhecimento verificável**. Não existe código de produção ao final — existe compreensão documentada com evidências coletadas em condições controladas.

No contexto de sistemas transacionais, isso significa: antes de confiar em qualquer framework para gerenciar conexões e transações, você investiga o que está acontecendo abaixo da abstração. Você provoca o sistema, observa as falhas, mede as consequências.

---

## Por que estudar JDBC sem frameworks é relevante?

Há uma suposição comum entre desenvolvedores Java de que JDBC é "coisa do passado" — substituído por JPA, Hibernate, Spring Data e outros. Essa suposição é cara quando algo dá errado.

Todo ORM do ecossistema Java fala com o banco de dados via JDBC. Todo pool de conexões que você usa — HikariCP, DBCP, C3P0 — implementa `javax.sql.DataSource` e entrega `java.sql.Connection`. Quando uma transação falha silenciosamente, quando um leak de conexão satura o pool, quando um deadlock congela sua aplicação em produção, o problema está na camada JDBC. E sem entender essa camada, você depende de logs e stack traces para adivinhar o que aconteceu.

Além disso, JDBC não é tecnologia legada — é uma **especificação ativa e estável** que define como aplicações Java se comunicam com bancos de dados relacionais. Java 21 usa a mesma interface `Connection` que Java 1.1 usou. Essa estabilidade é uma propriedade, não uma limitação.

Estudar JDBC sem ORM expõe trade-offs que frameworks escondem: o custo real de criar uma conexão, o que `setAutoCommit(false)` realmente faz no banco, o que acontece com locks quando uma transação fica aberta por tempo demais, e por que `Connection.close()` dentro de um pool não fecha nada.

---

## Qual fenômeno transacional está sendo estudado?

O spike investiga três fenômenos interdependentes:

**Ciclo de vida de uma conexão JDBC** — o que acontece no nível do protocolo quando `DriverManager.getConnection()` é chamado, qual é o custo real dessa operação, e o que acontece quando a conexão não é fechada corretamente.

**Controle manual de transações** — a diferença real e observável entre `autoCommit=true` e `autoCommit=false`, como `commit()` e `rollback()` afetam durabilidade e consistência, e o que acontece com dados parcialmente escritos quando uma falha ocorre no meio de uma sequência de operações.

**Comportamento sob concorrência** — como múltiplas transações simultâneas interagem, o que os níveis de isolamento realmente garantem e custam, como deadlocks se formam, e quais anomalias (dirty reads, non-repeatable reads, phantom reads) são observáveis sob cada nível de isolamento.

O pool de conexões manual serve como veículo para entender por que pools existem e quais problemas eles introduzem — não como objetivo em si.

---

## Por que Java puro, sem frameworks?

A decisão de excluir Hibernate, JPA, Spring e pools prontos é intencional e pedagógica. Cada camada de abstração que você adiciona esconde um comportamento. O objetivo é tornar o comportamento visível.

Sem JPA, você vê exatamente qual SQL é executado e quando. Sem HikariCP, você vê o custo de criar e destruir conexões. Sem `@Transactional`, você controla manualmente cada `commit()` e `rollback()` e observa o que acontece quando você esquece um. Sem tratamento automático de erros, você vê como exceções durante transações deixam o banco em estados parciais.

O aprendizado está na fricção deliberada.

---

## O papel do TDD como ferramenta de investigação

Neste contexto, TDD não serve primariamente para design de software — serve para **delimitar o que é verificável com precisão e o que exige um banco de dados real funcionando**.

Essa distinção produz dois conjuntos de testes com características completamente diferentes:

**Testes unitários** cobrem lógica determinística e local: a política de alocação do pool, o algoritmo de detecção de conexões expiradas, validação de estado interno, comportamento da fila de espera quando o pool está cheio. Esses testes são rápidos, isolados e repetíveis.

**Testes de integração** cobrem comportamento emergente que exige banco real: o que o banco faz quando recebe um `ROLLBACK`, quais locks são adquiridos em qual nível de isolamento, como o banco responde a uma conexão que morreu abruptamente, o que aparece em `pg_stat_activity` durante uma transação aberta.

A fronteira entre essas duas categorias é o ponto de maior aprendizado do spike. TDD torna essa fronteira explícita e forçada — você não pode escrever um teste unitário para deadlock real sem perceber que está simulando o banco, e uma simulação de banco não é um banco.

---

## O que observar neste laboratório?

Ao longo dos experimentos, o leitor técnico deve ser capaz de observar e registrar:

- O tempo real de estabelecimento de uma conexão JDBC (TCP handshake + autenticação + negociação de protocolo)
- O estado de dados no banco durante uma transação não commitada — visível para a própria conexão, invisível para outras
- O comportamento do banco quando uma conexão é fechada com transação aberta
- Como diferentes níveis de isolamento produzem comportamentos distintos e mensuráveis para as mesmas queries
- O estado de travamento no banco (`pg_locks`, `information_schema`) durante deadlocks provocados intencionalmente
- A diferença entre uma conexão "fechada" para a aplicação e uma conexão realmente encerrada no servidor de banco
- Como o pool manual se comporta quando todas as conexões estão em uso e um novo pedido chega

---

## Infraestrutura mínima necessária

- JDK 21+
- PostgreSQL ou MySQL rodando localmente via Docker (para isolamento e reproducibilidade)
- Driver JDBC correspondente ao banco escolhido, adicionado ao classpath via Maven ou Gradle
- JUnit 5 para a suite de testes, com source sets separados para unitários e integração
- Acesso ao terminal do banco para observar estado interno em tempo real (`psql`, `pg_stat_activity`, `SHOW PROCESSLIST`, `SHOW ENGINE INNODB STATUS`)

---

## Sobre este laboratório

Este spike não produz código de produção. Ele produz engenheiros que entendem o que acontece quando `@Transactional` funciona — e o que acontece quando não funciona. Essa compreensão não é substituível por documentação de framework.