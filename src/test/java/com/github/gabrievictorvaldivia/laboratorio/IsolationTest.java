package com.github.gabrievictorvaldivia.laboratorio;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IsolationTest {
  private static final String URL = "jdbc:postgresql://localhost:5432/spike_db";
  private static final String USER = "postgres";
  private static final String PASS = "password";

  @BeforeEach
  void prepararBancoDeDados() throws SQLException {
    try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS account");
      stmt.execute("CREATE TABLE account (id INT PRIMARY KEY, balance DECIMAL)");
      stmt.execute("INSERT INTO account (id, balance) VALUES (3, 5000.0)"); // Conta inicial com R$ 5000
    }
  }

  @Test
  void deveOcultarAlteracoesNaoCommitadasParaOutrasConexoes() throws SQLException {
    // Abrir duas conexões simultaneas para simular dois usuários ou processos
    // diferentes
    try (Connection connA = DriverManager.getConnection(URL, USER, PASS);
        Connection connB = DriverManager.getConnection(URL, USER, PASS)) {
      // Conexão A inicia uma transaction manual
      connA.setAutoCommit(false);
      try (Statement stmtA = connA.createStatement(); Statement stmtB = connB.createStatement()) {
        // 1. Vamos executar um débito de R$ 1.000 no "rascunho" da conexão A
        stmtA.executeUpdate("UPDATE account SET balance = balance - 1000 WHERE id = 3");

        // O padrão é READ_COMMITED, então ele irá buscar pelo saldo real gravado
        ResultSet rsAntesDoCommit = stmtB.executeQuery("SELECT balance FROM account WHERE id = 3");
        assertTrue(rsAntesDoCommit.next());
        assertEquals(5000.0, rsAntesDoCommit.getDouble(1),
            "Conexão B não deve ver o rascunho (uncommited) da Conexão A");

        // Vamos publicar o "rascunho"
        connA.commit();

        // Agora, vamos ler novamente pós publicação das alterações
        ResultSet rsAposCommit = stmtB.executeQuery("SELECT balance FROM account WHERE id = 3");
        assertTrue(rsAposCommit.next());
        assertEquals(4000.0, rsAposCommit.getDouble(1),
            "Conexão B não deve ver o rascunho (uncommited) da Conexão A");
      }
    }
  }
}
