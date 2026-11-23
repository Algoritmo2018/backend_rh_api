-- SMS code that confirms a new phone number (accounts with two-step verification)
ALTER TABLE one_time_tokens DROP CONSTRAINT ck_one_time_tokens_type;
ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_type CHECK (type IN (
    'PASSWORD_RESET', 'INVITATION', 'EMAIL_VERIFICATION', 'LOGIN_OTP', 'TWO_FACTOR_SETUP', 'TWO_FACTOR_LOGIN', 'PHONE_CHANGE'));
