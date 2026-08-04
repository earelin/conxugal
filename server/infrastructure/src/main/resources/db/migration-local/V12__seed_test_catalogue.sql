-- Fixed identifiers, so schemathesis.toml can point the contract test's {id} path
-- parameters at resources that exist and the generated requests reach the endpoints'
-- real logic instead of stopping at 404.
--
-- Every name is suffixed "(proba)" and every source key prefixed "test-" so none can
-- collide with something a developer already made: a term's name is unique among its
-- siblings, and a collision would skip the row, leaving the rows below it pointing at a
-- parent that was never inserted and failing the migration on the foreign key instead.

-- A throwaway account for POST /api/admin/users/{id}/enabled to toggle. Never an
-- administrator: the contract test authenticates as one, and disabling that account
-- mid-run would lock the rest of the run out. Shares demo@local's password hash; nothing
-- ever logs in as it.
INSERT INTO users (id, email, password_hash, role)
VALUES (
  '55555555-5555-7555-8555-555555555555',
  'contract-test@local',
  '65536:3:1:LTBTAPNgSXsTcR6gSh9nXg==:V/MYTbuTk4ccInjd7wXk84UUjTk+Wi4WajJ2cTL9toI=',
  'USER')
ON CONFLICT DO NOTHING;

INSERT INTO termo (id, name, parent_id)
VALUES
  ('11111111-1111-7111-8111-111111111111', 'Sanidade (proba)', NULL),
  ('22222222-2222-7222-8222-222222222222', 'Atención primaria (proba)', '11111111-1111-7111-8111-111111111111'),
  ('33333333-3333-7333-8333-333333333333', 'Atención hospitalaria (proba)', '11111111-1111-7111-8111-111111111111'),
  ('44444444-4444-7444-8444-444444444444', 'Educación (proba)', NULL),
  ('66666666-6666-7666-8666-666666666666', 'Termo prescindible (proba)', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO organo_contratacion (id, source_key, name, active, termo_id, importable)
VALUES
  ('aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', 'test-sergas', 'Servizo Galego de Saúde (proba)', TRUE, '22222222-2222-7222-8222-222222222222', TRUE),
  ('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', 'test-chuac', 'Complexo Hospitalario Universitario da Coruña (proba)', TRUE, '33333333-3333-7333-8333-333333333333', FALSE),
  ('cccccccc-cccc-7ccc-8ccc-cccccccccccc', 'test-consellaria-educacion', 'Consellaría de Educación (proba)', TRUE, NULL, FALSE),
  ('dddddddd-dddd-7ddd-8ddd-dddddddddddd', 'test-organo-inactivo', 'Órgano de proba inactivo', FALSE, NULL, FALSE)
ON CONFLICT DO NOTHING;
