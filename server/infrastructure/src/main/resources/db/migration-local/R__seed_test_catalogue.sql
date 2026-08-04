-- ${flyway:timestamp}
--
-- Repeatable, and the placeholder above forces it to run on every start rather than only when
-- the file changes: it rewrites the checksum each time. The contract test deletes and renames
-- these rows as it goes, so a run following another would otherwise inherit whatever the last
-- one left behind. Being repeatable also keeps the fixtures out of the versioned sequence the
-- two migration folders share, where they would reserve a number `db/migration` steps over.
--
-- Fixed identifiers, so schemathesis.toml can point the contract test's {id} path parameters
-- at rows that exist and the generated requests reach the endpoints' real logic instead of
-- stopping at 404. Every row is restored to its declared shape rather than merely inserted
-- when absent, so a pinned identifier always names a row of the shape the run expects.
--
-- Every name is suffixed "(proba)" and every source key prefixed "test-" so none can collide
-- with something a developer already made: a term's name is unique among its siblings, and a
-- collision would fail the migration and with it the start-up.

-- A throwaway account for POST /api/admin/users/{id}/enabled to toggle. Never an
-- administrator: the contract test authenticates as one, and disabling that account mid-run
-- would lock the rest of the run out. Shares demo@local's password hash; nothing ever logs in
-- as it.
INSERT INTO users (id, email, password_hash, role, enabled)
VALUES (
  '55555555-5555-7555-8555-555555555555',
  'contract-test@local',
  '65536:3:1:LTBTAPNgSXsTcR6gSh9nXg==:V/MYTbuTk4ccInjd7wXk84UUjTk+Wi4WajJ2cTL9toI=',
  'USER',
  TRUE)
ON CONFLICT (id) DO UPDATE SET
  email = EXCLUDED.email,
  role = EXCLUDED.role,
  enabled = EXCLUDED.enabled;

INSERT INTO termo (id, name, parent_id)
VALUES
  ('11111111-1111-7111-8111-111111111111', 'Sanidade (proba)', NULL),
  ('22222222-2222-7222-8222-222222222222', 'Atención primaria (proba)', '11111111-1111-7111-8111-111111111111'),
  ('33333333-3333-7333-8333-333333333333', 'Atención hospitalaria (proba)', '11111111-1111-7111-8111-111111111111'),
  ('44444444-4444-7444-8444-444444444444', 'Educación (proba)', NULL),
  ('66666666-6666-7666-8666-666666666666', 'Termo prescindible (proba)', NULL)
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  parent_id = EXCLUDED.parent_id;

INSERT INTO organo_contratacion (id, source_key, name, active, termo_id, importable)
VALUES
  ('aaaaaaaa-aaaa-7aaa-8aaa-aaaaaaaaaaaa', 'test-sergas', 'Servizo Galego de Saúde (proba)', TRUE, '22222222-2222-7222-8222-222222222222', TRUE),
  ('bbbbbbbb-bbbb-7bbb-8bbb-bbbbbbbbbbbb', 'test-chuac', 'Complexo Hospitalario Universitario da Coruña (proba)', TRUE, '33333333-3333-7333-8333-333333333333', FALSE),
  ('cccccccc-cccc-7ccc-8ccc-cccccccccccc', 'test-consellaria-educacion', 'Consellaría de Educación (proba)', TRUE, NULL, FALSE),
  ('dddddddd-dddd-7ddd-8ddd-dddddddddddd', 'test-organo-inactivo', 'Órgano de proba inactivo', FALSE, NULL, FALSE)
ON CONFLICT (id) DO UPDATE SET
  source_key = EXCLUDED.source_key,
  name = EXCLUDED.name,
  active = EXCLUDED.active,
  termo_id = EXCLUDED.termo_id,
  importable = EXCLUDED.importable;
