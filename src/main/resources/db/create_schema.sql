CREATE TABLE IF NOT EXISTS user_types (
    id SMALLSERIAL PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    "desc" TEXT,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT idx_user_types_type_role UNIQUE (type, role)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    organisation VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    notes TEXT,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    permissions JSONB,
    resources JSONB,
    last_login_device VARCHAR(255),
    last_login_browser VARCHAR(255),
    last_login_at TIMESTAMP,
    session_data TEXT,
    session_expires_at TIMESTAMP,
    created_by BIGINT,
    user_type_id SMALLINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_user_type FOREIGN KEY (user_type_id) REFERENCES user_types(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_is_deleted ON users(is_deleted);
CREATE INDEX IF NOT EXISTS idx_users_user_type_id ON users(user_type_id);

CREATE TABLE IF NOT EXISTS api_source (
    id BIGSERIAL PRIMARY KEY,
    api_path VARCHAR(255) NOT NULL,
    api_method VARCHAR(10) NOT NULL,
    module VARCHAR(100),
    description TEXT,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_api_path_method UNIQUE (api_path, api_method)
);

CREATE INDEX IF NOT EXISTS idx_api_source_api_path ON api_source(api_path);
CREATE INDEX IF NOT EXISTS idx_api_source_api_method ON api_source(api_method);
CREATE INDEX IF NOT EXISTS idx_api_source_module ON api_source(module);
CREATE INDEX IF NOT EXISTS idx_api_source_path_method ON api_source(api_path, api_method);

CREATE TABLE IF NOT EXISTS user_api_mapping (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_role VARCHAR(50),
    api_source_id BIGINT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_user_api_source UNIQUE (user_id, api_source_id),
    CONSTRAINT fk_user_api_mapping_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_api_mapping_api_source FOREIGN KEY (api_source_id) REFERENCES api_source(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_api_mapping_user ON user_api_mapping(user_id);
CREATE INDEX IF NOT EXISTS idx_user_api_mapping_user_role ON user_api_mapping(user_role);
CREATE INDEX IF NOT EXISTS idx_user_api_mapping_api_source ON user_api_mapping(api_source_id);
CREATE INDEX IF NOT EXISTS idx_user_api_mapping_is_active ON user_api_mapping(is_active);
CREATE INDEX IF NOT EXISTS idx_user_api_mapping_user_active ON user_api_mapping(user_id, is_active);

CREATE TABLE IF NOT EXISTS sso_user_groups (
    id BIGSERIAL PRIMARY KEY,
    profile_id VARCHAR(255) NOT NULL UNIQUE,
    grp_id VARCHAR(255) NOT NULL,
    company_name VARCHAR(255),
    cin_number VARCHAR(255),
    group_pan VARCHAR(255),
    authorized_person_name VARCHAR(255),
    designation VARCHAR(255),
    email VARCHAR(255),
    mobile VARCHAR(255),
    landline VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'Active',
    user_type_id SMALLINT NOT NULL,
    compliance_status VARCHAR(255),
    exchange_access VARCHAR(255),
    valid_till TIMESTAMP,
    last_synced_at TIMESTAMP,
    registrations JSONB NOT NULL,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sso_user_groups_user_type FOREIGN KEY (user_type_id) REFERENCES user_types(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_sso_user_groups_profile_id ON sso_user_groups(profile_id);
CREATE INDEX IF NOT EXISTS idx_sso_user_groups_grp_id ON sso_user_groups(grp_id);
CREATE INDEX IF NOT EXISTS idx_sso_user_groups_email ON sso_user_groups(email);
CREATE INDEX IF NOT EXISTS idx_sso_user_groups_status ON sso_user_groups(status);
CREATE INDEX IF NOT EXISTS idx_sso_user_groups_user_type_id ON sso_user_groups(user_type_id);

CREATE TABLE IF NOT EXISTS sso_user_sessions (
    id BIGSERIAL PRIMARY KEY,
    profile_id VARCHAR(255) NOT NULL UNIQUE,
    auth_code VARCHAR(255) NOT NULL,
    auth_code_expires_at TIMESTAMP NOT NULL,
    pkce_challenge VARCHAR(255),
    pkce_method VARCHAR(255),
    pkce_verifier TEXT,
    pkce_verifier_expires_at TIMESTAMP,
    sso_access_token TEXT,
    sso_refresh_token TEXT,
    sso_id_token TEXT,
    sso_token_issued_at TIMESTAMP,
    sso_token_expires_at TIMESTAMP,
    token_type VARCHAR(255),
    scope TEXT,
    jti VARCHAR(255),
    token_status VARCHAR(50),
    last_login_on TIMESTAMP,
    last_activity_at TIMESTAMP,
    ip_address VARCHAR(255),
    user_agent TEXT,
    unit_id VARCHAR(255),
    session_metadata JSONB,
    created_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    device_fingerprint VARCHAR(255),
    browser_fingerprint VARCHAR(255),
    is_active BOOLEAN,
    redis_session_data TEXT,
    redis_session_expires_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sso_user_sessions_profile_id ON sso_user_sessions(profile_id);
CREATE INDEX IF NOT EXISTS idx_sso_user_sessions_auth_code ON sso_user_sessions(auth_code);
CREATE INDEX IF NOT EXISTS idx_sso_user_sessions_token_status ON sso_user_sessions(token_status);
CREATE INDEX IF NOT EXISTS idx_sso_user_sessions_is_active ON sso_user_sessions(is_active);

CREATE TABLE IF NOT EXISTS user_kyc (
    id BIGSERIAL PRIMARY KEY,
    grp_id VARCHAR(255) NOT NULL,
    user_type_id SMALLINT,
    act_holder_name VARCHAR(255) NOT NULL,
    act_number VARCHAR(255) NOT NULL,
    act_number_masked VARCHAR(255),
    bank_ifsc VARCHAR(11) NOT NULL,
    bank_name VARCHAR(255),
    bank_branch VARCHAR(255),
    status SMALLINT NOT NULL DEFAULT 0,
    verified_at TIMESTAMP WITH TIME ZONE,
    last_updated_at TIMESTAMP WITH TIME ZONE,
    quarter_lock_date TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_kyc_user_type FOREIGN KEY (user_type_id) REFERENCES user_types(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_user_kyc_grp_id ON user_kyc(grp_id);
CREATE INDEX IF NOT EXISTS idx_user_kyc_user_type_id ON user_kyc(user_type_id);
CREATE INDEX IF NOT EXISTS idx_user_kyc_status ON user_kyc(status);
CREATE INDEX IF NOT EXISTS idx_user_kyc_is_active ON user_kyc(is_active);

INSERT INTO user_types (type, role, "desc") VALUES
('SSO_USER', 'INDUSTRY_USER', 'Industry users accessing through SSO'),
('SSO_USER', 'AUCTION_USER', 'Auction users accessing through SSO'),
('INTERNAL_USER', 'ADMIN', 'administrators'),
('INTERNAL_USER', 'USER', 'users'),
('INTERNAL_USER', 'SUPER_ADMIN', 'super administrators')
ON CONFLICT (type, role) DO NOTHING;

INSERT INTO api_source (api_path, api_method, module, description) VALUES
('/exchange/v1/int/auth/login', 'POST', 'auth', 'Internal user login'),
('/exchange/v1/int/auth/register', 'POST', 'auth', 'Internal user registration'),
('/exchange/v1/int/auth/refresh', 'POST', 'auth', 'Refresh internal user token'),
('/exchange/v1/int/auth/logout', 'POST', 'auth', 'Internal user logout'),
('/exchange/v1/int/auth/profile', 'GET', 'auth', 'Get internal user profile'),
('/exchange/v1/sso/authorize', 'GET', 'sso', 'SSO authorization endpoint'),
('/exchange/v1/sso/callback', 'GET', 'sso', 'SSO callback endpoint'),
('/exchange/v1/sso/profile', 'GET', 'sso', 'Get SSO user profile'),
('/exchange/v1/sso/refresh', 'POST', 'sso', 'Refresh SSO token'),
('/exchange/v1/sso/logout', 'POST', 'sso', 'SSO user logout'),
('/exchange/v1/sso/kyc', 'GET', 'kyc', 'Get user KYC details'),
('/exchange/v1/sso/kyc', 'POST', 'kyc', 'Submit user KYC'),
('/exchange/v1/sso/kyc', 'PUT', 'kyc', 'Update user KYC')
ON CONFLICT (api_path, api_method) DO NOTHING;
