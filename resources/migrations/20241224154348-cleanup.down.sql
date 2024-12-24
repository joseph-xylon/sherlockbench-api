ALTER TABLE attempts ADD COLUMN complete_log TEXT;

--;;

ALTER TABLE attempts ADD COLUMN time_taken FLOAT;

--;;

ALTER TABLE runs ADD COLUMN total_api_calls INTEGER;

--;;

ALTER TABLE runs ALTER COLUMN total_run_time TYPE FLOAT USING NULL;
