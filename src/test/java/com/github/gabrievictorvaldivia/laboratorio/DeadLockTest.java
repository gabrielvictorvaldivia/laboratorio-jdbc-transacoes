package com.github.gabrievictorvaldivia.laboratorio;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DeadLockTest {
  private static final String URL = "jdbc:postgresql://localhost:5432/spike_db";
  private static final String USER = "postgres";
  private static final String PASS = "password";

  @BeforeEach
  void prepararBancoDeDados() throws SQLException {
    try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
        Statement stmt = conn.createStatement()) {
      stmt.execute("DROP TABLE IF EXISTS account");
      stmt.execute("CREATE TABLE account (id INT PRIMARY KEY, balance DECIMAL)");
      // Criaremos 2 contas com R$ 1000,00 cada
      stmt.execute("INSERT INTO account (id, balance) VALUES (1, 1000.0), (2, 1000.0)");
    }
  }

  @Test
  void deveProvocarEDetectarUmDeadlockEntreDuasTransacoes() throws InterruptedException {
    // Usando Latches para sincronizar as threads perfeitamente
    CountDownLatch locksIniciaisAdquiridos = new CountDownLatch(2);

    // Capturar a exceção que ocorrerá dentro de uma das threads
    AtomicReference<SQLException> excecaoCapturada = new AtomicReference<SQLException>();

    Runnable transacao1 = () -> {
      try (Connection conn = DriverManager.getConnection(URL, USER, PASS); Statement stmt = conn.createStatement()) {
        conn.setAutoCommit(false);

        // Lock na conta 1
        stmt.executeUpdate("UPDATE account SET balance = balance + 10 WHERE id =1");
        locksIniciaisAdquiridos.countDown(); // Avisa que pegou o lock
        locksIniciaisAdquiridos.await(); // Espera T2 pegar o lock dela

        // Assim que transacao 2 pegar o lock dela, transacao1 vai tentar entrar aqui e
        // atualizar saldo da conta 2
        stmt.executeUpdate("UPDATE account SET balance = balance - 10 WHERE id = 2");
        conn.commit();
      } catch (SQLException e) {
        excecaoCapturada.set(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };

    Runnable transacao2 = () -> {
      try (Connection conn = DriverManager.getConnection(URL, USER, PASS); Statement stmt = conn.createStatement()) {
        conn.setAutoCommit(false);
        // Aqui adquirimos lock na conta 2
        stmt.executeUpdate("UPDATE account SET balance = balance + 10 WHERE id =2");
        locksIniciaisAdquiridos.countDown(); // Avisa que pegou o lock
        locksIniciaisAdquiridos.await(); // Espera T1 pegar o lock dela

        // esta transacao tenta adquirir lock na conta 1 -> DEADLOCK!
        stmt.executeUpdate("UPDATE account SET balance = balance - 10 WHERE id = 1");
        conn.commit();
      } catch (SQLException e) {
        excecaoCapturada.set(e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    };

    // Disparando as duas threads simultaneamente
    Thread thread1 = new Thread(transacao1);
    Thread thread2 = new Thread(transacao2);
    thread1.start();
    thread2.start();

    // Aguardando ambas terminarem (uma irá commitar a outra morrer pelo banco).
    thread1.join();
    thread2.join();

    SQLException erro = excecaoCapturada.get();
    assertTrue(!Objects.isNull(erro), "Uma das transações DEVE ter falhado com SQLException.");

    // 40P01 é o código de falha (SQLState) para Deadlock que devemos buscar
    assertEquals("40P01", erro.getSQLState(),
        "O banco deve identificar a falha explicitamente como um Deadlock");
  }

  @Test
  void devePrevenirDeadlockOrdenandoOsRecursosNumericamente() throws InterruptedException {
    CountDownLatch largada = new CountDownLatch(1);
    AtomicReference<SQLException> excecaoCapturada = new AtomicReference<SQLException>();

    Runnable transacao1 = () -> {
      try (Connection conn = DriverManager.getConnection(URL, USER, PASS); Statement stmt = conn.createStatement()) {
        conn.setAutoCommit(false);

        largada.await(); // fica na linha de largada

        // Lock na conta de id = 1 (menor)
        stmt.executeUpdate("UPDATE account SET balance = balance - 10 WHERE id =1");
        Thread.sleep(50);

        // Lock na conta de id = 2 (maior)
        stmt.executeUpdate("UPDATE account SET balance = balance + 10 WHERE id = 2");
        conn.commit();
      } catch (Exception e) {
        if (e instanceof SQLException)
          excecaoCapturada.set((SQLException) e);
      }
    };

    Runnable transacao2 = () -> {
      try (Connection conn = DriverManager.getConnection(URL, USER, PASS); Statement stmt = conn.createStatement()) {
        conn.setAutoCommit(false);

        largada.await();

        // transaction2 também tentando lockar o menor primeiro
        // Se transaction1 chegou primeiro, transaction2 aguarda a liberação pois não há
        // empate
        stmt.executeUpdate("UPDATE account SET balance = balance + 10 WHERE id =1");

        // transaction2 locka o maior
        stmt.executeUpdate("UPDATE account SET balance = balance - 10 WHERE id = 2");
        conn.commit();
      } catch (Exception e) {
        if (e instanceof SQLException)
          excecaoCapturada.set((SQLException) e);
      }
    };

    Thread thread1 = new Thread(transacao1);
    Thread thread2 = new Thread(transacao2);
    thread1.start();
    thread2.start();

    largada.countDown();

    thread1.join();
    thread2.join();

    assertTrue(Objects.isNull(excecaoCapturada.get()), "Com a ordenação estrita, o deadlock não ocorre");
  }
}
