package com.coinmind.trade.futures;

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;

/** Dedicated integration-test connection; never accepts credentials embedded in URLs. */
final class FuturesArchiveTestConnection {
    private FuturesArchiveTestConnection() {}

    static ConnectionFactory create() {
        String url = System.getenv("FUTURES_ARCHIVE_TEST_R2DBC_URL");
        String user = System.getenv("FUTURES_ARCHIVE_TEST_DB_USER");
        String password = System.getenv("FUTURES_ARCHIVE_TEST_DB_PASSWORD");
        if (!"ISOLATED_TEST_DATABASE_ONLY".equals(System.getenv("FUTURES_ARCHIVE_TEST_CONFIRM"))
                || url == null || !url.matches("r2dbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/coinmind_replay_test")
                || user == null || user.isBlank() || password == null || password.isBlank())
            throw new IllegalArgumentException("Isolated test DB, host, user and password required");
        var options = ConnectionFactoryOptions.parse(url).mutate()
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, password).build();
        return ConnectionFactories.get(options);
    }
}
