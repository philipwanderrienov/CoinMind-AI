# Offline replay PostgreSQL integration test (isolated database only)

This is a **manual integration test**, not part of the default Maven `verify` suite. Do not run it against the CoinMind production database or expose archive read endpoints to the internet.

## Prerequisites

- An isolated/disposable PostgreSQL database, for example `coinmind_replay_test`.
- A dedicated least-privilege database user with privileges to read/write the replay test table.
- Schema applied to that isolated database using `backend/src/main/resources/db/futures_paper_replay_archive.sql`.
- Set both environment variables locally in the test shell. Never commit passwords or connection strings.

Example (replace placeholder values and ensure the database is **not** the production database):

```bash
# On the development server, create the isolated DB/user through your normal DBA process.
# Use psql with test-only credentials to apply the schema:
psql -h localhost -U replay_test_user -d coinmind_replay_test \
  -v ON_ERROR_STOP=1 -f backend/src/main/resources/db/futures_paper_replay_archive.sql

cd backend
export FUTURES_ARCHIVE_TEST_R2DBC_URL='r2dbc:postgresql://replay_test_user:REDACTED@localhost:5432/coinmind_replay_test'
export FUTURES_ARCHIVE_TEST_CONFIRM='ISOLATED_TEST_DATABASE_ONLY'
mvn -Dtest=FuturesPaperReplayPostgresIT test
unset FUTURES_ARCHIVE_TEST_R2DBC_URL FUTURES_ARCHIVE_TEST_CONFIRM
```

The integration test uses a random UUID, checks insert/find/history/idempotence and deletes its own row in `finally`. It is a **real DB test**, unlike the normal unit tests, but a new repository instance alone is not proof of an actual process restart. The real archive is still opt-in and the test performs **no production migration**.

Both `FUTURES_PAPER_REPLAY_ARCHIVE_ENABLED` and `FUTURES_PAPER_REPLAY_ARCHIVE_READ_ENABLED` must remain `false` in public deployments. Further work is required for authenticated read API, automatic backend replay scheduling, and persisted per-trade equity curve.
