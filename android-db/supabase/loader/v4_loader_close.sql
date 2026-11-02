-- Closes the temporary loader (v4_loader_open.sql): nothing can write to catalogue_v4 through the API afterwards.
drop function if exists public.bc_v4_load(text, text, jsonb);
drop table if exists catalogue_v4.loader_key;
