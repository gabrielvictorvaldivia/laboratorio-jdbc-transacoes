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
}
