package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DocItem
import com.example.data.model.Patient
import com.example.data.model.Session
import com.example.data.model.SessionTurn
import com.example.ui.viewmodel.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin

// Theme Custom Colors following guidelines (Professional Polish Aesthetic)
val SlateBackground = Color(0xFFFDFBFF) // Soft tinted background
val SlateCard = Color(0xFFFFFFFF)       // Pure white card surfaces
val TealAccent = Color(0xFF001453)      // Brand Active Primary (Deep Navy/Indigo)
val CoralHighlight = Color(0xFFF43F5E)  // Vibrant Coral Accent (Stop/Warning)
val MintSuccess = Color(0xFF10B981)     // High-contrast emerald green
val GoldWarning = Color(0xFFF59E0B)     // High-contrast gold warning
val CoolGrayText = Color(0xFF44474E)    // Charcoal neutral secondary text

val PolishDarkText = Color(0xFF1B1B1F)  // Primary deep text
val PolishBorder = Color(0xFFC4C6CF)    // Subtle gray outline/divider
val PolishHighlight = Color(0xFFDDE1FF) // Lavender selection/banner focus
val PolishNeutralBg = Color(0xFFF3F4F9) // Quiet gray-blue card backing/fills

@Composable
fun polishTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PolishDarkText,
    unfocusedTextColor = PolishDarkText,
    focusedLabelColor = TealAccent,
    unfocusedLabelColor = CoolGrayText,
    focusedBorderColor = TealAccent,
    unfocusedBorderColor = PolishBorder
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediAgentApp(viewModel: MediAgentViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Could handle failure gracefully if not granted
    }
    
    LaunchedEffect(Unit) {
        val hasMic = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            permLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = currentScreen != AppScreen.LOGIN) {
        if (!viewModel.navigateBack()) {
            // let system handle back if we can't pop
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SlateBackground
    ) {
        Crossfade(targetState = currentScreen, label = "screen_transition") { screen ->
            when (screen) {
                AppScreen.LOGIN -> LoginScreen(viewModel)
                AppScreen.PATIENT_LIST -> PatientListScreen(viewModel)
                AppScreen.PATIENT_DETAIL -> PatientDetailScreen(viewModel)
                AppScreen.ACTIVE_SESSION -> ActiveSessionScreen(viewModel)
                AppScreen.PAST_SESSION_REVIEW -> PastSessionReviewScreen(viewModel)
                AppScreen.DOCUMENTS_MANAGER -> DocumentsManagerScreen(viewModel)
                AppScreen.SETTINGS -> SettingsScreen(viewModel)
            }
        }
    }
}

// ---------------- SCREEN 1: LOGIN ----------------
@Composable
fun LoginScreen(viewModel: MediAgentViewModel) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var doctorName by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "MediAgent",
            color = TealAccent,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif
        )
        Text(
            text = "Clinical Consultation Assistant MVP",
            color = CoolGrayText,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUp) "Doctor Registration" else "Doctor Authentication",
                    color = PolishDarkText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Doctor Name (Only for Sign Up)
                AnimatedVisibility(visible = isSignUp) {
                    Column {
                        OutlinedTextField(
                            value = doctorName,
                            onValueChange = { doctorName = it; authError = null },
                            label = { Text("Doctor Full Name") },
                            colors = polishTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // Email
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; authError = null },
                    label = { Text("Email Address") },
                    colors = polishTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; authError = null },
                    label = { Text("Access Password") },
                    colors = polishTextFieldColors(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = "toggle password check",
                                tint = CoolGrayText
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Error notice block
                AnimatedVisibility(visible = authError != null) {
                    Text(
                        text = authError ?: "",
                        color = CoralHighlight,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        val trimmedEmail = email.replace(" ", "")
                        val trimmedPassword = password.replace(" ", "")
                        val isSanta = trimmedEmail.equals("santa", ignoreCase = true) && trimmedPassword == "santa"
                        if (isSanta) {
                            isSubmitting = true
                            coroutineScope.launch {
                                delay(600L)
                                isSubmitting = false
                                viewModel.setDoctorProfile("santa", "santa")
                                viewModel.navigateTo(AppScreen.PATIENT_LIST)
                            }
                            return@Button
                        }

                        val isEmailValid = android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()
                        if (trimmedEmail.isBlank() || trimmedPassword.isBlank() || (isSignUp && doctorName.isBlank())) {
                            authError = "All fields are required"
                            return@Button
                        }
                        if (!isEmailValid) {
                            authError = "Please enter a valid email format"
                            return@Button
                        }
                        if (trimmedPassword.length < 6) {
                            authError = "Password must be at least 6 characters"
                            return@Button
                        }
                        
                        isSubmitting = true
                        coroutineScope.launch {
                            delay(1000L)
                            isSubmitting = false
                            if (isSignUp) {
                                viewModel.setDoctorProfile(doctorName, trimmedEmail)
                                viewModel.navigateTo(AppScreen.PATIENT_LIST)
                            } else {
                                // Default logic for signed up user since there assumes any successful user
                                viewModel.setDoctorProfile("Dr. Jenkins", trimmedEmail)
                                viewModel.navigateTo(AppScreen.PATIENT_LIST)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(if (isSignUp) "Register Account" else "Sign In", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = if (isSignUp) "Already have an account? Sign In" else "Create new credential? Sign Up",
                    color = TealAccent,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        isSignUp = !isSignUp
                        email = ""
                        password = ""
                        authError = null
                    }
                )
            }
        }
    }
}

// ---------------- SCREEN 2: PATIENT LIST ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientListScreen(viewModel: MediAgentViewModel) {
    val doctorName by viewModel.doctorName.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val patients by viewModel.patientsList.collectAsStateWithLifecycle()
    
    var showAddPatientSheet by remember { mutableStateOf(false) }
    var patientToManage by remember { mutableStateOf<Patient?>(null) }
    var showEditPatientFromList by remember { mutableStateOf(false) }
    var showDeletePatientFromList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MediAgent Consultation Workspace", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                        Text(doctorName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.DOCUMENTS_MANAGER) }) {
                        Icon(Icons.Default.List, contentDescription = "Clinic Guidelines", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }) {
                        Icon(Icons.Default.Settings, contentDescription = "System Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealAccent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddPatientSheet = true },
                containerColor = TealAccent,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Register New Patient", modifier = Modifier.size(28.dp))
            }
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // High-contrast Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("Search by name or clinical key...", color = CoolGrayText) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "search", tint = CoolGrayText) },
                colors = polishTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            if (patients.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AccountBox, contentDescription = "empty patients", tint = CoolGrayText, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No matching patient records found", color = CoolGrayText, fontSize = 16.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(patients) { patient ->
                        PatientCardRow(
                            patient = patient,
                            onTap = { viewModel.selectPatient(patient) },
                            onLongTap = { patientToManage = patient }
                        )
                    }
                }
            }
        }

        // Add Patient Bottom Sheet Modal Dialog
        if (showAddPatientSheet) {
            AddPatientDialog(
                onDismiss = { showAddPatientSheet = false },
                onSave = { name, dob, phone, allergy, chronic, md, notes, gender ->
                    viewModel.createPatient(name, dob, phone, chronic, allergy, md, notes, gender)
                    showAddPatientSheet = false
                }
            )
        }

        if (patientToManage != null && showEditPatientFromList) {
            EditPatientDialog(
                patient = patientToManage!!,
                onDismiss = {
                    showEditPatientFromList = false
                    patientToManage = null
                },
                onSave = { name, dob, phone, allergy, chronic, meds, notes, gender ->
                    viewModel.updatePatientDetails(patientToManage!!.id, name, dob, phone, chronic, allergy, meds, notes, gender)
                    showEditPatientFromList = false
                    patientToManage = null
                }
            )
        }

        if (patientToManage != null && showDeletePatientFromList) {
            AlertDialog(
                onDismissRequest = {
                    showDeletePatientFromList = false
                    patientToManage = null
                },
                title = { Text("Delete Patient Profile?", fontWeight = FontWeight.Bold, color = PolishDarkText) },
                text = { Text("Are you sure you want to permanently delete ${patientToManage!!.fullName}'s record? All associated consult summaries and transcript history segments will be forever expunged from the workstation.", color = CoolGrayText) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePatient(patientToManage!!)
                            showDeletePatientFromList = false
                            patientToManage = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight)
                    ) {
                        Text("Delete Forever", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDeletePatientFromList = false
                        patientToManage = null
                    }) {
                        Text("Cancel", color = TealAccent)
                    }
                },
                containerColor = SlateCard
            )
        }

        if (patientToManage != null && !showEditPatientFromList && !showDeletePatientFromList) {
            AlertDialog(
                onDismissRequest = { patientToManage = null },
                title = { Text("Manage Patient Profile", fontWeight = FontWeight.Bold, color = PolishDarkText) },
                text = { Text("Select an action to manage ${patientToManage!!.fullName}'s electronic medical record:", color = CoolGrayText) },
                confirmButton = {
                    Button(
                        onClick = { showEditPatientFromList = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                    ) {
                        Text("Edit Demographics")
                    }
                },
                dismissButton = {
                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showDeletePatientFromList = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight)
                        ) {
                            Text("Delete Record", color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { patientToManage = null }) {
                            Text("Cancel", color = CoolGrayText)
                        }
                    }
                },
                containerColor = SlateCard
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PatientCardRow(patient: Patient, onTap: () -> Unit, onLongTap: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongTap
            )
            .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile Initials Badge with asymmetric color highlights
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(TealAccent, PolishHighlight))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = patient.fullName.split(" ").map { it.firstOrNull() ?: 'P' }.joinToString(""),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(patient.fullName, color = PolishDarkText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .background(TealAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(patient.patientCode, color = TealAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Allergies: ${if (patient.allergies.isBlank()) "None declared" else patient.allergies}",
                    color = CoolGrayText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Conditions: ${if (patient.chronicConditions.isBlank()) "No chronic concerns" else patient.chronicConditions}",
                    color = TealAccent,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------- DIALOG: REGISTER NEW PATIENT ----------------
@Composable
fun AddPatientDialog(onDismiss: () -> Unit, onSave: (String, String, String, String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var entryByAge by remember { mutableStateOf(false) }
    var dob by remember { mutableStateOf("") }
    var ageStr by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var phone by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var chronicConditions by remember { mutableStateOf("") }
    var medications by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun updateDobAndCalcAge(newDob: String) {
        dob = newDob
        val dobRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (dobRegex.matches(newDob)) {
            val parts = newDob.split("-")
            val year = parts[0].toIntOrNull()
            if (year != null && year in 1900..2026) {
                ageStr = (2026 - year).toString()
            }
        } else {
            val yearPart = newDob.substringBefore("-")
            val year = yearPart.toIntOrNull()
            if (year != null && yearPart.length == 4 && year in 1900..2026) {
                ageStr = (2026 - year).toString()
            }
        }
    }

    fun updateAgeAndCalcDob(newAge: String) {
        ageStr = newAge
        val ageVal = newAge.toIntOrNull()
        if (ageVal != null && ageVal in 0..120) {
            val birthYear = 2026 - ageVal
            dob = "$birthYear-01-01"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        title = { Text("Register New Patient Account", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Legal Name") },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Patient Gender", color = TealAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("Male", "Female", "Other").forEach { g ->
                                val isSelected = gender == g
                                Card(
                                    onClick = { gender = g },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) TealAccent else SlateCard
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.Transparent else PolishBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = g,
                                            color = if (isSelected) Color.White else PolishDarkText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (entryByAge) "Enter Age (Calculates DOB Year)" else "Enter DOB (Calculates Age)",
                            color = TealAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { entryByAge = !entryByAge }) {
                            Text(
                                text = if (entryByAge) "Switch to DOB" else "Switch to Age",
                                color = CoralHighlight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item {
                    if (entryByAge) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { updateAgeAndCalcDob(it) },
                            label = { Text("Patient Age") },
                            colors = polishTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = dob,
                            onValueChange = { updateDobAndCalcAge(it) },
                            label = { Text("Date of Birth (YYYY-MM-DD)") },
                            colors = polishTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TealAccent.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "DOB: ${dob.ifBlank { "Not parsed yet" }} | Age: ${ageStr.ifBlank { "Not parsed yet" }}",
                            fontSize = 11.sp,
                            color = PolishDarkText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Contact Number") },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = chronicConditions,
                        onValueChange = { chronicConditions = it },
                        label = { Text("Chronic Conditions (comma separated)") },
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = allergies,
                        onValueChange = { allergies = it },
                        label = { Text("Clinical Allergies (comma separated)") },
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = medications,
                        onValueChange = { medications = it },
                        label = { Text("Current Medications (comma separated)") },
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("General Intake Notes") },
                        colors = polishTextFieldColors(),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    if (validationError != null) {
                        Text(text = validationError ?: "", color = CoralHighlight, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val dobRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
                    val phoneRegex = Regex("^[+]?[0-9]{10,15}$")
                    if (name.isBlank()) {
                        validationError = "Name cannot be empty"
                    } else if (!dobRegex.matches(dob)) {
                        validationError = "DOB must be in YYYY-MM-DD format (either input DOB or enter valid numerical Age)"
                    } else if (!phoneRegex.matches(phone)) {
                        validationError = "Phone must be a valid 10-15 digit number"
                    } else {
                        validationError = null
                        onSave(name, dob, phone, allergies, chronicConditions, medications, notes, gender) 
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CoralHighlight)
            }
        },
        containerColor = SlateCard
    )
}

@Composable
fun EditPatientDialog(
    patient: Patient,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(patient.fullName) }
    var entryByAge by remember { mutableStateOf(false) }
    var dob by remember { mutableStateOf(patient.dateOfBirth) }
    var ageStr by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(patient.gender) }
    var phone by remember { mutableStateOf(patient.contact) }
    var allergies by remember { mutableStateOf(patient.allergies) }
    var chronicConditions by remember { mutableStateOf(patient.chronicConditions) }
    var medications by remember { mutableStateOf(patient.currentMedications) }
    var notes by remember { mutableStateOf(patient.notes) }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(patient.dateOfBirth) {
        val parts = patient.dateOfBirth.split("-")
        val year = parts.getOrNull(0)?.toIntOrNull()
        if (year != null && year in 1900..2026) {
            ageStr = (2026 - year).toString()
        }
    }

    fun updateDobAndCalcAge(newDob: String) {
        dob = newDob
        val dobRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        if (dobRegex.matches(newDob)) {
            val parts = newDob.split("-")
            val year = parts[0].toIntOrNull()
            if (year != null && year in 1900..2026) {
                ageStr = (2026 - year).toString()
            }
        } else {
            val yearPart = newDob.substringBefore("-")
            val year = yearPart.toIntOrNull()
            if (year != null && yearPart.length == 4 && year in 1900..2026) {
                ageStr = (2026 - year).toString()
            }
        }
    }

    fun updateAgeAndCalcDob(newAge: String) {
        ageStr = newAge
        val ageVal = newAge.toIntOrNull()
        if (ageVal != null && ageVal in 0..120) {
            val birthYear = 2026 - ageVal
            dob = "$birthYear-01-01"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false),
        title = { Text("Edit Patient Demographics", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Full Legal Name") },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Patient Gender", color = TealAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("Male", "Female", "Other").forEach { g ->
                                val isSelected = gender == g
                                Card(
                                    onClick = { gender = g },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) TealAccent else SlateCard
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(
                                            1.dp,
                                            if (isSelected) Color.Transparent else PolishBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = g,
                                            color = if (isSelected) Color.White else PolishDarkText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (entryByAge) "Enter Age (Calculates DOB Year)" else "Enter DOB (Calculates Age)",
                            color = TealAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { entryByAge = !entryByAge }) {
                            Text(
                                text = if (entryByAge) "Switch to DOB" else "Switch to Age",
                                color = CoralHighlight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item {
                    if (entryByAge) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { updateAgeAndCalcDob(it) },
                            label = { Text("Patient Age") },
                            colors = polishTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = dob,
                            onValueChange = { updateDobAndCalcAge(it) },
                            label = { Text("Date of Birth (YYYY-MM-DD)") },
                            colors = polishTextFieldColors(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TealAccent.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "DOB: ${dob.ifBlank { "Not parsed yet" }} | Age: ${ageStr.ifBlank { "Not parsed yet" }}",
                            fontSize = 11.sp,
                            color = PolishDarkText,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Contact Number") },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = chronicConditions,
                        onValueChange = { chronicConditions = it },
                        label = { Text("Chronic Conditions (comma separated)") },
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = allergies,
                        onValueChange = { allergies = it },
                        label = { Text("Clinical Allergies (comma separated)") },
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = medications,
                        onValueChange = { medications = it },
                        label = { Text("Current Medications (comma separated)") },
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("General History Notes") },
                        colors = polishTextFieldColors(),
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    if (validationError != null) {
                        Text(text = validationError ?: "", color = CoralHighlight, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    val dobRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
                    val phoneRegex = Regex("^[+]?[0-9]{10,15}$")
                    if (name.isBlank()) {
                        validationError = "Name cannot be empty"
                    } else if (!dobRegex.matches(dob)) {
                        validationError = "DOB must be in YYYY-MM-DD format"
                    } else if (!phoneRegex.matches(phone)) {
                        validationError = "Phone must be a valid 10-15 digit number"
                    } else {
                        validationError = null
                        onSave(name, dob, phone, allergies, chronicConditions, medications, notes, gender) 
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Text("Save Changes", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CoralHighlight)
            }
        },
        containerColor = SlateCard
    )
}

// ---------------- SCREEN 3: PATIENT DETAIL ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(viewModel: MediAgentViewModel) {
    val patient by viewModel.selectedPatient.collectAsStateWithLifecycle()
    val sessions by viewModel.patientSessions.collectAsStateWithLifecycle()
    var sessionToDelete by remember { mutableStateOf<Session?>(null) }
    var showEditPatientDialog by remember { mutableStateOf(false) }
    var showDeletePatientDialog by remember { mutableStateOf(false) }

    if (sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = { Text("Delete This Session?", fontWeight = FontWeight.Bold, color = PolishDarkText) },
            text = { Text("Are you sure you want to permanently delete this clinical session? The transcripts and summaries of this session will be removed.", color = CoolGrayText) },
            confirmButton = {
                Button(
                    onClick = {
                        sessionToDelete?.let { viewModel.deleteSession(it) }
                        sessionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", color = TealAccent)
                }
            },
            containerColor = SlateCard
        )
    }

    if (showEditPatientDialog && patient != null) {
        EditPatientDialog(
            patient = patient!!,
            onDismiss = { showEditPatientDialog = false },
            onSave = { name, dob, phone, allergy, chronic, md, notes, gender ->
                viewModel.updatePatientDetails(patient!!.id, name, dob, phone, chronic, allergy, md, notes, gender)
                showEditPatientDialog = false
            }
        )
    }

    if (showDeletePatientDialog && patient != null) {
        AlertDialog(
            onDismissRequest = { showDeletePatientDialog = false },
            title = { Text("Delete Patient Profile?", fontWeight = FontWeight.Bold, color = PolishDarkText) },
            text = { Text("Are you sure you want to permanently delete ${patient!!.fullName}'s record? All associated consult summaries and transcript history segments will be forever expunged from the workstation.", color = CoolGrayText) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePatient(patient!!)
                        showDeletePatientDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight)
                ) {
                    Text("Delete Forever", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePatientDialog = false }) {
                    Text("Cancel", color = TealAccent)
                }
            },
            containerColor = SlateCard
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clinical History Detail", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showEditPatientDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = Color.White)
                    }
                    IconButton(onClick = { showDeletePatientDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealAccent)
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        patient?.let { pat ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section 1: Patient Quick Overview Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlateCard)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pat.fullName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PolishDarkText)
                                Text(pat.patientCode, color = TealAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = PolishBorder)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Basic Information", fontSize = 15.sp, color = TealAccent, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("🎂 DOB: ${pat.dateOfBirth}   |   ⚧ Gender: ${pat.gender}", color = PolishDarkText)
                            Text("📞 Contact: ${pat.contact}", color = PolishDarkText)
                            
                            val regSdf = remember { java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm", java.util.Locale.getDefault()) }
                            val regDateStr = remember(pat.createdAt) { regSdf.format(java.util.Date(pat.createdAt)) }
                            Text("📅 Registered: $regDateStr", color = PolishDarkText)

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Chronic Diseases / Vitals", fontSize = 15.sp, color = TealAccent, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(pat.chronicConditions, color = PolishDarkText)

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Current Medications", fontSize = 15.sp, color = TealAccent, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(pat.currentMedications, color = PolishDarkText)

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Known Allergies", fontSize = 15.sp, color = CoralHighlight, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(pat.allergies, color = PolishDarkText)
                        }
                    }
                }

                // Call to Action (Start live transcription consultation)
                item {
                    Button(
                        onClick = { viewModel.startNewSession(pat.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "mic icon", modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Record Consult Session", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // List of Past Sessions
                item {
                    Text("Session Consultation History", color = PolishDarkText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                if (sessions.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCard)
                        ) {
                            Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No clinical sessions on file yet. Tap Record to start.", color = CoolGrayText, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(sessions) { s ->
                        SessionHistoryRow(
                            session = s,
                            onTap = { viewModel.selectPastSession(s) },
                            onLongTap = { sessionToDelete = s },
                            onResume = { viewModel.continuePastSession(s) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionHistoryRow(session: Session, onTap: () -> Unit, onLongTap: () -> Unit, onResume: (() -> Unit)? = null) {
    val dateText = SimpleDateFormat("MMM d, yyyy - HH:mm", Locale.getDefault()).format(Date(session.startedAt))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongTap)
            .border(1.dp, PolishBorder, RoundedCornerShape(10.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateText, color = PolishDarkText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .background(
                            if (session.status == "ACTIVE") TealAccent.copy(alpha = 0.15f) else PolishHighlight,
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        session.status,
                        color = TealAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = session.aiSummary ?: "No session summary details on file.",
                fontSize = 13.sp,
                color = CoolGrayText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (session.status == "CLOSED") {
                Spacer(modifier = Modifier.height(6.dp))
                val feeFormatted = remember(session.fee) { String.format("%.2f", session.fee) }
                Text(
                    text = "💰 Collected Consult Fee: $$feeFormatted",
                    color = TealAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            if (onResume != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = PolishBorder.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onResume,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "continue session",
                            tint = TealAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Continue Session", color = TealAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- SCREEN 4: PAST SESSION REVIEW ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastSessionReviewScreen(viewModel: MediAgentViewModel) {
    val session by viewModel.currentPastSession.collectAsStateWithLifecycle()
    val turns by viewModel.currentPastTurns.collectAsStateWithLifecycle()

    val docNotesFromTurns = remember(turns) {
        turns.filter { it.role == "assistant" && it.jsonData.isNotBlank() && it.jsonData != "{}" }
            .mapNotNull {
                try {
                    val obj = org.json.JSONObject(it.jsonData)
                    val notes = obj.optString("doctor_treatment_notes", "")
                    if (notes.isNotBlank()) notes else null
                } catch (e: Exception) {
                    null
                }
            }.firstOrNull() ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session Consultation Log review", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealAccent)
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        session?.let { s ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = { viewModel.continuePastSession(s) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintSuccess),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Continue", tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Continue Conversation & Consultation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlateCard)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Summary Narrative", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(s.aiSummary ?: "No narrative file summaries", color = PolishDarkText, fontSize = 15.sp)
                        }
                    }
                }

                item {
                    val pastSessionImages by viewModel.pastSessionImages.collectAsStateWithLifecycle()
                    val processedPastImages = remember(pastSessionImages) { viewModel.processImagesForDisplay(pastSessionImages) }
                    var showPastExpandedDialog by remember { mutableStateOf(false) }
                    var pastExpandedImagePath by remember { mutableStateOf<String?>(null) }

                    if (processedPastImages.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCard)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Sourced medical visual reference figures",
                                        tint = TealAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("📷 Sourced Reference Figures", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Clinical guideline visuals referred to or cited during this session.", color = CoolGrayText, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                    modifier = Modifier.fillMaxWidth().height(120.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(processedPastImages) { path ->
                                        Card(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .fillMaxHeight()
                                                .clickable {
                                                    pastExpandedImagePath = path
                                                    showPastExpandedDialog = true
                                                },
                                            colors = CardDefaults.cardColors(containerColor = SlateBackground)
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                coil.compose.AsyncImage(
                                                    model = java.io.File(path),
                                                    contentDescription = "guideline reference figure preview",
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showPastExpandedDialog && pastExpandedImagePath != null) {
                            GuidelineImageGalleryDialog(
                                initialImagePath = pastExpandedImagePath!!,
                                allImages = pastSessionImages,
                                onDismissRequest = { showPastExpandedDialog = false },
                                viewModel = viewModel,
                                titleText = "Sourced Reference Figures"
                            )
                        }
                    }
                }

                if (docNotesFromTurns.isNotBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().border(1.dp, TealAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCard)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Applied Clinical Treatment",
                                        tint = TealAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Applied Clinical Treatment & Advice", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(docNotesFromTurns, color = PolishDarkText, fontSize = 15.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }

                item {
                    Text("Dialog Transcript Turns", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                if (turns.isEmpty()) {
                    item {
                        Text("No transcript data on record", color = CoolGrayText, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    items(turns) { turn ->
                        TranscriptTurnRow(
                            turn = turn,
                            onEdit = { updatedTurn -> viewModel.updateTurn(updatedTurn) },
                            onDelete = { turnToDelete -> viewModel.deleteTurn(turnToDelete) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TranscriptTurnRow(
    turn: SessionTurn,
    onEdit: ((SessionTurn) -> Unit)? = null,
    onDelete: ((SessionTurn) -> Unit)? = null
) {
    val isDoc = turn.role == "doctor"
    val bubbleShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = if (isDoc) 12.dp else 0.dp,
        bottomEnd = if (isDoc) 0.dp else 12.dp
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = if (isDoc) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isDoc) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = if (isDoc) Icons.Default.AccountBox else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isDoc) TealAccent else MintSuccess,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isDoc) "Doctor Input Intake" else "MediAgent Companion Sourced Solutions",
                color = if (isDoc) TealAccent else MintSuccess,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (onEdit != null || onDelete != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onEdit != null) {
                        var showEditDialog by remember { mutableStateOf(false) }
                        var editedText by remember(turn.id) { mutableStateOf(turn.textContent) }
                        
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit turn",
                                tint = TealAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        
                        if (showEditDialog) {
                            AlertDialog(
                                onDismissRequest = { showEditDialog = false },
                                title = { Text("Edit Dialogue Content", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                                text = {
                                    OutlinedTextField(
                                        value = editedText,
                                        onValueChange = { editedText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = polishTextFieldColors()
                                    )
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            onEdit(turn.copy(textContent = editedText))
                                            showEditDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                                    ) {
                                        Text("Save", color = Color.White)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEditDialog = false }) {
                                        Text("Cancel", color = CoralHighlight)
                                    }
                                },
                                containerColor = SlateCard
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = { onDelete(turn) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete turn",
                                tint = CoralHighlight,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        
        if (isDoc) {
            Box(
                modifier = Modifier
                    .background(PolishHighlight, bubbleShape)
                    .border(1.dp, PolishBorder.copy(alpha = 0.5f), bubbleShape)
                    .padding(12.dp)
                    .widthIn(max = 280.dp)
            ) {
                Text(
                    turn.textContent,
                    color = TealAccent,
                    fontSize = 14.sp,
                    lineHeight = 18.sp
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .background(SlateCard, bubbleShape)
                    .border(1.dp, PolishBorder, bubbleShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Narrative response summary
                Text(
                    text = turn.textContent,
                    color = PolishDarkText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp
                )
                
                var parsedNotes = ""
                var parsedDoctorNotes = ""
                val parsedSolutions = mutableListOf<com.example.ui.viewmodel.ClinicalSolution>()
                
                try {
                    if (turn.jsonData.isNotBlank() && turn.jsonData != "{}") {
                        val payload = org.json.JSONObject(turn.jsonData)
                        parsedNotes = payload.optString("session_notes", "")
                        parsedDoctorNotes = payload.optString("doctor_treatment_notes", "")
                        
                        val solutionsArray = payload.optJSONArray("solutions")
                        if (solutionsArray != null) {
                            for (i in 0 until solutionsArray.length()) {
                                val sObj = solutionsArray.getJSONObject(i)
                                val contras = mutableListOf<String>()
                                val diet = mutableListOf<String>()
                                val life = mutableListOf<String>()

                                val contrasArray = sObj.optJSONArray("contraindications")
                                if (contrasArray != null) {
                                    for (x in 0 until contrasArray.length()) contras.add(contrasArray.getString(x))
                                }
                                val dietArray = sObj.optJSONArray("dietary_plan")
                                if (dietArray != null) {
                                    for (x in 0 until dietArray.length()) diet.add(dietArray.getString(x))
                                }
                                val lifeArray = sObj.optJSONArray("lifestyle_modifications")
                                if (lifeArray != null) {
                                    for (x in 0 until lifeArray.length()) life.add(lifeArray.getString(x))
                                }

                                parsedSolutions.add(
                                    com.example.ui.viewmodel.ClinicalSolution(
                                        title = sObj.optString("title", "Clinical Protocol Option"),
                                        description = sObj.optString("description", ""),
                                        sourceDocument = sObj.optString("source_document", "Uploaded Guidelines"),
                                        sourcePage = sObj.optInt("source_page", 1),
                                        contraindications = contras,
                                        dietaryPlan = diet,
                                        lifestyleModifications = life,
                                        followUpTimeline = sObj.optString("follow_up_timeline", "7-14 Days"),
                                        drugInteractionsWarning = sObj.optString("drug_interactions_warning", null)
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback cleanly on non-JSON turns
                }
                
                var showParsedNotesWarning by remember(turn.id) { mutableStateOf(true) }
                if (parsedNotes.isNotBlank() && showParsedNotesWarning) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CoralHighlight.copy(alpha = 0.08f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CoralHighlight.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(end = 36.dp, start = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "warning",
                                    tint = CoralHighlight,
                                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(parsedNotes, color = CoolGrayText, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                            IconButton(
                                onClick = { showParsedNotesWarning = false },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                                    .padding(4.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "dismiss", tint = CoolGrayText, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                if (parsedDoctorNotes.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TealAccent.copy(alpha = 0.05f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, TealAccent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Doctor Treatment Guidance",
                                    tint = TealAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Doctor's Applied Treatment & Advice",
                                    color = TealAccent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = parsedDoctorNotes,
                                color = PolishDarkText,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }
                
                if (parsedSolutions.isNotEmpty()) {
                    Divider(color = PolishBorder, thickness = 0.5.dp)
                    Text("Sourced Clinical Guideline Standards:", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    parsedSolutions.forEach { sol ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PolishBorder, RoundedCornerShape(8.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCard.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(sol.title, color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(sol.description, color = CoolGrayText, fontSize = 12.sp, lineHeight = 16.sp)
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = "info", tint = CoolGrayText, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Source referred: ${sol.sourceDocument} (Page ${sol.sourcePage})",
                                        color = CoolGrayText,
                                        fontSize = 11.sp
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Followup Check: ",
                                        color = TealAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = sol.followUpTimeline,
                                        color = PolishDarkText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                
                                val dismissedDocWarnings = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
                                sol.drugInteractionsWarning?.let { warning ->
                                    if (warning.isNotBlank() && dismissedDocWarnings[warning] != true) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = GoldWarning.copy(alpha = 0.12f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                Row(modifier = Modifier.padding(end = 36.dp, start = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Warning, contentDescription = "warn", tint = GoldWarning, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(warning, color = PolishDarkText, fontSize = 11.sp, lineHeight = 15.sp)
                                                }
                                                IconButton(
                                                    onClick = { dismissedDocWarnings[warning] = true },
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(32.dp)
                                                        .padding(4.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "dismiss", tint = CoolGrayText, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- SCREEN 5: ACTIVE LIVE CONSULTATION SESSION ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(viewModel: MediAgentViewModel) {
    val liveState by viewModel.activeSessionState.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsedSeconds.collectAsStateWithLifecycle()
    val draftTrans by viewModel.liveTranscriptionDraft.collectAsStateWithLifecycle()
    val recStatus by viewModel.recordingStatus.collectAsStateWithLifecycle()
    val summary by viewModel.patientSummary.collectAsStateWithLifecycle()
    val confidence by viewModel.confidenceLevel.collectAsStateWithLifecycle()
    val notes by viewModel.sessionNotes.collectAsStateWithLifecycle()
    val preliminaryDirection by viewModel.preliminaryDirection.collectAsStateWithLifecycle()

    val questions by viewModel.interactiveQuestions.collectAsStateWithLifecycle()
    val solutions by viewModel.solutions.collectAsStateWithLifecycle()
    val lastRetrievedChunks by viewModel.lastRetrievedChunks.collectAsStateWithLifecycle()

    val isBgListening by viewModel.isBackgroundListeningActive.collectAsStateWithLifecycle()
    val bgDraft by viewModel.backgroundTranscriptionDraft.collectAsStateWithLifecycle()

    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val detectedUpdates by viewModel.detectedUpdates.collectAsStateWithLifecycle()
    val detailedErrorLog by viewModel.lastDetailedErrorLog.collectAsStateWithLifecycle()

    val isMainMuted by viewModel.isMainMicMuted.collectAsStateWithLifecycle()
    val isBgMuted by viewModel.isBackgroundMicMuted.collectAsStateWithLifecycle()
    val sessionTurns by viewModel.activeSessionTurns.collectAsStateWithLifecycle()
    var showActiveHistoryView by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var simulatingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    var showRagAuditDetails by remember { mutableStateOf(false) }
    var showRagExpandedDialog by remember { mutableStateOf(false) }
    var ragExpandedImagePath by remember { mutableStateOf<String?>(null) }
    var ragSourceDoc by remember { mutableStateOf("") }
    var ragSourcePage by remember { mutableStateOf(1) }
    var ragExpandedImagesList by remember { mutableStateOf<List<String>>(emptyList()) }

    var showBackExitConfirmationDialog by remember { mutableStateOf(false) }
    var showFeeDialog by remember { mutableStateOf(false) }
    var feeInputStr by remember { mutableStateOf("") }

    BackHandler(enabled = true) {
        showBackExitConfirmationDialog = true
    }

    val minutes = elapsed / 60
    val seconds = elapsed % 60
    val timerString = String.format("%02d:%02d", minutes, seconds)

    if (showActiveHistoryView) {
        AlertDialog(
            onDismissRequest = { showActiveHistoryView = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Active Turns",
                            tint = TealAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Live Session Dialogue turns",
                            color = PolishDarkText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    IconButton(onClick = { showActiveHistoryView = false }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = CoolGrayText
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "History of dialogue turns and assistant solutions captured during this consultation session:",
                        color = CoolGrayText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (sessionTurns.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(SlateBackground, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No dialogue turns recordered in this active session yet.",
                                color = CoolGrayText,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(SlateBackground, RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(sessionTurns) { turn ->
                                TranscriptTurnRow(
                                    turn = turn,
                                    onEdit = { updatedTurn -> viewModel.updateTurn(updatedTurn) },
                                    onDelete = { turnToDelete -> viewModel.deleteTurn(turnToDelete) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showActiveHistoryView = false },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Text("Return to Consult", color = Color.White)
                }
            },
            containerColor = SlateCard
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediAgent Core Consult", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { showBackExitConfirmationDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .background(CoralHighlight.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .clickable {
                                showActiveHistoryView = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Active Turns History",
                                tint = CoralHighlight,
                                modifier = Modifier.size(16.dp).padding(end = 4.dp)
                            )
                            Text(
                                timerString,
                                color = CoralHighlight,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealAccent)
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // AI grounding warning removed entirely

            if (detailedErrorLog != null) {
                var isExpanded by remember { mutableStateOf(false) }
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                val context = androidx.compose.ui.platform.LocalContext.current
                
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, CoralHighlight, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = CoralHighlight.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error Logo",
                                    tint = CoralHighlight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Consultation Pipeline Issue",
                                    fontWeight = FontWeight.Bold,
                                    color = CoralHighlight,
                                    fontSize = 13.sp
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { isExpanded = !isExpanded },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(
                                        text = if (isExpanded) "Hide Trace" else "Show Trace",
                                        color = CoolGrayText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(detailedErrorLog!!))
                                        android.widget.Toast.makeText(context, "Copied trace to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Copy Error Log",
                                        tint = CoolGrayText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearDetailedErrorLog() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = CoolGrayText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        
                        Text(
                            text = "A technical exception occurred while communicating with the Gemini model. You can inspect the diagnostic trace below or copy it to clipboard.",
                            fontSize = 11.sp,
                            color = PolishDarkText.copy(alpha = 0.8f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                        
                        if (isExpanded) {
                            HorizontalDivider(color = CoralHighlight.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = detailedErrorLog!!,
                                    color = Color(0xFFFFB3B3),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            when (liveState) {
                ActiveSessionState.RECORDING -> {
                    // Recording waveform layout
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                    ) {
                        Text("TRANSCRIPTION ACTIVE", color = TealAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

                        // Audio Waveform Drawing canvas
                        PulseWaveform()

                        // Dynamic status label
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TealAccent.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth().border(1.dp, TealAccent.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = TealAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (recStatus.contains("Error") || recStatus.contains("not warm")) 
                                        "Browser/Emulator Microphone isolated in Sandbox. Please use Preset templates below or type manually!" 
                                        else recStatus,
                                    color = PolishDarkText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Mute Mic toggle button
                        Button(
                            onClick = { viewModel.toggleMainMicMute() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMainMuted) CoralHighlight else TealAccent
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isMainMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = if (isMainMuted) "Unmute Mic" else "Mute Mic",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isMainMuted) "Unmute Microphone" else "Mute Microphone",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Editable input draft supporting full manual keyboard edit fallback
                        OutlinedTextField(
                            value = draftTrans,
                            onValueChange = { viewModel.updateLiveTranscriptionDraft(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp, max = 250.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 14.sp, lineHeight = 19.sp),
                            label = { Text("Consultation Intake Draft") },
                            placeholder = { Text("Speak or type patient notes directly here...", color = CoolGrayText.copy(alpha = 0.6f)) },
                            colors = polishTextFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Custom bullet notes section for doctor inside active session
                        var customNoteInputText by remember { mutableStateOf("") }
                        val customNotesList by viewModel.liveCustomNotes.collectAsStateWithLifecycle()

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCard)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Doctor's Custom Bullet Notes", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    if (customNotesList.isNotEmpty()) {
                                        TextButton(onClick = { viewModel.clearLiveCustomNotes() }) {
                                            Text("Clear All", color = CoralHighlight, fontSize = 11.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = customNoteInputText,
                                        onValueChange = { customNoteInputText = it },
                                        placeholder = { Text("Specify alternative directions, drug substitutions, custom questions, notes, or patient instructions here...", color = CoolGrayText.copy(alpha = 0.6f), fontSize = 12.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = polishTextFieldColors(),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 13.sp)
                                    )
                                    Button(
                                        onClick = {
                                            if (customNoteInputText.isNotBlank()) {
                                                viewModel.addLiveCustomNote(customNoteInputText)
                                                customNoteInputText = ""
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(40.dp)
                                    ) {
                                        Text("Add", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (customNoteInputText.isNotBlank()) {
                                        Button(
                                            onClick = {
                                                customNoteInputText = ""
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Text("Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (customNotesList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 125.dp)
                                            .background(SlateBackground, RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        LazyColumn(
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsIndexed(customNotesList) { idx, note ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(SlateCard, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text(
                                                        text = "• $note",
                                                        color = PolishDarkText,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    IconButton(
                                                        onClick = { viewModel.deleteLiveCustomNoteAt(idx) },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Delete note",
                                                            tint = CoralHighlight,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = { viewModel.forceStopRecording() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(CoralHighlight, CircleShape)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Process and Sourced Guidance", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Process & Sourced Guidance", color = CoolGrayText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                ActiveSessionState.THINKING -> {
                    val statsModel by viewModel.llmStatsModel.collectAsStateWithLifecycle()
                    val statsElapsed by viewModel.llmStatsElapsedTime.collectAsStateWithLifecycle()
                    val statsStage by viewModel.llmStatsStage.collectAsStateWithLifecycle()
                    val statsChunks by viewModel.llmStatsChunksReceived.collectAsStateWithLifecycle()
                    val statsChars by viewModel.llmStatsCharsReceived.collectAsStateWithLifecycle()
                    val statsPreview by viewModel.llmStatsStreamPreview.collectAsStateWithLifecycle()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TealAccent, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "MediAgent is analyzing context & processing medical knowledge vector database guides...",
                                color = PolishDarkText,
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = SlateCard)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "⚡ REAL-TIME LLM INFERENCE STATISTICS",
                                    color = TealAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )

                                Divider(color = PolishBorder, thickness = 0.5.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Active Model", color = CoolGrayText, fontSize = 10.sp)
                                        Text(statsModel.ifBlank { viewModel.selectedModel.value }, color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Elapsed Time", color = CoolGrayText, fontSize = 10.sp)
                                        Text(statsElapsed, color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Stage", color = CoolGrayText, fontSize = 10.sp)
                                        Text(statsStage, color = GoldWarning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Chunks / Chars", color = CoolGrayText, fontSize = 10.sp)
                                        Text("$statsChunks Chunks / $statsChars Chars", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                if (statsPreview.isNotBlank()) {
                                    Divider(color = PolishBorder, thickness = 0.5.dp)
                                    Text("Response preview:", color = CoolGrayText, fontSize = 9.sp)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .background(SlateBackground, RoundedCornerShape(6.dp))
                                            .padding(8.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = statsPreview,
                                            color = PolishDarkText,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ActiveSessionState.INTERACTIVE -> {
                    val answers = remember { mutableStateMapOf<String, String>() }
                    var showAddCustomQDialog by remember { mutableStateOf(false) }

                    // Question and intake loop layout
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Patient Summary Findings so far", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(summary, color = PolishDarkText, fontSize = 14.sp)
                                    }
                                }
                            }

                            if (!preliminaryDirection.isNullOrBlank()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().border(1.dp, TealAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = SlateCard)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "route",
                                                    tint = TealAccent,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Working Diagnostic Route", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(preliminaryDirection!!, color = PolishDarkText, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }

                            if (draftTrans.isNotBlank()) {
                                item {
                                    var showTranscriptDetails by remember { mutableStateOf(false) }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, PolishBorder, RoundedCornerShape(12.dp))
                                            .clickable { showTranscriptDetails = !showTranscriptDetails },
                                        colors = CardDefaults.cardColors(containerColor = SlateCard)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.List,
                                                        contentDescription = "Transcript",
                                                        tint = TealAccent,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "Recorded Intake Transcription Draft",
                                                        color = PolishDarkText,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = if (showTranscriptDetails) "Hide" else "Show",
                                                    color = TealAccent,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (showTranscriptDetails) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = draftTrans,
                                                    color = PolishDarkText,
                                                    fontSize = 13.sp,
                                                    lineHeight = 17.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                val isBgMuted by viewModel.isBackgroundMicMuted.collectAsStateWithLifecycle()
                                BackgroundListenerWidget(
                                    isBgListening = isBgListening,
                                    bgDraft = bgDraft,
                                    onToggle = { viewModel.toggleBackgroundListening() },
                                    onSend = { viewModel.sendBackgroundTranscription() },
                                    onDraftChange = { viewModel.updateBackgroundTranscriptionDraft(it) },
                                    isMicMuted = isBgMuted,
                                    onMuteToggle = { viewModel.toggleBackgroundMicMute() }
                                )
                            }

                            item {
                                var customNoteInputTextInteractive by remember { mutableStateOf("") }
                                val customNotesListInteractive by viewModel.liveCustomNotes.collectAsStateWithLifecycle()
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Doctor's Custom Bullet Notes", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (customNotesListInteractive.isNotEmpty()) {
                                                TextButton(onClick = { viewModel.clearLiveCustomNotes() }) {
                                                    Text("Clear All", color = CoralHighlight, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = customNoteInputTextInteractive,
                                                onValueChange = { customNoteInputTextInteractive = it },
                                                placeholder = { Text("Specify alternative directions, drug substitutions, custom questions, notes, or patient instructions here...", color = CoolGrayText.copy(alpha = 0.6f), fontSize = 12.sp) },
                                                modifier = Modifier.weight(1f),
                                                colors = polishTextFieldColors(),
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 13.sp)
                                            )
                                            Button(
                                                onClick = {
                                                    if (customNoteInputTextInteractive.isNotBlank()) {
                                                        viewModel.addLiveCustomNote(customNoteInputTextInteractive)
                                                        customNoteInputTextInteractive = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(40.dp)
                                            ) {
                                                Text("Add", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            if (customNoteInputTextInteractive.isNotBlank()) {
                                                Button(
                                                    onClick = {
                                                        customNoteInputTextInteractive = ""
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(40.dp)
                                                ) {
                                                    Text("Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        if (customNotesListInteractive.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 125.dp)
                                                    .background(SlateBackground, RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            ) {
                                                LazyColumn(
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    itemsIndexed(customNotesListInteractive) { idx, note ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(SlateCard, RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = "• $note",
                                                                color = PolishDarkText,
                                                                fontSize = 12.sp,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            IconButton(
                                                                onClick = { viewModel.deleteLiveCustomNoteAt(idx) },
                                                                modifier = Modifier.size(20.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Delete note",
                                                                    tint = CoralHighlight,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text("Interactive Clinical Questions", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            items(questions) { q ->
                                QuestionInputCard(q) { ans ->
                                    answers[q.id] = ans
                                }
                            }
                        }

                        // Static anchored action buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Button(
                                onClick = { showAddCustomQDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                border = BorderStroke(1.dp, TealAccent)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Add, contentDescription = "add", tint = TealAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Custom Question", color = TealAccent, fontSize = 11.sp)
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.triggerLlmForMoreQuestions() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                border = BorderStroke(1.dp, MintSuccess)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = "AI generate", tint = MintSuccess, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("AI Ask More", color = MintSuccess, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { viewModel.submitInteractiveAnswers(answers) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintSuccess)
                        ) {
                            Text("Submit Vital Answers", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        if (showAddCustomQDialog) {
                            var customQText by remember { mutableStateOf("") }
                            var customAnsText by remember { mutableStateOf("") }
                            var customError by remember { mutableStateOf<String?>(null) }
                            
                            AlertDialog(
                                onDismissRequest = { showAddCustomQDialog = false },
                                title = { Text("Add Custom Clinical Question", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            value = customQText,
                                            onValueChange = { customQText = it },
                                            label = { Text("Clinical Question", color = TealAccent) },
                                            placeholder = { Text("e.g. Do you have chest pain?") },
                                            colors = polishTextFieldColors(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedTextField(
                                            value = customAnsText,
                                            onValueChange = { customAnsText = it },
                                            label = { Text("Patient Response / Answer", color = TealAccent) },
                                            placeholder = { Text("e.g. No, only mild pressure when sitting.") },
                                            colors = polishTextFieldColors(),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (customError != null) {
                                            Text(customError ?: "", color = CoralHighlight, fontSize = 12.sp)
                                        }
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (customQText.isBlank() || customAnsText.isBlank()) {
                                                customError = "Both fields are required."
                                            } else {
                                                viewModel.addNewInteractiveQuestion(
                                                    field = "Custom Question",
                                                    questionText = customQText,
                                                    customAnswer = customAnsText
                                                )
                                                showAddCustomQDialog = false
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                                    ) {
                                        Text("Add Question & Answer", color = Color.White)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showAddCustomQDialog = false }) {
                                        Text("Cancel", color = CoralHighlight)
                                    }
                                },
                                containerColor = SlateCard
                            )
                        }
                    }
                }

                ActiveSessionState.FINAL_RESOLUTION -> {
                    val allImagesWithSource = remember(lastRetrievedChunks) {
                        val list = mutableListOf<Pair<String, com.example.data.model.DocumentChunk>>()
                        lastRetrievedChunks.forEach { pair ->
                            val chunk = pair.first
                            chunk.imagePath?.split(";")?.filter { it.isNotBlank() }?.forEach { path ->
                                if (list.none { it.first == path }) {
                                    list.add(Pair(path, chunk))
                                }
                            }
                        }
                        list
                    }

                    // Final formatted solution layouts
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            // High Alert Notes
                            item {
                                var showHighAlertWarning by remember { mutableStateOf(true) }
                                if (notes.isNotBlank() && showHighAlertWarning) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (notes.contains("⚠️") || notes.contains("WARNING")) CoralHighlight.copy(alpha = 0.1f) else SlateCard
                                        ),
                                        border = BorderStroke(1.dp, if (notes.contains("⚠️")) CoralHighlight else TealAccent)
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            Row(modifier = Modifier.padding(end = 36.dp, start = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Warning,
                                                    contentDescription = "alert",
                                                    tint = CoralHighlight,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(notes, color = PolishDarkText, fontSize = 13.sp)
                                            }
                                            IconButton(
                                                onClick = { showHighAlertWarning = false },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(32.dp)
                                                    .padding(4.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "dismiss", tint = CoolGrayText, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Display Sourced Interactive Questions and Answers
                            item {
                                val answeredQuestions = remember(questions) { questions.filter { !it.customAnswer.isNullOrBlank() } }
                                if (answeredQuestions.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                                        colors = CardDefaults.cardColors(containerColor = SlateCard)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = "Questions History",
                                                    tint = TealAccent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Diagnostic Questions & Answers History",
                                                    color = PolishDarkText,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(10.dp))
                                            answeredQuestions.forEach { q ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .background(SlateBackground, RoundedCornerShape(8.dp))
                                                        .padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = "[${q.field}] Question: ${q.questionText}",
                                                        color = TealAccent,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Answer: ${q.customAnswer ?: "No answer recorded"}",
                                                        color = PolishDarkText,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Background Environment Listener
                            item {
                                val isBgMuted by viewModel.isBackgroundMicMuted.collectAsStateWithLifecycle()
                                BackgroundListenerWidget(
                                    isBgListening = isBgListening,
                                    bgDraft = bgDraft,
                                    onToggle = { viewModel.toggleBackgroundListening() },
                                    onSend = { viewModel.sendBackgroundTranscription() },
                                    onDraftChange = { viewModel.updateBackgroundTranscriptionDraft(it) },
                                    isMicMuted = isBgMuted,
                                    onMuteToggle = { viewModel.toggleBackgroundMicMute() }
                                )
                            }

                            item {
                                var customNoteInputTextFinal by remember { mutableStateOf("") }
                                val customNotesListFinal by viewModel.liveCustomNotes.collectAsStateWithLifecycle()
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Doctor's Custom Bullet Notes", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            if (customNotesListFinal.isNotEmpty()) {
                                                TextButton(onClick = { viewModel.clearLiveCustomNotes() }) {
                                                    Text("Clear All", color = CoralHighlight, fontSize = 11.sp)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = customNoteInputTextFinal,
                                                onValueChange = { customNoteInputTextFinal = it },
                                                placeholder = { Text("Specify alternative directions, drug substitutions, custom questions, notes, or patient instructions here...", color = CoolGrayText.copy(alpha = 0.6f), fontSize = 12.sp) },
                                                modifier = Modifier.weight(1f),
                                                colors = polishTextFieldColors(),
                                                singleLine = true,
                                                textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 13.sp)
                                            )
                                            Button(
                                                onClick = {
                                                    if (customNoteInputTextFinal.isNotBlank()) {
                                                        viewModel.addLiveCustomNote(customNoteInputTextFinal)
                                                        customNoteInputTextFinal = ""
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                modifier = Modifier.height(40.dp)
                                            ) {
                                                Text("Add", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                            if (customNoteInputTextFinal.isNotBlank()) {
                                                Button(
                                                    onClick = {
                                                        customNoteInputTextFinal = ""
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(40.dp)
                                                ) {
                                                    Text("Cancel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }

                                        if (customNotesListFinal.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 125.dp)
                                                    .background(SlateBackground, RoundedCornerShape(8.dp))
                                                    .padding(6.dp)
                                            ) {
                                                LazyColumn(
                                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    itemsIndexed(customNotesListFinal) { idx, note ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(SlateCard, RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = "• $note",
                                                                color = PolishDarkText,
                                                                fontSize = 12.sp,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            IconButton(
                                                                onClick = { viewModel.deleteLiveCustomNoteAt(idx) },
                                                                modifier = Modifier.size(20.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Delete note",
                                                                    tint = CoralHighlight,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Doctor Treatment Notes
                            item {
                                val doctorNotesFinal by viewModel.doctorTreatmentNotes.collectAsStateWithLifecycle()
                                Card(
                                    modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Doctor's Custom Treatment, Questions or Extra Directives (Finalized)", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        OutlinedTextField(
                                            value = doctorNotesFinal,
                                            onValueChange = { viewModel.updateDoctorTreatmentNotes(it) },
                                            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 150.dp),
                                            placeholder = { Text("Specify alternative directions, drug substitutions, custom questions, notes, or patient instructions here...", color = CoolGrayText.copy(alpha = 0.6f), fontSize = 12.sp) },
                                            colors = polishTextFieldColors(),
                                            textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 13.sp, lineHeight = 17.sp),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("RAG Sourced Clinical Solutions", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                when (confidence) {
                                                    "HIGH" -> MintSuccess.copy(alpha = 0.15f)
                                                    "MEDIUM" -> TealAccent.copy(alpha = 0.15f)
                                                    else -> GoldWarning.copy(alpha = 0.15f)
                                                },
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "$confidence CONFIDENCE",
                                            color = when (confidence) {
                                                "HIGH" -> MintSuccess
                                                "MEDIUM" -> TealAccent
                                                else -> GoldWarning
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (draftTrans.isNotBlank()) {
                                item {
                                    var showTranscriptDetails by remember { mutableStateOf(false) }
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, PolishBorder, RoundedCornerShape(12.dp))
                                            .clickable { showTranscriptDetails = !showTranscriptDetails },
                                        colors = CardDefaults.cardColors(containerColor = SlateCard)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.List,
                                                        contentDescription = "Transcript",
                                                        tint = TealAccent,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Recorded Intake Transcription Draft",
                                                        color = PolishDarkText,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Text(
                                                    text = if (showTranscriptDetails) "Hide" else "Show",
                                                    color = TealAccent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                            if (showTranscriptDetails) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    text = draftTrans,
                                                    color = PolishDarkText,
                                                    fontSize = 14.sp,
                                                    lineHeight = 18.sp,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = 1.dp,
                                            color = if (lastRetrievedChunks.isNotEmpty()) TealAccent.copy(alpha = 0.5f) else PolishBorder,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable { showRagAuditDetails = !showRagAuditDetails },
                                    colors = CardDefaults.cardColors(containerColor = SlateCard)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = "RAG Audit",
                                                    tint = if (lastRetrievedChunks.isNotEmpty()) TealAccent else CoolGrayText,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "RAG Reference Chunks Audit (${lastRetrievedChunks.size} chunks)",
                                                    color = PolishDarkText,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Text(
                                                text = if (showRagAuditDetails) "Hide" else "Audit Chunks",
                                                color = TealAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            if (showRagAuditDetails) {
                                if (lastRetrievedChunks.isEmpty()) {
                                    item {
                                        Text(
                                            text = "No active local vector sources were query-targeted or matched for this turn. (Upload booklets in Guidelines tab to seed RAG!)",
                                            color = CoolGrayText,
                                            fontSize = 12.sp,
                                            fontStyle = FontStyle.Italic,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                } else {
                                    if (allImagesWithSource.isNotEmpty()) {
                                        item {
                                            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                                                Text("📷 Sourced Medical Figures & Gallery", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Horizontal view of all referred graphical charts, diagrams, and figures from RAG chunks:", color = CoolGrayText, fontSize = 11.sp)
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth().height(110.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(allImagesWithSource) { (path, chunk) ->
                                                        Card(
                                                            modifier = Modifier
                                                                .width(100.dp)
                                                                .fillMaxHeight()
                                                                .clickable {
                                                                    ragExpandedImagePath = path
                                                                    ragExpandedImagesList = allImagesWithSource.map { it.first }
                                                                    ragSourceDoc = chunk.docSource
                                                                    ragSourcePage = chunk.pageIndex
                                                                    showRagExpandedDialog = true
                                                                },
                                                            colors = CardDefaults.cardColors(containerColor = SlateCard),
                                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                                        ) {
                                                            Box(modifier = Modifier.fillMaxSize()) {
                                                                coil.compose.AsyncImage(
                                                                    model = java.io.File(path),
                                                                    contentDescription = "guideline figure thumbnail",
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                                )
                                                                Box(
                                                                    modifier = Modifier
                                                                        .align(Alignment.BottomCenter)
                                                                        .fillMaxWidth()
                                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                                        .padding(vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = "Page ${chunk.pageIndex}",
                                                                        color = Color.White,
                                                                        fontSize = 9.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                                        modifier = Modifier.fillMaxWidth()
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Divider(color = PolishBorder, thickness = 0.5.dp)
                                            }
                                        }
                                    }

                                    itemsIndexed(
                                        items = lastRetrievedChunks.toList(),
                                        key = { idx, pair -> "${pair.first.id}_$idx" }
                                    ) { idx, pair ->
                                        val chunk = pair.first
                                        val score = pair.second
                                        
                                        val imagesState = remember(chunk) { mutableStateOf<List<String>>(emptyList()) }
                                        LaunchedEffect(chunk) {
                                            imagesState.value = viewModel.getCrossReferencedImagesForChunk(chunk)
                                        }
                                        
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .background(SlateBackground, RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "[${idx + 1}] Source: ${chunk.docSource}",
                                                    color = TealAccent,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.weight(1.0f)
                                                )
                                                Text(
                                                    text = "Page ${chunk.pageIndex} | Sim: ${String.format("%.2f", score)}",
                                                    color = CoolGrayText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = chunk.chunkText,
                                                color = PolishDarkText,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                            
                                            val processedRagImages = remember(imagesState.value) { viewModel.processImagesForDisplay(imagesState.value) }
                                            if (processedRagImages.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Divider(color = PolishBorder, thickness = 0.5.dp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text("📷 Associated & Referred Figures:", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                Spacer(modifier = Modifier.height(6.dp))
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth().height(100.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    items(processedRagImages) { path ->
                                                        Card(
                                                            modifier = Modifier
                                                                .width(85.dp)
                                                                .fillMaxHeight()
                                                                .clickable {
                                                                    ragExpandedImagePath = path
                                                                    ragExpandedImagesList = imagesState.value
                                                                    ragSourceDoc = chunk.docSource
                                                                    ragSourcePage = chunk.pageIndex
                                                                    showRagExpandedDialog = true
                                                                },
                                                            colors = CardDefaults.cardColors(containerColor = SlateCard)
                                                        ) {
                                                            Box(modifier = Modifier.fillMaxSize()) {
                                                                coil.compose.AsyncImage(
                                                                    model = java.io.File(path),
                                                                    contentDescription = "guideline reference figure preview",
                                                                    modifier = Modifier.fillMaxSize(),
                                                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            items(solutions) { sol ->
                                SolutionDetailCard(sol, viewModel)
                            }

                            item {
                                val lastRawResponse by viewModel.lastRawLlmResponse.collectAsStateWithLifecycle()
                                val docName by viewModel.doctorName.collectAsStateWithLifecycle()
                                val docEmail by viewModel.doctorEmail.collectAsStateWithLifecycle()
                                val statsModel by viewModel.llmStatsModel.collectAsStateWithLifecycle()
                                val statsElapsed by viewModel.llmStatsElapsedTime.collectAsStateWithLifecycle()
                                val statsStage by viewModel.llmStatsStage.collectAsStateWithLifecycle()
                                val statsChunks by viewModel.llmStatsChunksReceived.collectAsStateWithLifecycle()
                                val statsChars by viewModel.llmStatsCharsReceived.collectAsStateWithLifecycle()
                                
                                var debugPasswordInput by remember { mutableStateOf("") }
                                val showSection = docName.equals("santa santa", ignoreCase = true) || 
                                                    docEmail.equals("santa santa", ignoreCase = true) ||
                                                    debugPasswordInput.equals("santa santa", ignoreCase = true)
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .background(SlateCard, RoundedCornerShape(12.dp))
                                        .border(1.dp, if (showSection) TealAccent else Color.Transparent, RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    if (!showSection) {
                                        Text(
                                            "Debug Console",
                                            color = CoolGrayText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(bottom = 4.dp)
                                        )
                                        OutlinedTextField(
                                            value = debugPasswordInput,
                                            onValueChange = { debugPasswordInput = it },
                                            placeholder = { Text("Enter bypass key to debug...", color = CoolGrayText.copy(alpha = 0.5f), fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 11.sp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = PolishBorder,
                                                focusedBorderColor = TealAccent
                                            ),
                                            singleLine = true
                                        )
                                    } else {
                                        var debugTab by remember { mutableStateOf("RAW") }
                                        val lastErrorLog by viewModel.lastDetailedErrorLog.collectAsStateWithLifecycle()
                                        val activeSessionNotes by viewModel.sessionNotes.collectAsStateWithLifecycle()
                                        
                                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                        val ctx = androidx.compose.ui.platform.LocalContext.current

                                        val currentContent = when(debugTab) {
                                            "RAW" -> lastRawResponse.ifBlank { "No active LLM response payload captured yet." }
                                            "ERRORS" -> (lastErrorLog ?: "No active exception, stack trace, or compiler error logged.").trim()
                                            "WARNINGS" -> activeSessionNotes.ifBlank { "No active clinical alerts, warnings, or anomalies reported." }
                                            else -> "--- INDEPENDENT SANTA CLINICAL METRICS ---\n" +
                                                     "Model Selection Name: ${viewModel.selectedModel.value}\n" +
                                                     "Model Actually Triggered: ${statsModel.ifBlank { "N/A" }}\n" +
                                                     "Inference Elapsed Latency: $statsElapsed\n" +
                                                     "Completed Stage: $statsStage\n" +
                                                     "Accumulated Chunks: $statsChunks\n" +
                                                     "Accumulated Bytes/Chars: $statsChars chars\n" +
                                                     "API Integration Mode: ${viewModel.apiMode.value}\n" +
                                                     "Active Application Screen: ${viewModel.currentScreen.value}\n" +
                                                     "Current Selected Patient: ${viewModel.selectedPatient.value?.fullName ?: "None"}"
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "🧑‍🎄 SANTA DEBUG DESK",
                                                color = TealAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            IconButton(
                                                onClick = { debugPasswordInput = "" },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Hide Debug",
                                                    tint = CoolGrayText,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        // Tab Selector Row
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf(
                                                "RAW" to "Raw JSON", 
                                                "ERRORS" to "Errors", 
                                                "WARNINGS" to "Warnings", 
                                                "INFO" to "Metadata"
                                            ).forEach { (key, label) ->
                                                val isSelected = debugTab == key
                                                val activeColor = when(key) {
                                                    "ERRORS" -> CoralHighlight
                                                    "WARNINGS" -> GoldWarning
                                                    else -> TealAccent
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .background(
                                                            if (isSelected) activeColor.copy(alpha = 0.15f) else SlateBackground, 
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .border(
                                                            1.dp, 
                                                            if (isSelected) activeColor else PolishBorder, 
                                                            RoundedCornerShape(6.dp)
                                                        )
                                                        .clickable { debugTab = key }
                                                        .padding(vertical = 8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = label, 
                                                        color = if (isSelected) activeColor else CoolGrayText, 
                                                        fontSize = 10.sp, 
                                                        fontWeight = FontWeight.Bold
                                                     )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(SlateBackground, RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Copiable View Window:",
                                                    color = CoolGrayText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                
                                                Button(
                                                    onClick = {
                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(currentContent))
                                                        android.widget.Toast.makeText(ctx, "Copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.height(28.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text("Copy Text", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(8.dp))
                                            
                                            androidx.compose.foundation.text.selection.SelectionContainer {
                                                Text(
                                                    text = currentContent,
                                                    color = PolishDarkText,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { viewModel.regenerateActiveSolutions() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
                                border = BorderStroke(1.dp, TealAccent)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Regenerate", tint = TealAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regenerate", color = TealAccent, fontSize = 12.sp)
                                }
                            }

                            Button(
                                onClick = {
                                    feeInputStr = ""
                                    showFeeDialog = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                            ) {
                                Text("End & Close Consultation", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBackExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showBackExitConfirmationDialog = false },
            title = { Text("Pause Consultation Session?", fontWeight = FontWeight.Bold, color = PolishDarkText) },
            text = { Text("Are you sure you want to temporarily leave this consult session? The session will remain ACTIVE and suspended in the background, allowing you to resume recording later. It will not be finalized or moved to history until you click 'End & Close Consultation'.", color = CoolGrayText) },
            confirmButton = {
                Button(
                    onClick = {
                        showBackExitConfirmationDialog = false
                        viewModel.navigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralHighlight)
                ) {
                    Text("Pause & Exit", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackExitConfirmationDialog = false }) {
                    Text("Stay in Session", color = TealAccent)
                }
            },
            containerColor = SlateCard
        )
    }

    if (showFeeDialog) {
        AlertDialog(
            onDismissRequest = { showFeeDialog = false },
            title = { Text("Record Consultation Fee", fontWeight = FontWeight.Bold, color = PolishDarkText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Please record the payment details or professional fee taken for this clinical consultation segment:", color = CoolGrayText, fontSize = 14.sp)
                    OutlinedTextField(
                        value = feeInputStr,
                        onValueChange = { feeInputStr = it },
                        label = { Text("Fee Amount ($ or local currency)") },
                        colors = polishTextFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val feeVal = feeInputStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                        viewModel.endSessionAndClose(feeVal)
                        showFeeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                ) {
                    Text("End & Save Fee", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeeDialog = false }) {
                    Text("Cancel", color = CoralHighlight)
                }
            },
            containerColor = SlateCard
        )
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Text(
                    "New Clinical Highlights Spoken",
                    color = PolishDarkText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "The on-device clinical analyzer identified the following new patient conditions, allergies, or medications that are not yet recorded:",
                        color = CoolGrayText,
                        fontSize = 13.sp
                    )
                    
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(detectedUpdates) { update ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateBackground.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(8.dp))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = when (update.type) {
                                                "chronic_conditions" -> "🏥 Chronic Disease"
                                                "allergies" -> "⚠️ Allergy Alert"
                                                "current_medications" -> "💊 Active Medication"
                                                else -> "📝 Care Hint"
                                            },
                                            color = TealAccent,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            update.value.uppercase(),
                                            color = PolishDarkText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        update.explanation,
                                        color = CoolGrayText,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.applyDetectedUpdates() },
                    colors = ButtonDefaults.buttonColors(containerColor = MintSuccess)
                ) {
                    Text("Append to Record", color = Color.White, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                    Text("Ignore Drafts", color = CoralHighlight, fontSize = 12.sp)
                }
            },
            containerColor = SlateCard
        )
    }

    if (showRagExpandedDialog && ragExpandedImagePath != null) {
        GuidelineImageGalleryDialog(
            initialImagePath = ragExpandedImagePath!!,
            allImages = ragExpandedImagesList,
            onDismissRequest = { showRagExpandedDialog = false },
            viewModel = viewModel,
            titleText = "Sourced Document: $ragSourceDoc"
        )
    }
}

// Draw Audio animation Wave on Canvas
@Composable
fun PulseWaveform() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_anim")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val path = Path()

        path.moveTo(0f, centerY)
        for (x in 0..width.toInt() step 5) {
            val relativeX = x / width
            // Shape the sine wave into a droplet/pulse bulge in the center
            val amplitudeFactor = sin(relativeX * Math.PI).toFloat()
            val y = centerY + amplitudeFactor * 45f * sin(relativeX * 10f * Math.PI + phase).toFloat()
            path.lineTo(x.toFloat(), y)
        }

        drawPath(
            path = path,
            color = TealAccent,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun BackgroundListenerWidget(
    isBgListening: Boolean,
    bgDraft: String,
    onToggle: () -> Unit,
    onSend: () -> Unit,
    onDraftChange: (String) -> Unit,
    isMicMuted: Boolean,
    onMuteToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TealAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCard.copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isBgListening) MintSuccess else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBgListening) "LIVE ROOM LISTENER ACTIVE" else "LIVE ROOM LISTENER INACTIVE",
                        color = if (isBgListening) MintSuccess else CoolGrayText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Mute/Unmute microphone button for live room listener
                    IconButton(
                        onClick = onMuteToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Unmute Microphone" else "Mute Microphone",
                            tint = if (isMicMuted) CoralHighlight else TealAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isBgListening) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle Room Listener",
                            tint = TealAccent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = bgDraft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 180.dp),
                textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 13.sp),
                label = { Text("Live Atmospheric Translation Notes / Input") },
                placeholder = { Text("Speak or type custom medicine context segment...", color = CoolGrayText.copy(alpha = 0.6f)) },
                colors = polishTextFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )

            if (bgDraft.isNotBlank() && !bgDraft.startsWith("Listening to live")) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onSend,
                    colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.align(Alignment.End).height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send Segment",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Analyze Segment", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun QuestionInputCard(question: InteractiveQuestion, onAnswerProvided: (String) -> Unit) {
    var rawTextAnswer by remember(question) { mutableStateOf(question.customAnswer ?: "") }
    var selectedChip by remember(question) { mutableStateOf<String?>(null) }

    LaunchedEffect(question) {
        if (question.customAnswer != null) {
            onAnswerProvided(question.customAnswer)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(question.field, color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(question.questionText, color = PolishDarkText, fontSize = 15.sp)

            Spacer(modifier = Modifier.height(12.dp))

            when (question.inputType) {
                "yes_no" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { selectedChip = "Yes"; onAnswerProvided("Yes") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedChip == "Yes") TealAccent else PolishNeutralBg,
                                contentColor = if (selectedChip == "Yes") Color.White else PolishDarkText
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Yes")
                        }
                        Button(
                            onClick = { selectedChip = "No"; onAnswerProvided("No") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedChip == "No") CoralHighlight else PolishNeutralBg,
                                contentColor = if (selectedChip == "No") Color.White else PolishDarkText
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("No")
                        }
                    }
                }
                "scale_1_10" -> {
                    var sliderVal by remember { mutableFloatStateOf(5f) }
                    Column {
                        Slider(
                            value = sliderVal,
                            onValueChange = {
                                sliderVal = it
                                onAnswerProvided(it.toInt().toString())
                            },
                            valueRange = 1f..10f,
                            steps = 8,
                            colors = SliderDefaults.colors(thumbColor = TealAccent, activeTrackColor = TealAccent)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mild (1)", color = CoolGrayText, fontSize = 11.sp)
                            Text("Value: ${sliderVal.toInt()}", color = PolishDarkText, fontSize = 13.sp)
                            Text("Severe (10)", color = CoolGrayText, fontSize = 11.sp)
                        }
                    }
                }
                "multiple_choice" -> {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(question.options) { option ->
                            val isSel = selectedChip == option
                            Box(
                                modifier = Modifier
                                    .background(if (isSel) TealAccent else PolishNeutralBg, RoundedCornerShape(16.dp))
                                    .clickable {
                                        selectedChip = option
                                        onAnswerProvided(option)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = option,
                                    color = if (isSel) Color.White else PolishDarkText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = rawTextAnswer,
                        onValueChange = {
                            rawTextAnswer = it
                            onAnswerProvided(it)
                        },
                        placeholder = { Text("Enter detail answers here...", color = CoolGrayText) },
                        colors = polishTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SolutionDetailCard(solution: ClinicalSolution, viewModel: MediAgentViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Check, contentDescription = "gnd", tint = MintSuccess, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(solution.title, color = PolishDarkText, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                }
                
                if (solution.categoryName.isNotBlank()) {
                    val pillColor = try {
                        Color(android.graphics.Color.parseColor(solution.categoryColor))
                    } catch (e: Exception) {
                        TealAccent
                    }
                    Text(
                        text = solution.categoryName,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(pillColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(solution.description, color = PolishDarkText, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = PolishBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Dietary & Lifestyle Items Row Blocks
            if (solution.dietaryPlan.isNotEmpty()) {
                Text("Dietary Management Model", color = TealAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                solution.dietaryPlan.forEach { d ->
                    Text("• $d", color = PolishDarkText, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (solution.lifestyleModifications.isNotEmpty()) {
                Text("Adjunct Lifestyle Modifications", color = TealAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                solution.lifestyleModifications.forEach { l ->
                    Text("• $l", color = PolishDarkText, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Grounded Citation Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info, 
                        contentDescription = "citation source info", 
                        tint = CoolGrayText, 
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Source Referred: ${solution.sourceDocument} (Page ${solution.sourcePage})",
                        color = CoolGrayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Checkup Schedule Timeline Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📅 Suggested Follow-Up Check Date: ",
                        color = TealAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = solution.followUpTimeline,
                        color = PolishDarkText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (solution.referencedImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = PolishBorder)
                Spacer(modifier = Modifier.height(10.dp))
                Text("📷 Attached Clinical Guideline Evidence:", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                
                var showExpandedImageDialog by remember { mutableStateOf(false) }
                var expandedImagePath by remember { mutableStateOf<String?>(null) }
                val processedSolutionImages = remember(solution.referencedImages) { viewModel.processImagesForDisplay(solution.referencedImages) }
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(processedSolutionImages) { path ->
                        Card(
                            modifier = Modifier
                                .width(120.dp)
                                .height(160.dp)
                                .clickable {
                                    expandedImagePath = path
                                    showExpandedImageDialog = true
                                },
                            colors = CardDefaults.cardColors(containerColor = SlateBackground)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                coil.compose.AsyncImage(
                                    model = java.io.File(path),
                                    contentDescription = "guideline attachment visual thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.Black.copy(alpha = 0.6f))
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Page ${solution.sourcePage}",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (showExpandedImageDialog && expandedImagePath != null) {
                    GuidelineImageGalleryDialog(
                        initialImagePath = expandedImagePath!!,
                        allImages = solution.referencedImages,
                        onDismissRequest = { showExpandedImageDialog = false },
                        viewModel = viewModel,
                        titleText = "Sourced Document: ${solution.sourceDocument}"
                    )
                }
            }

            var dismissedPrescriptionWarning by remember { mutableStateOf(false) }
            solution.drugInteractionsWarning?.let { warning ->
                if (!dismissedPrescriptionWarning) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                         modifier = Modifier.fillMaxWidth(),
                         colors = CardDefaults.cardColors(containerColor = GoldWarning.copy(alpha = 0.15f)),
                         border = BorderStroke(1.dp, GoldWarning)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(end = 40.dp, start = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = "drug interaction warn", tint = GoldWarning, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(warning, color = PolishDarkText, fontSize = 12.sp)
                            }
                            IconButton(
                                onClick = { dismissedPrescriptionWarning = true },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                                    .padding(6.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "dismiss", tint = CoolGrayText, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- SCREEN 6: DOCUMENTS MANAGER ----------------
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DocumentsManagerScreen(viewModel: MediAgentViewModel) {
    val documents by viewModel.documentsList.collectAsStateWithLifecycle()
    val docProgress by viewModel.docProgress.collectAsStateWithLifecycle()
    val removedCategories by viewModel.removedCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCategoryDialog by remember { mutableStateOf(false) }
    var onCategorySelected by remember { mutableStateOf<(() -> Unit)?>(null) }
    var chosenCategoryName by remember { mutableStateOf("General Guidelines") }
    var chosenCategoryColor by remember { mutableStateOf("#008080") }

    val existingCategories = remember(documents, removedCategories) {
        val list = documents.map { it.categoryName }.filter { it.isNotBlank() && it !in removedCategories }.distinct().toMutableList()
        val defaultCategories = listOf("General Guidelines", "Cardiology", "Pediatrics", "Oncology", "Internal Medicine")
        defaultCategories.forEach { devCat ->
            if (devCat !in list && devCat !in removedCategories) {
                list.add(devCat)
            }
        }
        list
    }

    val categoryColors = listOf(
        "#008080", // Teal Accent
        "#FF6B6B", // Coral Highlight
        "#3498DB", // Royal Blue
        "#2ECC71", // Emerald Green
        "#9B59B6", // Amethyst Violet
        "#F1C40F", // Amber Gold
        "#E67E22", // Pumpkin Orange
        "#1ABC9C", // Turquoise Teal
        "#34495E"  // Slate Blue
    )

    if (showCategoryDialog) {
        var tempName by remember { mutableStateOf(chosenCategoryName) }
        var tempColor by remember { mutableStateOf(chosenCategoryColor) }
        var isNewCategoryMode by remember { mutableStateOf(false) }

        var categoryToDelete by remember { mutableStateOf<String?>(null) }
        var showDeleteConfirm by remember { mutableStateOf(false) }

        if (showDeleteConfirm && categoryToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                containerColor = SlateCard,
                title = {
                    Text(
                        text = "Delete Category Tag?",
                        color = PolishDarkText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to remove the tag \"$categoryToDelete\"? This will clear this category tag from any associated guideline documents, and hide this category preset.",
                        color = CoolGrayText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.removeCategoryTag(categoryToDelete!!)
                            if (tempName == categoryToDelete) {
                                tempName = "General Guidelines"
                            }
                            showDeleteConfirm = false
                            categoryToDelete = null
                        }
                    ) {
                        Text("Remove Tag", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text("Cancel", color = CoolGrayText)
                    }
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            containerColor = SlateCard,
            tonalElevation = 6.dp,
            title = {
                Text(
                    text = "Organize Guidelines",
                    color = PolishDarkText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "To help organize clinical books and RAG solutions, choose or create a user-defined category with a custom tag color. (Long-press any tag option below to delete it.)",
                        color = CoolGrayText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Category Type",
                            color = PolishDarkText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isNewCategoryMode) "Custom Name" else "Preset Option",
                                color = TealAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(TealAccent.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(vertical = 2.dp, horizontal = 6.dp)
                                    .clickable { isNewCategoryMode = !isNewCategoryMode }
                            )
                        }
                    }

                    if (!isNewCategoryMode) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(existingCategories) { catName ->
                                val isSelected = tempName == catName
                                Card(
                                    modifier = Modifier
                                        .combinedClickable(
                                            onClick = { tempName = catName },
                                            onLongClick = {
                                                categoryToDelete = catName
                                                showDeleteConfirm = true
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) TealAccent else PolishBorder,
                                            RoundedCornerShape(8.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) TealAccent.copy(alpha = 0.15f) else SlateBackground
                                    )
                                ) {
                                    Text(
                                        text = catName,
                                        color = if (isSelected) TealAccent else PolishDarkText,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = tempName,
                            onValueChange = { tempName = it },
                            placeholder = { Text("Enter Category Name...", color = CoolGrayText.copy(alpha = 0.5f), fontSize = 13.sp) },
                            singleLine = true,
                            colors = polishTextFieldColors(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = PolishDarkText, fontSize = 13.sp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        text = "Assign Tag Color",
                        color = PolishDarkText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categoryColors) { colorHex ->
                            val isSelected = tempColor == colorHex
                            val composeColor = try {
                                Color(android.graphics.Color.parseColor(colorHex))
                            } catch (e: Exception) {
                                TealAccent
                            }

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(composeColor, CircleShape)
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { tempColor = colorHex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, PolishBorder, RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = SlateBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sampleColor = try {
                                Color(android.graphics.Color.parseColor(tempColor))
                            } catch (e: Exception) {
                                TealAccent
                            }
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(sampleColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Preview: ${tempName.ifBlank { "General" }}",
                                color = PolishDarkText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalName = tempName.trim().ifBlank { "General" }
                        chosenCategoryName = finalName
                        chosenCategoryColor = tempColor
                        showCategoryDialog = false
                        onCategorySelected?.invoke()
                    }
                ) {
                    Text("Select File & Upload", color = TealAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Cancel", color = CoolGrayText)
                }
            }
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            var fileName = "Uploaded_Document"
            var fileContent = ""
            var imageBase64: String? = null
            var filePath: String? = null
            var fileType = "doc"
            try {
                val cursor = context.contentResolver.query(it, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                    cursor.close()
                }
                
                val type = context.contentResolver.getType(it) ?: ""
                val extension = fileName.lowercase().substringAfterLast('.', "")
                val isImage = extension in listOf("png", "jpg", "jpeg", "webp", "bmp") || type.startsWith("image/")
                val isPdfRaw = extension == "pdf" || type == "application/pdf"

                val tempFile = java.io.File(context.cacheDir, "upload_${System.currentTimeMillis()}.$extension")
                var writeSuccess = false
                try {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    writeSuccess = tempFile.exists() && tempFile.length() > 0
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (writeSuccess) {
                    val isStartWithPdf = if (tempFile.length() >= 4) {
                        try {
                            java.io.FileInputStream(tempFile).use { fInput ->
                                val header = ByteArray(4)
                                fInput.read(header)
                                header[0] == 0x25.toByte() && // '%'
                                header[1] == 0x50.toByte() && // 'P'
                                header[2] == 0x44.toByte() && // 'D'
                                header[3] == 0x46.toByte()    // 'F'
                            }
                        } catch (e: Exception) {
                            false
                        }
                    } else false

                    if (isImage) {
                        fileType = "image"
                        filePath = tempFile.absolutePath
                    } else if (isPdfRaw || isStartWithPdf) {
                        fileType = "pdf"
                        filePath = tempFile.absolutePath
                        fileContent = ""
                    } else if (type.contains("text") || extension in listOf("txt", "json", "csv", "md", "xml", "html")) {
                        fileContent = try {
                            tempFile.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            ""
                        }
                    } else {
                        fileContent = try {
                            tempFile.readText(Charsets.UTF_8)
                        } catch (e: Exception) {
                            "[Binary Document: $fileName - ${tempFile.length()} bytes]"
                        }
                    }
                } else {
                    fileContent = "Could not initialize directory cache stream for uploading."
                }
            } catch (e: Exception) {
                fileContent = "Failed to parse content: ${e.message}"
            }
            viewModel.addDocument(fileName, fileType, (1..5).random(), fileContent, imageBase64, filePath, chosenCategoryName, chosenCategoryColor)
        }
    }

    LaunchedEffect(filePickerLauncher) {
        onCategorySelected = {
            filePickerLauncher.launch(arrayOf("*/*"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clinic Guideline Books", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealAccent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    showCategoryDialog = true
                },
                containerColor = TealAccent,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Upload Guideline")
            }
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text("Active Vector Database Resources", color = PolishDarkText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Uploaded clinical standard pathways processed for targeted RAG search context.", color = CoolGrayText, fontSize = 13.sp)

            Spacer(modifier = Modifier.height(10.dp))

            var showStabilityWarning by remember { mutableStateOf(true) }
            if (showStabilityWarning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CoralHighlight.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CoralHighlight.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(end = 36.dp, start = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Stability Warning",
                                tint = GoldWarning,
                                modifier = Modifier.size(20.dp).padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Important Note: Document vectorization requires your device to maintain a stable internet connection for generating embeddings. For optimal results, please stay on this screen. Under temporary connection drop-offs or rate-limiting, progress will automatically pause. You can simply click the card to expand options and tap Resume once stability is restored.",
                                color = CoolGrayText,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        IconButton(
                            onClick = { showStabilityWarning = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp)
                                .padding(4.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "dismiss", tint = CoolGrayText, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (documents.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No processed document guidelines available.", color = CoolGrayText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = documents,
                        key = { it.id }
                    ) { doc ->
                        DocumentRowItem(
                            doc = doc,
                            onDelete = { viewModel.deleteDocument(doc) },
                            onTogglePriority = { viewModel.toggleDocumentPriority(doc) },
                            progressState = docProgress[doc.id],
                            onPause = { viewModel.pauseIndexing(doc.id) },
                            onResume = { viewModel.resumeIndexing(doc) },
                            onCancel = { viewModel.cancelIndexing(doc.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DocumentRowItem(
    doc: DocItem, 
    onDelete: () -> Unit, 
    onTogglePriority: () -> Unit,
    progressState: Pair<Float, Int>? = null,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .border(1.dp, if (isExpanded) TealAccent else PolishBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info, 
                        contentDescription = "pdf", 
                        tint = CoralHighlight, 
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = doc.fileSource, 
                        color = PolishDarkText, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!doc.categoryName.isNullOrBlank()) {
                        val pillColor = try {
                            Color(android.graphics.Color.parseColor(doc.categoryColor))
                        } catch (e: Exception) {
                            TealAccent
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = doc.categoryName,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(pillColor, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    
                    // Priority Indicator Icon
                    IconButton(
                        onClick = onTogglePriority,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (doc.isPriority) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Toggle Priority Search Status",
                            tint = if (doc.isPriority) Color(0xFFFFC107) else CoolGrayText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                if (isExpanded) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "delete", tint = CoralHighlight)
                    }
                }
            }

            if (doc.status != "PROCESSING") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(doc.summary, color = CoolGrayText, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (doc.status == "PROCESSING") {
                val progress = progressState?.first ?: 0.05f
                val remainingSec = progressState?.second ?: 12
                val isPaused = com.example.service.RagProgressManager.docPaused.collectAsStateWithLifecycle().value[doc.id] == true
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isPaused) "Paused: ${(progress * 100).toInt()}%" else "Processing: ${(progress * 100).toInt()}%",
                            color = if (isPaused) GoldWarning else TealAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (remainingSec > 0) "Est: ${com.example.service.formatDuration(remainingSec)}" else "Wrapping up...",
                                color = CoolGrayText,
                                fontSize = 11.sp
                            )
                            if (isExpanded) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (isPaused) {
                                            onResume()
                                        } else {
                                            onPause()
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (isPaused) "Resume" else "Pause",
                                        tint = TealAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        onCancel()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Indexing",
                                        tint = CoralHighlight,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (isPaused) GoldWarning else TealAccent,
                        trackColor = PolishBorder
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(TealAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Pages: ${doc.pageCount}", color = TealAccent, fontSize = 12.sp)
                }
                val statusColor = when (doc.status) {
                    "PROCESSING" -> GoldWarning
                    "ERROR" -> CoralHighlight
                    else -> MintSuccess
                }
                Box(
                    modifier = Modifier
                        .background(
                            statusColor.copy(alpha = 0.15f), 
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = doc.status, 
                        color = statusColor, 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Bold
                    )
                }
                if (doc.isPriority) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFC107).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("HIGH PRIORITY RAG", color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = if (isExpanded) "Tap to lock" else "Tap for ops",
                    color = CoolGrayText.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}

@Composable
fun AddDocumentDialog(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var rawName by remember { mutableStateOf("") }
    var rawPageCount by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload Medical Guideline Book", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rawName,
                    onValueChange = { rawName = it },
                    label = { Text("File Name (e.g., Cardiology_DASH_2026.pdf)") },
                    colors = polishTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rawPageCount,
                    onValueChange = { rawPageCount = it },
                    label = { Text("Page Volume") },
                    colors = polishTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (rawName.isNotBlank()) {
                        onAdd(rawName, rawPageCount.toIntOrNull() ?: 5)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Text("Process Guideline", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CoralHighlight)
            }
        },
        containerColor = SlateCard
    )
}

// ---------------- SCREEN 7: SYSTEM SETTINGS ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MediAgentViewModel) {
    val context = LocalContext.current
    val currentMode by viewModel.apiMode.collectAsStateWithLifecycle()
    val serverUrl by viewModel.remoteServerUrl.collectAsStateWithLifecycle()
    val provider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val model by viewModel.selectedModel.collectAsStateWithLifecycle()
    val key by viewModel.llmApiKey.collectAsStateWithLifecycle()
    val llmApiLink by viewModel.llmApiLink.collectAsStateWithLifecycle()

    val embedProvider by viewModel.embeddingProvider.collectAsStateWithLifecycle()
    val embedModel by viewModel.embeddingModel.collectAsStateWithLifecycle()
    val embedKey by viewModel.embeddingApiKey.collectAsStateWithLifecycle()
    val embeddingApiLink by viewModel.embeddingApiLink.collectAsStateWithLifecycle()
    val useAiBrainFallback by viewModel.useAiBrainFallback.collectAsStateWithLifecycle()
    val ragRetrieveLimit by viewModel.ragRetrieveLimit.collectAsStateWithLifecycle()
    val ragChunkSize by viewModel.ragChunkSize.collectAsStateWithLifecycle()

    val testLlmStatus by viewModel.testLlmStatus.collectAsStateWithLifecycle()
    val testEmbedStatus by viewModel.testEmbedStatus.collectAsStateWithLifecycle()

    val fallbackProfiles by viewModel.fallbackProfiles.collectAsStateWithLifecycle()
    var showAddFallbackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.clearTestStatuses()
    }

    var activeMode by remember { mutableStateOf(currentMode) }
    var activeUrl by remember { mutableStateOf(serverUrl) }
    var activeProvider by remember { mutableStateOf(provider) }
    var activeModel by remember { mutableStateOf(model) }
    var activeKey by remember { mutableStateOf(key) }
    var activeLlmApiLink by remember { mutableStateOf(llmApiLink) }

    var activeEmbedProvider by remember { mutableStateOf(embedProvider) }
    var activeEmbedModel by remember { mutableStateOf(embedModel) }
    var activeEmbedKey by remember { mutableStateOf(embedKey) }
    var activeEmbeddingApiLink by remember { mutableStateOf(embeddingApiLink) }
    var activeUseAiBrainFallback by remember { mutableStateOf(useAiBrainFallback) }
    var activeRagRetrieveLimit by remember { mutableStateOf(ragRetrieveLimit.toString()) }
    var activeRagChunkSize by remember { mutableStateOf(ragChunkSize.toString()) }

    // Synchronize with external changes (e.g. backup import)
    LaunchedEffect(useAiBrainFallback) {
        activeUseAiBrainFallback = useAiBrainFallback
    }
    LaunchedEffect(ragRetrieveLimit) {
        activeRagRetrieveLimit = ragRetrieveLimit.toString()
    }
    LaunchedEffect(ragChunkSize) {
        activeRagChunkSize = ragChunkSize.toString()
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val text = stream.bufferedReader().readText()
                    viewModel.importAllData(context, text)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading backup: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediAgent Settings Panel", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealAccent)
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Provider Intelligence Models (LLM)", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = activeProvider,
                onValueChange = { activeProvider = it },
                label = { Text("LLM Hub Provider (e.g. Google)") },
                colors = polishTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = activeLlmApiLink,
                onValueChange = { activeLlmApiLink = it },
                label = { Text("LLM API Custom Base URL / Link (e.g. https://generativelanguage.googleapis.com)") },
                colors = polishTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = activeModel,
                onValueChange = { activeModel = it },
                label = { Text("Intelligence Model Specifier Name") },
                colors = polishTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = activeKey,
                onValueChange = { activeKey = it },
                label = { Text("Provider Authentication Key") },
                colors = polishTextFieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Online Embedding Model Settings (RAG)", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = activeEmbedProvider,
                onValueChange = { activeEmbedProvider = it },
                label = { Text("Embedding Provider (e.g. Google)") },
                colors = polishTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = activeEmbeddingApiLink,
                onValueChange = { activeEmbeddingApiLink = it },
                label = { Text("Embedding Vector API Custom Base URL / Link (e.g. https://generativelanguage.googleapis.com)") },
                colors = polishTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = activeEmbedModel,
                onValueChange = { activeEmbedModel = it },
                label = { Text("Embedding Vector Model Name") },
                colors = polishTextFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = activeEmbedKey,
                onValueChange = { activeEmbedKey = it },
                label = { Text("Embedding API Key") },
                colors = polishTextFieldColors(),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            // --- Dialog for Adding Fallback LLM profile ---
            if (showAddFallbackDialog) {
                var profileName by remember { mutableStateOf("") }
                var profileModel by remember { mutableStateOf("") }
                var profileApiKey by remember { mutableStateOf("") }
                var profileProvider by remember { mutableStateOf("Google") }
                var profileApiLink by remember { mutableStateOf("https://generativelanguage.googleapis.com") }
                var isFallbackModelOnly by remember { mutableStateOf(true) } // default to the simpler Fallback Model option

                AlertDialog(
                    onDismissRequest = { showAddFallbackDialog = false },
                    title = {
                        Text(
                            text = "Add Fallback Connection",
                            color = PolishDarkText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Choose fallback mode. If primary clinician model fails, MediAgent will cascade automatically down this fallback queue to guarantee seamless diagnotic flow.",
                                color = CoolGrayText,
                                fontSize = 11.sp
                            )

                            // Clean visual toggle cards instead of complex switches
                            Text(
                                text = "Select Fallback Type:",
                                color = PolishDarkText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (isFallbackModelOnly) TealAccent.copy(alpha = 0.12f) else PolishNeutralBg, RoundedCornerShape(10.dp))
                                        .border(1.5.dp, if (isFallbackModelOnly) TealAccent else PolishBorder, RoundedCornerShape(10.dp))
                                        .clickable { isFallbackModelOnly = true }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Fallback Model", color = if (isFallbackModelOnly) TealAccent else PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Same API Key & server,\ndifferent model name", color = CoolGrayText, fontSize = 9.sp, textAlign = TextAlign.Center)
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(if (!isFallbackModelOnly) TealAccent.copy(alpha = 0.12f) else PolishNeutralBg, RoundedCornerShape(10.dp))
                                        .border(1.5.dp, if (!isFallbackModelOnly) TealAccent else PolishBorder, RoundedCornerShape(10.dp))
                                        .clickable { isFallbackModelOnly = false }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("New Connection Spec", color = if (!isFallbackModelOnly) TealAccent else PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Different API Key, server,\nendpoint & model name", color = CoolGrayText, fontSize = 9.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedTextField(
                                value = profileName,
                                onValueChange = { profileName = it },
                                label = { Text("Fallback Name (e.g., GPT-4o Fallback)") },
                                colors = polishTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = profileModel,
                                onValueChange = { profileModel = it },
                                label = { Text("Model Name (e.g., gemini-1.5-pro)") },
                                colors = polishTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (!isFallbackModelOnly) {
                                Divider(color = PolishBorder, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                
                                Text(
                                    text = "Custom Provider Server Details",
                                    color = PolishDarkText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )

                                OutlinedTextField(
                                    value = profileProvider,
                                    onValueChange = { profileProvider = it },
                                    label = { Text("Provider (e.g., OpenAI, Google, Custom)") },
                                    colors = polishTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = profileApiKey,
                                    onValueChange = { profileApiKey = it },
                                    label = { Text("Dedicated Fallback API Key") },
                                    colors = polishTextFieldColors(),
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = profileApiLink,
                                    onValueChange = { profileApiLink = it },
                                    label = { Text("Base Server Link (API Endpoint URL)") },
                                    colors = polishTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (profileName.isNotBlank() && profileModel.isNotBlank()) {
                                    viewModel.addFallbackProfile(
                                        com.example.ui.viewmodel.FallbackLlmProfile(
                                            id = java.util.UUID.randomUUID().toString(),
                                            name = profileName,
                                            model = profileModel,
                                            apiKey = profileApiKey,
                                            provider = profileProvider,
                                            llmApiLink = profileApiLink,
                                            isFallbackModelOnly = isFallbackModelOnly
                                        )
                                    )
                                    showAddFallbackDialog = false
                                    Toast.makeText(context, "Added $profileName successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please fill in Name and Model fields!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
                        ) {
                            Text("Save Target", color = Color.White)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddFallbackDialog = false }) {
                            Text("Dismiss", color = CoralHighlight)
                        }
                    },
                    containerColor = SlateCard
                )
            }

            Text("Secondary Fallback API Configurations", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Add fallback models, custom API keys, and endpoint servers. If the primary clinician model triggers rate limits or network issues, MediAgent automatically recovers diagnostic streams by failing over through these configured connection targets sequentially.",
                        color = CoolGrayText,
                        fontSize = 12.sp
                    )
                    
                    if (fallbackProfiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PolishNeutralBg, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No fallback connection specs registered yet. Clinical failover is disabled.",
                                color = CoolGrayText,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            fallbackProfiles.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PolishNeutralBg, RoundedCornerShape(8.dp))
                                        .border(0.5.dp, PolishBorder, RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (item.isFallbackModelOnly) {
                                                "Fallback Model Only -> Model: ${item.model}"
                                            } else {
                                                "Provider: ${item.provider} | Model: ${item.model}\nURL: ${item.llmApiLink}"
                                            },
                                            color = CoolGrayText,
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            viewModel.removeFallbackProfile(item)
                                            Toast.makeText(context, "Removed fallback spec: ${item.name}", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Fallback Spec", tint = CoralHighlight)
                                    }
                                }
                            }
                        }
                    }
                    
                    Button(
                        onClick = { showAddFallbackDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PolishHighlight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Fallback Profile Spec", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Text("Guideline Search Threshold & Limit", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Configure the maximum number of guideline document chunks to query and feed into your clinical AI context. Increasing this delivers broader referencing, while decreasing speeds up reasoning.",
                        color = CoolGrayText,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = activeRagRetrieveLimit,
                        onValueChange = { nv ->
                            if (nv.isEmpty() || nv.all { it.isDigit() }) {
                                activeRagRetrieveLimit = nv
                            }
                        },
                        label = { Text("Active Chunk Retrieval Limit (Default: 20)") },
                        colors = polishTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text("Guideline Chunks Splitting Size", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Configure the length (in characters) of standard document segments during text parsing. A smaller chunk size isolates precise paragraphs, while a larger chunk size preserves complete paragraph context.",
                        color = CoolGrayText,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = activeRagChunkSize,
                        onValueChange = { nv ->
                            if (nv.isEmpty() || nv.all { it.isDigit() }) {
                                activeRagChunkSize = nv
                            }
                        },
                        label = { Text("Active Chunk Size (Default: 1000)") },
                        colors = polishTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text("Data Backup & Restore Administration", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Secure clinical records syncing and local schema evolutionary migration guides.", color = CoolGrayText, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportAllData(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export Backup", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export", color = Color.White, fontSize = 12.sp)
                        }
                        Button(
                            onClick = { importLauncher.launch("*/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MintSuccess),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Import Backup", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Backup", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }

            Text("Connection Verification", color = PolishDarkText, fontWeight = FontWeight.Bold, fontSize = 16.sp)

            Card(
                modifier = Modifier.fillMaxWidth().border(1.dp, PolishBorder, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = SlateCard)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Verify model connectivity, endpoints, and credentials prior to committing workspace configurations.",
                        color = CoolGrayText,
                        fontSize = 12.sp
                    )
                    
                    if (testLlmStatus != null || testEmbedStatus != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // LLM Connection Status Indicator
                            testLlmStatus?.let { status ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(
                                        when {
                                            status == "TESTING" -> GoldWarning.copy(alpha = 0.08f)
                                            status == "SUCCESS" -> MintSuccess.copy(alpha = 0.08f)
                                            else -> CoralHighlight.copy(alpha = 0.08f)
                                        },
                                        RoundedCornerShape(8.dp)
                                    ).border(
                                        1.dp,
                                        when {
                                            status == "TESTING" -> GoldWarning.copy(alpha = 0.3f)
                                            status == "SUCCESS" -> MintSuccess.copy(alpha = 0.3f)
                                            else -> CoralHighlight.copy(alpha = 0.3f)
                                        },
                                        RoundedCornerShape(8.dp)
                                    ).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "1. LLM Core Endpoint",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PolishDarkText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when {
                                                status == "TESTING" -> "Pinging LLM Provider... (Please wait)"
                                                status == "SUCCESS" -> "Success: Core LLM connection is fully operational!"
                                                else -> status // displays friendly error message
                                            },
                                            fontSize = 11.sp,
                                            color = when {
                                                status == "TESTING" -> GoldWarning
                                                status == "SUCCESS" -> MintSuccess
                                                else -> CoralHighlight
                                            }
                                        )
                                    }
                                    if (status == "TESTING") {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GoldWarning, strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = if (status == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (status == "SUCCESS") MintSuccess else CoralHighlight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Embedding Connection Status Indicator
                            testEmbedStatus?.let { status ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(
                                        when {
                                            status == "TESTING" -> GoldWarning.copy(alpha = 0.08f)
                                            status == "SUCCESS" -> MintSuccess.copy(alpha = 0.08f)
                                            else -> CoralHighlight.copy(alpha = 0.08f)
                                        },
                                        RoundedCornerShape(8.dp)
                                    ).border(
                                        1.dp,
                                        when {
                                            status == "TESTING" -> GoldWarning.copy(alpha = 0.3f)
                                            status == "SUCCESS" -> MintSuccess.copy(alpha = 0.3f)
                                            else -> CoralHighlight.copy(alpha = 0.3f)
                                        },
                                        RoundedCornerShape(8.dp)
                                    ).padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "2. Embedding Vectorizer Endpoint",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PolishDarkText
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = when {
                                                status == "TESTING" -> "Verifying Embeddings connection... (Please wait)"
                                                status == "SUCCESS" -> "Success: RAG Vectorization is fully operational!"
                                                else -> status // displays friendly error message
                                            },
                                            fontSize = 11.sp,
                                            color = when {
                                                status == "TESTING" -> GoldWarning
                                                status == "SUCCESS" -> MintSuccess
                                                else -> CoralHighlight
                                            }
                                        )
                                    }
                                    if (status == "TESTING") {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GoldWarning, strokeWidth = 2.dp)
                                    } else {
                                        Icon(
                                            imageVector = if (status == "SUCCESS") Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (status == "SUCCESS") MintSuccess else CoralHighlight,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.testApiConfigurations(
                                llmUrl = activeLlmApiLink,
                                llmModel = activeModel,
                                llmKey = activeKey,
                                embedUrl = activeEmbeddingApiLink,
                                embedModel = activeEmbedModel,
                                embedKey = activeEmbedKey
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PolishHighlight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = testLlmStatus != "TESTING" && testEmbedStatus != "TESTING"
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Test API Button", tint = TealAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Connections Now", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.updateSettings(
                        activeMode, activeUrl, 
                        activeProvider, activeModel, activeKey,
                        activeEmbedProvider, activeEmbedModel, activeEmbedKey,
                        activeLlmApiLink, activeEmbeddingApiLink,
                        false,
                        activeRagRetrieveLimit.toIntOrNull() ?: 20,
                        activeRagChunkSize.toIntOrNull() ?: 1000
                    )
                    viewModel.navigateBack()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Apply & Commit Settings Spec", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    viewModel.navigateTo(AppScreen.LOGIN)
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, CoralHighlight),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CoralHighlight),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Log out", tint = CoralHighlight)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out Doctor", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ZoomableAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }
    Box(
        modifier = modifier
            .clipToBounds()
            .transformable(state = state)
    ) {
        coil.compose.AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x * scale,
                    translationY = offset.y * scale
                ),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }
}

@Composable
fun GuidelineImageGalleryDialog(
    initialImagePath: String,
    allImages: List<String>,
    onDismissRequest: () -> Unit,
    viewModel: MediAgentViewModel,
    titleText: String = "Clinical Guideline Evidence"
) {
    val processedImages = remember(allImages) { viewModel.processImagesForDisplay(allImages) }
    
    var currentIndex by remember(initialImagePath, processedImages) {
        val idx = processedImages.indexOf(initialImagePath)
        mutableStateOf(if (idx != -1) idx else 0)
    }
    
    val currentPath = processedImages.getOrNull(currentIndex) ?: initialImagePath
    var showFullPageMode by remember(currentPath) { mutableStateOf(false) }
    
    val uiPath = remember(currentPath, showFullPageMode) {
        if (showFullPageMode) {
            val parsed = viewModel.parseImagePath(currentPath)
            if (parsed != null) {
                val fullFile = java.io.File(java.io.File(currentPath).parentFile, "pdf_${parsed.docId}_page_${parsed.pageIndex}_full.jpg")
                if (fullFile.exists()) {
                    fullFile.absolutePath
                } else {
                    currentPath
                }
            } else {
                currentPath
            }
        } else {
            currentPath
        }
    }
    
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(12.dp)
                .border(1.dp, PolishBorder, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = titleText,
                            color = PolishDarkText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val parsed = viewModel.parseImagePath(currentPath)
                        if (parsed != null) {
                            Text(
                                text = "Document Reference: ${parsed.docId} | Page: ${parsed.pageIndex}",
                                color = CoolGrayText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Referenced Guideline Image Attachment",
                                color = CoolGrayText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "close",
                            tint = CoolGrayText,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                val parsed = viewModel.parseImagePath(currentPath)
                val fullPageAvailable = remember(currentPath) {
                    if (parsed != null) {
                        java.io.File(java.io.File(currentPath).parentFile, "pdf_${parsed.docId}_page_${parsed.pageIndex}_full.jpg").exists()
                    } else false
                }
                
                if (fullPageAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showFullPageMode = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!showFullPageMode) TealAccent else PolishNeutralBg,
                                contentColor = if (!showFullPageMode) Color.White else CoolGrayText
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Cropped Figure", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showFullPageMode = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (showFullPageMode) TealAccent else PolishNeutralBg,
                                contentColor = if (showFullPageMode) Color.White else CoolGrayText
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Show Full Page", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableAsyncImage(
                        model = java.io.File(uiPath),
                        contentDescription = "guideline reference preview",
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    if (currentIndex > 0) {
                        IconButton(
                            onClick = { 
                                currentIndex--
                                showFullPageMode = false
                            },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 12.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "previous image",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    if (currentIndex < processedImages.size - 1) {
                        IconButton(
                            onClick = { 
                                currentIndex++
                                showFullPageMode = false
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "next image",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Related Pages & Figures (${processedImages.size})",
                    color = CoolGrayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    processedImages.forEachIndexed { idx, path ->
                        val isSelected = idx == currentIndex
                        Card(
                            modifier = Modifier
                                .width(70.dp)
                                .fillMaxHeight()
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) TealAccent else PolishBorder.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { 
                                    currentIndex = idx
                                    showFullPageMode = false
                                },
                            colors = CardDefaults.cardColors(containerColor = PolishNeutralBg)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                coil.compose.AsyncImage(
                                    model = java.io.File(path),
                                    contentDescription = "guideline preview thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
