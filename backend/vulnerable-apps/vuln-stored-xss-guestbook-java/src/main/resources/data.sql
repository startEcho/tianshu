INSERT INTO guestbook_entries(author, content, created_at)
VALUES
('coach', 'Welcome to the training guestbook. HTML is rendered directly here.', CURRENT_TIMESTAMP),
('analyst', 'Try a harmless payload first, then escalate to an event handler based payload.', CURRENT_TIMESTAMP),
('ops', '<b>Reminder:</b> this environment is intentionally unsafe and should stay isolated.', CURRENT_TIMESTAMP);
