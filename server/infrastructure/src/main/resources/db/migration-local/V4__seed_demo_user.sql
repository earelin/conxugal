INSERT INTO users (email, password_hash, role)
VALUES ('demo@local', '65536:3:1:LTBTAPNgSXsTcR6gSh9nXg==:V/MYTbuTk4ccInjd7wXk84UUjTk+Wi4WajJ2cTL9toI=', 'USER')
ON CONFLICT (email) DO NOTHING;
