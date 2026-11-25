-- Candidate email change (stage 7b): link sent to the NEW address. The token is bound to that address, so the
-- address is stored with it (lower case); every other token type leaves it empty.
ALTER TABLE one_time_tokens ADD COLUMN target_email VARCHAR(160);
ALTER TABLE one_time_tokens DROP CONSTRAINT ck_one_time_tokens_type;
ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_type CHECK (type IN (
    'PASSWORD_RESET', 'INVITATION', 'EMAIL_VERIFICATION', 'LOGIN_OTP', 'TWO_FACTOR_SETUP', 'TWO_FACTOR_LOGIN', 'PHONE_CHANGE',
    'EMAIL_CHANGE'));
ALTER TABLE one_time_tokens ADD CONSTRAINT ck_one_time_tokens_target_email CHECK (
    (type = 'EMAIL_CHANGE') = (target_email IS NOT NULL) AND (target_email IS NULL OR target_email = lower(target_email)));
