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
  ('1c9e4d2b-7a3f-4e58-9b21-0d6f5a4c3e77', 'Termo do exemplo do contrato (proba)', NULL),
  ('4f2a6b8c-1d3e-4a5b-8c7d-9e0f1a2b3c44', 'Termo pai do exemplo do contrato (proba)', NULL),
  ('66666666-6666-7666-8666-666666000001', 'Termo prescindible 01 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000002', 'Termo prescindible 02 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000003', 'Termo prescindible 03 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000004', 'Termo prescindible 04 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000005', 'Termo prescindible 05 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000006', 'Termo prescindible 06 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000007', 'Termo prescindible 07 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000008', 'Termo prescindible 08 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000009', 'Termo prescindible 09 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000010', 'Termo prescindible 10 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000011', 'Termo prescindible 11 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000012', 'Termo prescindible 12 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000013', 'Termo prescindible 13 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000014', 'Termo prescindible 14 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000015', 'Termo prescindible 15 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000016', 'Termo prescindible 16 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000017', 'Termo prescindible 17 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000018', 'Termo prescindible 18 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000019', 'Termo prescindible 19 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000020', 'Termo prescindible 20 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000021', 'Termo prescindible 21 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000022', 'Termo prescindible 22 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000023', 'Termo prescindible 23 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000024', 'Termo prescindible 24 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000025', 'Termo prescindible 25 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000026', 'Termo prescindible 26 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000027', 'Termo prescindible 27 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000028', 'Termo prescindible 28 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000029', 'Termo prescindible 29 (proba)', NULL),
  ('66666666-6666-7666-8666-666666000030', 'Termo prescindible 30 (proba)', NULL)
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
