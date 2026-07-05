-- Database Schema for MediAgent MVP

-- 1. Clinics
CREATE TABLE IF NOT EXISTS clinics (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL
);

-- 2. Doctors
CREATE TABLE IF NOT EXISTS doctors (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  full_name TEXT NOT NULL,
  clinic_id UUID NOT NULL REFERENCES clinics(id),
  llm_provider TEXT DEFAULT 'openrouter', -- 'openrouter' | 'gemini' | 'openai'
  llm_model TEXT DEFAULT 'google/gemini-2.5-flash',
  llm_api_key TEXT, -- Encrypted or stored as configured
  llm_api_url TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Patients (PII columns are encrypted via pgcrypto or on-app-level, stored as text/bytea)
CREATE TABLE IF NOT EXISTS patients (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id UUID NOT NULL REFERENCES doctors(id),
  patient_code TEXT NOT NULL, -- e.g. 'P-1042', readable in UI
  name_encrypted TEXT NOT NULL,
  dob_encrypted TEXT,
  contact_encrypted TEXT,
  chronic_conditions TEXT[] DEFAULT '{}',
  allergies TEXT[] DEFAULT '{}',
  current_medications TEXT[] DEFAULT '{}',
  notes TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 4. Documents
CREATE TABLE IF NOT EXISTS documents (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  clinic_id UUID NOT NULL REFERENCES clinics(id),
  file_name TEXT NOT NULL,
  file_type TEXT NOT NULL, -- 'pdf', 'html', 'txt', 'pptx'
  r2_url TEXT NOT NULL,
  status TEXT DEFAULT 'PROCESSING', -- 'PROCESSING' | 'READY' | 'FAILED'
  page_count INT,
  uploaded_by UUID REFERENCES doctors(id),
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 5. Document Summaries
CREATE TABLE IF NOT EXISTS document_summaries (
  doc_id UUID PRIMARY KEY REFERENCES documents(id) ON DELETE CASCADE,
  summary TEXT NOT NULL,
  primary_conditions TEXT[] DEFAULT '{}',
  treatment_categories TEXT[] DEFAULT '{}',
  keywords TEXT[] DEFAULT '{}',
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 6. Sessions
CREATE TABLE IF NOT EXISTS sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id UUID NOT NULL REFERENCES doctors(id),
  patient_id UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  status TEXT DEFAULT 'ACTIVE', -- 'ACTIVE' | 'CLOSED'
  ai_summary TEXT, -- generated when session closes
  started_at TIMESTAMPTZ DEFAULT NOW(),
  closed_at TIMESTAMPTZ
);

-- 7. Session Turns (Stores chronological conversation payload inside content JSONB)
CREATE TABLE IF NOT EXISTS session_turns (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
  role TEXT NOT NULL, -- 'doctor' | 'assistant'
  content JSONB NOT NULL, -- stores message contents, questions, or solution cards
  turn_index INT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 8. Refresh Tokens (Opaque, for secure JWT updates)
CREATE TABLE IF NOT EXISTS refresh_tokens (
  token TEXT PRIMARY KEY,
  doctor_id UUID NOT NULL REFERENCES doctors(id) ON DELETE CASCADE,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Create Indexes for optimization
CREATE INDEX IF NOT EXISTS idx_patients_doctor ON patients(doctor_id);
CREATE INDEX IF NOT EXISTS idx_sessions_doctor ON sessions(doctor_id);
CREATE INDEX IF NOT EXISTS idx_sessions_patient ON sessions(patient_id);
CREATE INDEX IF NOT EXISTS idx_turns_session ON session_turns(session_id, turn_index);
