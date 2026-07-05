package com.example.data.repository

import com.example.data.local.MediAgentDao
import com.example.data.model.DocItem
import com.example.data.model.Patient
import com.example.data.model.Session
import com.example.data.model.SessionTurn
import com.example.data.model.DocumentChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class MediAgentRepository(private val dao: MediAgentDao) {

    private var cachedAllChunks: List<DocumentChunk>? = null
    private val cachedDocChunks = java.util.concurrent.ConcurrentHashMap<String, List<DocumentChunk>>()
    private val chunksMutex = kotlinx.coroutines.sync.Mutex()

    fun clearChunksCache() {
        cachedAllChunks = null
        cachedDocChunks.clear()
    }

    val allPatients: Flow<List<Patient>> = dao.getAllPatients()
    val allDocuments: Flow<List<DocItem>> = dao.getAllDocuments()

    fun getAllPatientsForDoctor(doctorId: String): Flow<List<Patient>> =
        dao.getAllPatientsForDoctor(doctorId)

    fun getSessionsForPatient(patientId: String): Flow<List<Session>> =
        dao.getSessionsForPatient(patientId)

    fun getTurnsForSession(sessionId: String): Flow<List<SessionTurn>> =
        dao.getTurnsForSession(sessionId)

    suspend fun getPatientById(id: String): Patient? = dao.getPatientById(id)

    suspend fun getSessionById(id: String): Session? = dao.getSessionById(id)

    suspend fun getAllSessions(): List<Session> = dao.getAllSessions()

    suspend fun getAllTurns(): List<SessionTurn> = dao.getAllTurns()

    suspend fun getAllDocumentsRaw(): List<DocItem> = dao.getAllDocumentsRaw()

    suspend fun getAllPatientsRaw(): List<Patient> = dao.getPatientById("").let { dao.getAllPatients() }.first()

    suspend fun insertPatient(patient: Patient) {
        dao.insertPatient(patient)
    }

    suspend fun updatePatient(patient: Patient) {
        dao.updatePatient(patient)
    }

    suspend fun deletePatientById(id: String) {
        dao.deletePatientById(id)
    }

    suspend fun insertSession(session: Session) {
        dao.insertSession(session)
    }

    suspend fun updateSession(session: Session) {
        dao.updateSession(session)
    }

    suspend fun deleteSessionById(id: String) {
        dao.deleteSessionById(id)
        dao.clearTurnsForSession(id)
    }

    suspend fun insertTurn(turn: SessionTurn) {
        dao.insertTurn(turn)
    }

    suspend fun updateTurn(turn: SessionTurn) {
        dao.updateTurn(turn)
    }

    suspend fun deleteTurn(turn: SessionTurn) {
        dao.deleteTurn(turn)
    }

    suspend fun clearTurnsForSession(sessionId: String) {
        dao.clearTurnsForSession(sessionId)
    }

    suspend fun insertDocument(doc: DocItem) {
        dao.insertDocument(doc)
    }

    suspend fun deleteDocumentById(id: String) {
        dao.deleteDocumentById(id)
        dao.deleteChunksByDocId(id)
        clearChunksCache()
    }

    suspend fun insertDocumentChunk(chunk: DocumentChunk) {
        dao.insertDocumentChunk(chunk)
        clearChunksCache()
    }

    suspend fun insertDocumentChunks(chunks: List<DocumentChunk>) {
        dao.insertDocumentChunks(chunks)
        clearChunksCache()
    }

    suspend fun getChunksForDocument(docId: String): List<DocumentChunk> {
        return cachedDocChunks.getOrPut(docId) {
            dao.getChunksForDocument(docId)
        }
    }

    suspend fun getAllChunks(): List<DocumentChunk> {
        return cachedAllChunks ?: chunksMutex.withLock {
            cachedAllChunks ?: dao.getAllChunks().also { cachedAllChunks = it }
        }
    }

    suspend fun deleteChunksByDocId(docId: String) {
        dao.deleteChunksByDocId(docId)
        clearChunksCache()
    }

    // Populate default database guides and sample patients for testing
    suspend fun prepopulateIfEmpty() {
        // Kept clean of preloaded data as requested
    }
}
