package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "patients")
data class Patient(
    @PrimaryKey val id: String,
    val currentDoctorId: String = "",
    val patientCode: String,
    val fullName: String,
    val dateOfBirth: String,
    val gender: String = "Male",
    val contact: String,
    val chronicConditions: String, // Comma-separated list
    val allergies: String,         // Comma-separated list
    val currentMedications: String, // Comma-separated list
    val notes: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val id: String,
    val doctorId: String,
    val patientId: String,
    val status: String, // "ACTIVE" | "CLOSED"
    val aiSummary: String?,
    val startedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val fee: Double = 0.0
)

@Entity(tableName = "session_turns")
data class SessionTurn(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String, // "doctor" | "assistant"
    val textContent: String,
    val jsonData: String, // JSON payload containing questions or options
    val turnIndex: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "documents")
data class DocItem(
    @PrimaryKey val id: String,
    val fileSource: String, // Name of the uploaded resource
    val fileType: String,      // "pdf" | "txt" | "html"
    val r2Url: String,
    val status: String,        // "PROCESSING" | "READY" | "FAILED"
    val pageCount: Int,
    val summary: String,
    val primaryConditions: String, // Comma-separated list
    val isPriority: Boolean = false,
    val categoryName: String = "",
    val categoryColor: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_chunks")
data class DocumentChunk(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val docId: String,
    val docSource: String,
    val chunkText: String,
    val embeddingJson: String, // Stringified JSON array of floats (vector)
    val pageIndex: Int,
    val imagePath: String? = null
)
