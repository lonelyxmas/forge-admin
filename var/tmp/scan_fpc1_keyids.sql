SELECT 'ai_provider.api_key' AS src, SUBSTRING_INDEX(SUBSTRING_INDEX(api_key,':',3),':',-1) AS kid, COUNT(*) AS cnt
FROM ai_provider WHERE api_key LIKE 'FPC1:%' GROUP BY kid
UNION ALL
SELECT 'ai_approval.payload', SUBSTRING_INDEX(SUBSTRING_INDEX(payload_ciphertext,':',3),':',-1), COUNT(*)
FROM ai_capability_approval WHERE payload_ciphertext LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'report_conn.password', SUBSTRING_INDEX(SUBSTRING_INDEX(password_cipher,':',3),':',-1), COUNT(*)
FROM ai_report_data_connection WHERE password_cipher LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'client.app_secret', SUBSTRING_INDEX(SUBSTRING_INDEX(app_secret,':',3),':',-1), COUNT(*)
FROM sys_client WHERE app_secret LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'ext_sys.oauth2_secret', SUBSTRING_INDEX(SUBSTRING_INDEX(oauth2_client_secret,':',3),':',-1), COUNT(*)
FROM sys_external_system WHERE oauth2_client_secret LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'ext_sys.token_value', SUBSTRING_INDEX(SUBSTRING_INDEX(token_value,':',3),':',-1), COUNT(*)
FROM sys_external_system WHERE token_value LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'ext_sys.api_key_value', SUBSTRING_INDEX(SUBSTRING_INDEX(api_key_value,':',3),':',-1), COUNT(*)
FROM sys_external_system WHERE api_key_value LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'file_storage.secret', SUBSTRING_INDEX(SUBSTRING_INDEX(secret_key,':',3),':',-1), COUNT(*)
FROM sys_file_storage_config WHERE secret_key LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'sms.ak_secret', SUBSTRING_INDEX(SUBSTRING_INDEX(access_key_secret,':',3),':',-1), COUNT(*)
FROM sys_sms_config WHERE access_key_secret LIKE 'FPC1:%' GROUP BY 2
UNION ALL
SELECT 'user_social.access_token', SUBSTRING_INDEX(SUBSTRING_INDEX(access_token,':',3),':',-1), COUNT(*)
FROM sys_user_social WHERE access_token LIKE 'FPC1:%' GROUP BY 2;
