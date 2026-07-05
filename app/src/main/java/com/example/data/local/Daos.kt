package com.example.data.local

import androidx.room.*
import com.example.data.model.DocItem
import com.example.data.model.Patient
import com.example.data.model.Session
import com.example.data.model.SessionTurn
import com.example.data.model.DocumentChunk
import kotlinx.coroutines.flow.Flow

@Dao
interface MediAgentDao {

    // --- Patient Operations ---
    @Query("SELECT * FROM patients WHERE currentDoctorId = :doctorId ORDER BY createdAt DESC")
    fun getAllPatientsForDoctor(doctorId: String): Flow<List<Patient>>

    @Query("SELECT * FROM patients ORDER BY createdAt DESC")
    fun getAllPatients(): Flow<List<Patient>>

    @Query("SELECT * FROM patients WHERE id = :id")
    suspend fun getPatientById(id: String): Patient?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatient(patient: Patient)

    @Update
    suspend fun updatePatient(patient: Patient)

    @Query("DELETE FROM patients WHERE id = :id")
    suspend fun deletePatientById(id: String)

    // --- Session Operations ---
    @Query("SELECT * FROM sessions WHERE patientId = :patientId ORDER BY startedAt DESC")
    fun getSessionsForPatient(patientId: String): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: String): Session?

    @Query("SELECT * FROM sessions")
    suspend fun getAllSessions(): List<Session>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)

    @Update
    suspend fun updateSession(session: Session)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    // --- Session Turn Operations ---
    @Query("SELECT * FROM session_turns WHERE sessionId = :sessionId ORDER BY turnIndex ASC")
    fun getTurnsForSession(sessionId: String): Flow<List<SessionTurn>>

    @Query("SELECT * FROM session_turns")
    suspend fun getAllTurns(): List<SessionTurn>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTurn(turn: SessionTurn)

    @Update
    suspend fun updateTurn(turn: SessionTurn)

    @Delete
    suspend fun deleteTurn(turn: SessionTurn)

    @Query("DELETE FROM session_turns WHERE sessionId = :sessionId")
    suspend fun clearTurnsForSession(sessionId: String)

    // --- Document Operations ---
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun getAllDocuments(): Flow<List<DocItem>>

    @Query("SELECT * FROM documents")
    suspend fun getAllDocumentsRaw(): List<DocItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocItem)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    // --- Document Chunk Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocumentChunk(chunk: DocumentChunk)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocumentChunks(chunks: List<DocumentChunk>)

    @Query("SELECT * FROM document_chunks WHERE docId = :docId")
    suspend fun getChunksForDocument(docId: String): List<DocumentChunk>

    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<DocumentChunk>

    @Query("DELETE FROM document_chunks WHERE docId = :docId")
    suspend fun deleteChunksByDocId(docId: String)
}
