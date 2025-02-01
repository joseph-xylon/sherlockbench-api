ALTER TABLE runs
  DROP COLUMN session_id,
  DROP COLUMN run_type,
  DROP COLUMN run_state,
  DROP COLUMN client_id;

--;;

DROP TYPE IF EXISTS run_state_type;

--;;

DROP TYPE IF EXISTS run_type_type;
