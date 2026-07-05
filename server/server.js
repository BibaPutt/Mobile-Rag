// Express and WebSocket Backend for MediAgent MVP
require('dotenv').config();
const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const cors = require('cors');
const { Pool } = require('pg');
const jwt = require('jsonwebtoken');
const multer = require('multer');

const PORT = process.env.PORT || 4000;
const JWT_SECRET = process.env.JWT_SECRET || 'mediagent_secret_secure_key_12345';

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ noServer: true });

app.use(cors());
app.use(express.json());

// Multi-mode Database connection pool
let pool = null;
if (process.env.DATABASE_URL) {
  pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
  });
  console.log('PostgreSQL database pool initialized.');
} else {
  console.log('No DATABASE_URL found. Running in mock-in-memory fallback mode.');
}

// Mock Database Storage
const mockDb = {
  clinics: [{ id: 'clinic-1', name: 'Metro Health Cardiology' }],
  doctors: [
    {
      id: 'doc-1',
      email: 'doctor@metrohealth.org',
      password_hash: 'doctor123', // Demo plaintext
      full_name: 'Dr. Sarah Jenkins',
      clinic_id: 'clinic-1',
      llm_provider: 'openrouter',
      llm_model: 'google/gemini-2.5-flash'
    }
  ],
  patients: [
    {
      id: 'pat-1',
      doctor_id: 'doc-1',
      patient_code: 'P-1042',
      name_encrypted: 'John Doe',
      dob_encrypted: '1984-11-20',
      contact_encrypted: '+1-555-0199',
      chronic_conditions: ['Hypertension', 'Type 2 Diabetes'],
      allergies: ['Penicillin'],
      current_medications: ['Lisinopril 10mg', 'Metformin 500mg'],
      notes: 'Patient experiences occasional lightheadedness when exercising.'
    },
    {
      id: 'pat-2',
      doctor_id: 'doc-1',
      patient_code: 'P-1043',
      name_encrypted: 'Alice Smith',
      dob_encrypted: '1992-04-15',
      contact_encrypted: '+1-555-0248',
      chronic_conditions: ['Mild Asthma'],
      allergies: ['Sulfa Drugs'],
      current_medications: ['Albuterol inhaler as needed'],
      notes: 'Monitors Peak Flow daily. Reports symptoms are well-controlled.'
    }
  ],
  documents: [
    {
      id: 'doc-guid-1',
      clinic_id: 'clinic-1',
      file_name: 'Hypertension_2025_Guidelines.pdf',
      file_type: 'pdf',
      r2_url: 'https://pub-r2.mediagent.net/Hypertension_2025_Guidelines.pdf',
      status: 'READY',
      page_count: 14,
      uploaded_by: 'doc-1',
      created_at: new Date().toISOString()
    }
  ],
  documentSummaries: [
    {
      doc_id: 'doc-guid-1',
      summary: 'ACCF/AHA consensus guidelines on managing stage 1 and stage 2 essential hypertension.',
      primary_conditions: ['Hypertension', 'Cardiovascular Risk'],
      treatment_categories: ['Pharmacotherapy', 'Dietary Modification'],
      keywords: ['ACE Inhibitor', 'Beta Blocker', 'DASH Diet']
    }
  ],
  sessions: [],
  sessionTurns: []
};

// Authentication Middleware
function authenticateToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'Access token missing' });

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return res.status(403).json({ error: 'Invalid or expired token' });
    req.user = user;
    next();
  });
}

// ---------------- REST ENDPOINTS ----------------

// Baseline status / Health Check
app.get('/health', (req, res) => {
  res.json({ status: 'ok', database: pool ? 'postgres' : 'mock-memory', timestamp: Date.now() });
});

// login
app.post('/api/auth/login', async (req, res) => {
  const { email, password } = req.body;
  if (!email || !password) {
    return res.status(400).json({ error: 'Email and password required' });
  }

  try {
    let doctor = null;
    if (pool) {
      const result = await pool.query('SELECT * FROM doctors WHERE email = $1', [email]);
      if (result.rows.length > 0) {
        doctor = result.rows[0];
        // password match in product would use bcrypt, doing simple comparison for ease of testing
        if (doctor.password_hash !== password) {
          return res.status(400).json({ error: 'Invalid password' });
        }
      }
    } else {
      doctor = mockDb.doctors.find(d => d.email === email && d.password_hash === password);
    }

    if (!doctor) {
      return res.status(400).json({ error: 'Doctor not found or invalid credentials' });
    }

    const payload = { id: doctor.id, email: doctor.email, full_name: doctor.full_name, clinic_id: doctor.clinic_id };
    const accessToken = jwt.sign(payload, JWT_SECRET, { expiresIn: '1d' });
    const refreshToken = jwt.sign({ id: doctor.id }, JWT_SECRET, { expiresIn: '30d' });

    res.json({
      access_token: accessToken,
      refresh_token: refreshToken,
      doctor: {
        id: doctor.id,
        email: doctor.email,
        full_name: doctor.full_name,
        clinic_id: doctor.clinic_id,
        llm_provider: doctor.llm_provider,
        llm_model: doctor.llm_model
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Internal server error during login' });
  }
});

// patient list
app.get('/api/patients', authenticateToken, async (req, res) => {
  try {
    let patients = [];
    if (pool) {
      const result = await pool.query('SELECT * FROM patients WHERE doctor_id = $1 ORDER BY created_at DESC', [req.user.id]);
      patients = result.rows;
    } else {
      patients = mockDb.patients.filter(p => p.doctor_id === req.user.id);
    }
    res.json(patients);
  } catch (error) {
    res.status(500).json({ error: 'Could not fetch patients' });
  }
});

// create patient
app.post('/api/patients', authenticateToken, async (req, res) => {
  const { name_encrypted, dob_encrypted, contact_encrypted, chronic_conditions, allergies, current_medications, notes } = req.body;
  if (!name_encrypted) {
    return res.status(400).json({ error: 'Patient name required' });
  }

  const patientCode = `P-${Date.now().toString().slice(-4)}`;

  try {
    const newPatient = {
      id: `pat-${Date.now()}`,
      doctor_id: req.user.id,
      patient_code: patientCode,
      name_encrypted,
      dob_encrypted,
      contact_encrypted,
      chronic_conditions: chronic_conditions || [],
      allergies: allergies || [],
      current_medications: current_medications || [],
      notes: notes || '',
      created_at: new Date().toISOString()
    };

    if (pool) {
      const result = await pool.query(
        `INSERT INTO patients (doctor_id, patient_code, name_encrypted, dob_encrypted, contact_encrypted, chronic_conditions, allergies, current_medications, notes)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9) RETURNING *`,
        [req.user.id, patientCode, name_encrypted, dob_encrypted, contact_encrypted, chronic_conditions, allergies, current_medications, notes]
      );
      res.status(201).json(result.rows[0]);
    } else {
      mockDb.patients.unshift(newPatient);
      res.status(201).json(newPatient);
    }
  } catch (error) {
    res.status(500).json({ error: 'Could not create patient' });
  }
});

// list documents
app.get('/api/documents', authenticateToken, async (req, res) => {
  try {
    let documents = [];
    if (pool) {
      const result = await pool.query(
        'SELECT d.*, s.summary, s.primary_conditions FROM documents d LEFT JOIN document_summaries s ON d.id = s.doc_id WHERE d.clinic_id = $1',
        [req.user.clinic_id]
      );
      documents = result.rows;
    } else {
      documents = mockDb.documents.map(d => {
        const sum = mockDb.documentSummaries.find(s => s.doc_id === d.id) || {};
        return { ...d, summary: sum.summary, primary_conditions: sum.primary_conditions };
      });
    }
    res.json(documents);
  } catch (error) {
    res.status(500).json({ error: 'Could not fetch documents' });
  }
});

// mock file upload
const upload = multer({ dest: 'uploads/' });
app.post('/api/documents/upload', authenticateToken, upload.single('file'), async (req, res) => {
  if (!req.file) return res.status(400).json({ error: 'No file uploaded' });

  const docId = `doc-guid-${Date.now()}`;
  const newDoc = {
    id: docId,
    clinic_id: req.user.clinic_id,
    file_name: req.file.originalname,
    file_type: req.file.originalname.split('.').pop() || 'pdf',
    r2_url: `https://pub-r2.mediagent.net/${docId}/${req.file.originalname}`,
    status: 'READY',
    page_count: 5,
    uploaded_by: req.user.id,
    created_at: new Date().toISOString()
  };

  const newSummary = {
    doc_id: docId,
    summary: `Automatically parsed clinical summary of ${req.file.originalname} addressing treatment pathways and diagnostic evaluations.`,
    primary_conditions: ['General Clinical Assessment'],
    treatment_categories: ['In-clinic guidelines'],
    keywords: ['treatment', 'evaluation']
  };

  try {
    if (pool) {
      await pool.query(
        'INSERT INTO documents (id, clinic_id, file_name, file_type, r2_url, status, page_count, uploaded_by) VALUES ($1, $2, $3, $4, $5, $6, $7, $8)',
        [docId, req.user.clinic_id, newDoc.file_name, newDoc.file_type, newDoc.r2_url, 'READY', 5, req.user.id]
      );
      await pool.query(
        'INSERT INTO document_summaries (doc_id, summary, primary_conditions, treatment_categories, keywords) VALUES ($1, $2, $3, $4, $5)',
        [docId, newSummary.summary, newSummary.primary_conditions, newSummary.treatment_categories, newSummary.keywords]
      );
    } else {
      mockDb.documents.unshift(newDoc);
      mockDb.documentSummaries.unshift(newSummary);
    }
    res.json({ ...newDoc, summary: newSummary.summary });
  } catch (error) {
    res.status(500).json({ error: 'Failed to upload and catalog document' });
  }
});

// sessions lists
app.get('/api/sessions', authenticateToken, async (req, res) => {
  const { patient_id } = req.query;
  if (!patient_id) return res.status(400).json({ error: 'patient_id query param required' });

  try {
    let sessions = [];
    if (pool) {
      const result = await pool.query('SELECT * FROM sessions WHERE patient_id = $1 ORDER BY started_at DESC', [patient_id]);
      sessions = result.rows;
    } else {
      sessions = mockDb.sessions.filter(s => s.patient_id === patient_id);
    }
    res.json(sessions);
  } catch (error) {
    res.status(500).json({ error: 'Could not fetch sessions' });
  }
});

// detail session including turns
app.get('/api/sessions/:id', authenticateToken, async (req, res) => {
  try {
    let session = null;
    let turns = [];

    if (pool) {
      const sResult = await pool.query('SELECT * FROM sessions WHERE id = $1', [req.params.id]);
      if (sResult.rows.length > 0) {
        session = sResult.rows[0];
        const tResult = await pool.query('SELECT * FROM session_turns WHERE session_id = $1 ORDER BY turn_index ASC', [req.params.id]);
        turns = tResult.rows;
      }
    } else {
      session = mockDb.sessions.find(s => s.id === req.params.id);
      if (session) {
        turns = mockDb.sessionTurns.filter(t => t.session_id === req.params.id).sort((a,b) => a.turn_index - b.turn_index);
      }
    }

    if (!session) return res.status(404).json({ error: 'Session not found' });
    res.json({ ...session, turns });
  } catch (error) {
    res.status(500).json({ error: 'Could not fetch session details' });
  }
});

// create session
app.post('/api/sessions', authenticateToken, async (req, res) => {
  const { patient_id } = req.body;
  if (!patient_id) return res.status(400).json({ error: 'patient_id required' });

  const sessionId = `ses-${Date.now()}`;
  try {
    const newSession = {
      id: sessionId,
      doctor_id: req.user.id,
      patient_id,
      status: 'ACTIVE',
      started_at: new Date().toISOString()
    };

    if (pool) {
      const result = await pool.query(
        'INSERT INTO sessions (id, doctor_id, patient_id) VALUES ($1, $2, $3) RETURNING *',
        [sessionId, req.user.id, patient_id]
      );
      res.status(201).json(result.rows[0]);
    } else {
      mockDb.sessions.unshift(newSession);
      res.status(201).json(newSession);
    }
  } catch (error) {
    res.status(500).json({ error: 'Could not start session' });
  }
});

// close and summarize session
app.patch('/api/sessions/:id/close', authenticateToken, async (req, res) => {
  const summaryText = "Consultation recorded. Clinical symptoms addressed, potential stage 1 hypertension suspected. Formulated DASH nutritional recommendations with 10mg Amlodipine regimen, subject to clinical follow-up.";

  try {
    if (pool) {
      await pool.query(
        'UPDATE sessions SET status = $1, ai_summary = $2, closed_at = $3 WHERE id = $4',
        ['CLOSED', summaryText, new Date(), req.params.id]
      );
    } else {
      const sIndex = mockDb.sessions.findIndex(s => s.id === req.params.id);
      if (sIndex !== -1) {
        mockDb.sessions[sIndex].status = 'CLOSED';
        mockDb.sessions[sIndex].ai_summary = summaryText;
        mockDb.sessions[sIndex].closed_at = new Date().toISOString();
      }
    }
    res.json({ status: 'CLOSED', ai_summary: summaryText });
  } catch (error) {
    res.status(500).json({ error: 'Failed to close session' });
  }
});


// ---------------- WEBSOCKET HANDLING ----------------
const systemPrompt = require('./systemPrompt');

async function callGemini(history, currentText) {
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return null;
  
  const contents = [...history, { role: "user", parts: [{ text: currentText }] }];
  
  try {
    const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${apiKey}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        systemInstruction: { parts: [{ text: systemPrompt }] },
        contents: contents,
        generationConfig: {
          responseFormat: { type: "json_object" }
        }
      })
    });
    const data = await response.json();
    if (data.candidates && data.candidates[0] && data.candidates[0].content && data.candidates[0].content.parts.length > 0) {
      return JSON.parse(data.candidates[0].content.parts[0].text);
    }
  } catch(e) {
    console.error("Gemini API error:", e);
  }
  return null;
}

wss.on('connection', (ws, req) => {
  console.log('WS Connection initiated');
  let currentSessionId = null;
  let currentDocId = null;
  let conversationHistory = [];

  ws.on('message', async (message) => {
    try {
      const data = JSON.parse(message);
      
      if (data.type === 'CONNECT_SESSION') {
        currentSessionId = data.session_id;
        conversationHistory = [];
        console.log(`WebSocket joined session: ${currentSessionId}`);
        ws.send(JSON.stringify({ type: 'SESSION_READY', session_id: currentSessionId }));
      }

      else if (data.type === 'TRANSCRIPT_SEGMENT') {
        if (!data.is_final) return; // Buffer intermediate
        
        console.log(`Received transcription for ${currentSessionId}: ${data.text}`);
        ws.send(JSON.stringify({ type: 'THINKING' }));

        let responsePayload = null;
        if (process.env.GEMINI_API_KEY) {
          responsePayload = await callGemini(conversationHistory, data.text);
        }

        if (!responsePayload) {
          // Fallback Clinical Agentic Intelligence Pipeline Simulation
          const lowerText = data.text.toLowerCase();
          if (lowerText.includes('chest') || lowerText.includes('pressure') || lowerText.includes('severe')) {
            responsePayload = {
              session_status: "INTERACTIVE",
              patient_summary: "Patient presents with pressure over chest region and generalized shortness of breath. Basic assessment ongoing.",
              confidence_level: "LOW",
              interactive_questions: [
                {
                  id: "q_onset",
                  field: "Symptoms Onset",
                  question_text: "Does the pressure radiate to the left neck, arm, or jaw?",
                  input_type: "yes_no"
                },
                {
                  id: "q_severity",
                  field: "Pain Severity",
                  question_text: "On a scale of 1-10, how severe is the chest discomfort?",
                  input_type: "scale_1_10"
                }
              ],
              solutions: [],
              session_notes: "⚠️ CARDIOVASCULAR WARNING: Monitor vitals continuously. High risk of coronary syndrome. Direct ECG triage if symptoms escalate."
            };
          } else {
            responsePayload = {
              session_status: "FINAL_RESOLUTION",
              patient_summary: "Confirmed Stage 1 Hypertension with slight clinical symptom presentation. Chronic history of lisinopril therapy.",
              confidence_level: "HIGH",
              interactive_questions: [],
              solutions: [
                {
                  title: "Option A: ACE Inhibitor dosage titration",
                  description: "Increase Lisinopril dose from 10mg to 20mg daily, subject to post-titration renal panel verification on day 10.",
                  source_document: "Hypertension_2025_Guidelines.pdf",
                  source_page: 4,
                  referenced_images: [],
                  contraindications: ["Pregnancy", "Prior history of Angioedema"],
                  dietary_plan: ["Restrict sodium below 1,500mg daily", "Increase leafy vegetable and potassium-rich diet portions"],
                  lifestyle_modifications: ["Prescribe 30 minutes of aerobic cardiovascular exercise 5x weekly", "Recommend daily home blood pressure logging"],
                  follow_up_timeline: "Re-evaluate in 2-3 weeks",
                  drug_interactions_warning: "Monitor concurrently with NSAID use (reduced efficacy + toxicity risks)."
                }
              ],
              session_notes: "DASH (Dietary Approaches to Stop Hypertension) acts synergistically with lisinopril and showed -8 to -11 mmHg drop in clinical studies."
            };
          }
        }

        conversationHistory.push({ role: "user", parts: [{ text: data.text }] });
        conversationHistory.push({ role: "model", parts: [{ text: JSON.stringify(responsePayload) }] });

        const turn = {
          id: `turn-${Date.now()}`,
          session_id: currentSessionId,
          role: 'assistant',
          content: responsePayload,
          turn_index: conversationHistory.length / 2,
          created_at: new Date().toISOString()
        };
        
        if (pool) {
          pool.query('INSERT INTO session_turns (session_id, role, content, turn_index) VALUES ($1, $2, $3, $4)', [
            currentSessionId, 'assistant', JSON.stringify(responsePayload), turn.turn_index
          ]).catch(err => console.error(err));
        } else {
          mockDb.sessionTurns.push(turn);
        }

        ws.send(JSON.stringify({ type: 'RESPONSE', payload: responsePayload }));
      }

      else if (data.type === 'ANSWERS_SUBMITTED') {
        console.log(`Answers submitted:`, data.answers);
        ws.send(JSON.stringify({ type: 'THINKING' }));

        const answersText = "Doctor submitted answers: " + JSON.stringify(data.answers);
        let responsePayload = null;
        if (process.env.GEMINI_API_KEY) {
          responsePayload = await callGemini(conversationHistory, answersText);
        }

        if (!responsePayload) {
          // Fallback Simulation
          responsePayload = {
            session_status: "FINAL_RESOLUTION",
            patient_summary: "Addressed chest pain concerns: client reports non-radiating level 4 visual-scale tension post heavy exertion, resolving on rest. Suspect stable ischemia vs primary muscular spasm path.",
            confidence_level: "MEDIUM",
            interactive_questions: [],
            solutions: [
              {
                title: "Primary Treatment: Myocardial perfusion tracking",
                description: "Recommend outpatient Exercise Tolerance Test (ETT). Maintain Nitroglycerin 0.4mg sublingual instructions for acute onset.",
                source_document: "Cardiovascular_Therapeutics.pdf",
                source_page: 11,
                referenced_images: [],
                contraindications: ["Aortic stenosis", "Acute MI within 2 days"],
                dietary_plan: ["Strict DASH diet program", "Dose coenzyme Q10 daily"],
                lifestyle_modifications: ["Suspend strenuous lifting", "Begin home blood pressure and scale logs daily"],
                follow_up_timeline: "Schedule clinical follow-up in 10 days",
                drug_interactions_warning: "Absolute contraindication with sildenafil or phosphodiesterase-5 inhibitors."
              }
            ],
            session_notes: "Pain is non-emergent at this moment. Triage directly to cardiology clinic for treadmill scheduling."
          };
        }

        conversationHistory.push({ role: "user", parts: [{ text: answersText }] });
        conversationHistory.push({ role: "model", parts: [{ text: JSON.stringify(responsePayload) }] });

        if (pool) {
          pool.query('INSERT INTO session_turns (session_id, role, content, turn_index) VALUES ($1, $2, $3, $4)', [
            currentSessionId, 'assistant', JSON.stringify(responsePayload), Math.floor(conversationHistory.length / 2)
          ]).catch(err => console.error(err));
        } else {
          mockDb.sessionTurns.push({
            id: `turn-${Date.now()}`,
            session_id: currentSessionId,
            role: 'assistant',
            content: responsePayload,
            turn_index: Math.floor(conversationHistory.length / 2),
            created_at: new Date().toISOString()
          });
        }

        ws.send(JSON.stringify({ type: 'RESPONSE', payload: responsePayload }));
      }
    } catch (e) {
      console.error('WS parsing error:', e);
    }
  });

  ws.on('close', () => console.log('WS Connection closed'));
});

// Upgrade HTTP to WS manually
server.on('upgrade', (request, socket, head) => {
  wss.handleUpgrade(request, socket, head, (ws) => {
    wss.emit('connection', ws, request);
  });
});

server.listen(PORT, () => console.log(`MediAgent Backend running successfully on port ${PORT}`));
