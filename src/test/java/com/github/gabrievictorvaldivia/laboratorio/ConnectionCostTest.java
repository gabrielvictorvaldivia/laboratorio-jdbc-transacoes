package com.github.gabrievictorvaldivia.laboratorio;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

public class ConnectionCostTest {

	@Test
	void deveEstabelecerUmaConexaoComOBanco() throws SQLException {
		String url = "jdbc:postgresql://localhost:5432/lab_jpa";
		String user = "postgres";
		String password = "password";

		Connection connection = DriverManager.getConnection(url, user, password);

		assertNotNull(connection);
	}

	@Test
	void deveMedirOCustoDeCriarNovasConexoesVersusReaproveitar() throws SQLException {
		String url = "jdbc:postgresql://localhost:5432/lab_jpa";
		String user = "postgres";
		String password = "password";
		int iteracoes = 100;

		// Cenário A: abrir e fechar 100 vezes (i.e: Pedir 100 ubers diferentes).
		long inicioCenarioA = System.currentTimeMillis();
		for (int i = 0; i < iteracoes; i++) {
			try (Connection conn = DriverManager.getConnection(url, user, password);
					var stmt = conn.createStatement();
					var rs = stmt.executeQuery("SELECT 1")) {
				rs.next(); // Itera o resultado para garantir execução
			}
		}
		long duracaoCenarioA = System.currentTimeMillis() - inicioCenarioA;

		// Cenário B: Abrir 1 vez, executar 100 vezez (i.e: Andar 100 quarteirões no
		// mesmo Uber).
		long inicioCenarioB = System.currentTimeMillis();
		try (Connection conn = DriverManager.getConnection(url, user, password)) {
			for (int i = 0; i < iteracoes; i++) {
				try (var stmt = conn.createStatement(); var rs = stmt.executeQuery("SELECT 1")) {
					rs.next();
				}
			}
		}
		long duracaoCenarioB = System.currentTimeMillis() - inicioCenarioB;

		System.out.println("Tempo total para 100 conexões novas: " + duracaoCenarioA + " ms");
		System.out.println("Tempo total para 1 conexão reaproveitada: " + duracaoCenarioB + " ms");

		// Asserção: Comprovando a Hipótese 1 (H1)
		// Reaproveitar DEVE ser brutalmente mais rápido (estamos assumindo pelo menos
		// 10x, ou seja, 1 ordem de grandeza)
		assertTrue(duracaoCenarioA > (duracaoCenarioB * 10),
				"O custo de criar conexões deve ser pelo menos uma ordem de grandeza maior");
	}

}
