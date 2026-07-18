INSERT INTO users (email, password_hash, role)
VALUES ('root@local', '65536:3:1:F/yzcdRkWfb/5adMM+b7yg==:965pJLQ26XMpQhM/JqChHGvGG/T2YwS0veRqNtfNuaw=', 'ADMIN')
ON CONFLICT (email) DO NOTHING;
