-- Run manually ONLY on the disposable coinmind_replay_test DB first.
-- Existing values already rounded to 10 dp cannot be recovered by this migration.
-- No production rollout without backup/review.
BEGIN;
ALTER TABLE futures_paper_replay_archive
    ALTER COLUMN initial_equity TYPE NUMERIC,
    ALTER COLUMN final_equity TYPE NUMERIC,
    ALTER COLUMN max_drawdown_quote TYPE NUMERIC,
    ALTER COLUMN daily_loss_limit_quote TYPE NUMERIC,
    ALTER COLUMN realized_loss_quote TYPE NUMERIC,
    ALTER COLUMN remaining_loss_budget_quote TYPE NUMERIC;
ALTER TABLE futures_paper_replay_equity
    ALTER COLUMN equity_usdt TYPE NUMERIC,
    ALTER COLUMN net_pnl_usdt TYPE NUMERIC;
COMMIT;
