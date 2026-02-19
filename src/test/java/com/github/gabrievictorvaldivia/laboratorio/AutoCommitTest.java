package com.github.gabrievictorvaldivia.laboratorio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AutoCommitTest {
	private static final String URL = "jdbc:postgresql://localhost:5432/spike_db";
	private static final String USER = "postgres";
	private static final String PASS = "password";

	@BeforeEach
	void prepararBancoDeDados() throws SQLException {
		try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
				Statement stmt = conn.createStatement()) {
			// YAGNI: Recriamos a tabela a cada teste para garantir isolamento e
			// reproducibilidade
			stmt.execute("DROP TABLE IF EXISTS account");
			stmt.execute("CREATE TABLE account (id INT PRIMARY KEY, balance DECIMAL)");
			stmt.execute("INSERT INTO account (id, balance) VALUES (1, 1000.0), (2, 1000.0)");
		}
	}

	@Test
	void deveDeixarEstadoInconsistenteQuandoFalhaOcorreComAutoCommitTrue() throws SQLException {
		// Tentativa de transferir R$ 100,00 da Conta 1 para a Conta 2
		try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
				Statement stmt = conn.createStatement()) {

			// 1. Débito (O banco commita isso instantaneamente porque autoCommit=true)
			stmt.executeUpdate("UPDATE account SET balance = balance - 100 WHERE id = 1");

			// 2. Simulação de falha catastrófica (ex: cabo de rede puxado, Exception)
			boolean falhaInjetada = true;
			if (falhaInjetada) {
				throw new RuntimeException("Sistema crashou antes de creditar o destinatário!");
			}

			// 3. Crédito - Esperamos que o sistema pule isto de propósito
			stmt.executeUpdate("UPDATE account SET balance = balance + 100 WHERE id = 2");

		} catch (RuntimeException e) {
			// Capturamos a falha silenciosamente apenas para o teste continuar
		}

		// Verificando como ficou o banco de dados após a falha
		double saldoConta1 = 0;
		double saldoConta2 = 0;

		try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
				Statement stmt = conn.createStatement()) {

			ResultSet rs1 = stmt.executeQuery("SELECT balance FROM account WHERE id = 1");
			if (rs1.next())
				saldoConta1 = rs1.getDouble(1);

			ResultSet rs2 = stmt.executeQuery("SELECT balance FROM account WHERE id = 2");
			if (rs2.next())
				saldoConta2 = rs2.getDouble(1);
		}

		// Asserções que documentam a anomalia (Hipótese 2):
		assertEquals(900.0, saldoConta1, "O dinheiro SAIU da conta 1 de forma permanente");
		assertEquals(1000.0, saldoConta2, "O dinheiro NUNCA CHEGOU na conta 2");
		assertEquals(1900.0, saldoConta1 + saldoConta2, "Inconsistência matemática: R$ 100 evaporaram do sistema");
	}
}
