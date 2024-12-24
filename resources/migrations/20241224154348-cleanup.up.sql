ALTER TABLE attempts DROP COLUMN complete_log;

--;;

ALTER TABLE attempts DROP COLUMN time_taken;

--;;

ALTER TABLE runs DROP COLUMN total_api_calls;

--;;

ALTER TABLE runs ALTER COLUMN total_run_time TYPE INTERVAL USING NULL;
