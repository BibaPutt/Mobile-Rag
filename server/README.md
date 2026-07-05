# MediAgent MVP — Backend Server Deploy Guide

This is the production-ready Node.js API & WebSocket server for **MediAgent**, designed for deployment on [Render](https://render.com) or any cloud VPS.

## Tech Stack Overview
- **Runtime:** Node.js v20+
- **API Framework:** Express (REST Endpoints)
- **Real-Time Communication:** WebSockets (`ws`)
- **Database:** PostgreSQL (with Neon integration support)
- **Storage:** Amazon S3 or Cloudflare R2 compatibility

---

## 🚀 Easy Development Startup
You can run this backend locally or on a server immediately. By default, if NO database URL is detected, the server automatically boots in a fully functional **Mock-in-Memory fallback mode**, allowing instant frontend development and testing!

1. Install dependencies:
   ```bash
   npm install
   ```

2. Configure environment variables in an `.env` file (see list below).

3. Start the server (runs on port `4000` by default):
   ```bash
   npm start
   ```

---

## 🌐 Deploying to Render

To deploy this backend as a production service:

### 1. Provision a PostgreSQL Database
- Create a PostgreSQL database instance on Render or [Neon](https://neon.tech).
- Execute the SQL schema defined in `/server/schema.sql` inside your database CLI or SQL Editor to initialize all tables and indexes.

### 2. Set Up a Render Web Service
1. Connect your GitHub repository containing the backend code.
2. Select **Web Service** in Render.
3. Configure the following build settings:
   - **Environment:** `Node`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Instance Type:** `Starter` ($7/mo) — *Crucial for clinical clinical apps as Free tier instances will spin down after 15 min of inactivity causing WebSocket failures.*

### 3. Configure Render Environment Variables
Add these keys under the **Environment** tab in your Render Web Service settings:

```env
PORT=4000
NODE_ENV=production
DATABASE_URL=postgresql://<user>:<password>@<host>/<dbname>?sslmode=require
JWT_SECRET=<secure-random-64-byte-string>
ENCRYPTION_KEY=<secure-random-32-byte-string-for-pii-columns>
```

*Note: Render handles custom SSL certificates and WebSocket termination natively. Connections automatically upgrade to secure WebSockets `wss://<app-name>.onrender.com`.*

---

## ⚡ Real-time WebSocket Protocol

The server uses a real-time WebSocket state machine on the endpoint `ws://<your-host>`. Below is the client-server message model:

### Client Connection
The client (Android) opens a connection. Once verified, client raises:
```json
{ "type": "CONNECT_SESSION", "session_id": "ses-123456" }
```
Server responds to verify success:
```json
{ "type": "SESSION_READY", "session_id": "ses-123456" }
```

### Recording & Audio segment
When the client stops recording, it sends:
```json
{ "type": "TRANSCRIPT_SEGMENT", "text": "Patient has Stage 1 hypertension symptoms...", "is_final": true }
```
Server broadcasts thinking status:
```json
{ "type": "THINKING" }
```
1-2 seconds later, returns the LLM interactive state or clinical solution:
```json
{ "type": "RESPONSE", "payload": { ...structured_clinical_json... } }
```

---

## 🏥 Clinical Validation Checks
- **PII Encryption Checklist:** Patient names, birthdays, and contact options should be saved strictly with column-level AES-256 (`pgcrypto`) or client-side encryption before reaching the persistent physical disk.
- **Emergency Escalation:** If incoming transcription contains severe red flags ("severe chest pain radiating to left jaw"), the server flags a red alert inside of the JSON `session_notes` block to suggest an emergency room triage protocol immediately.
