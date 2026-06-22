package com.example.safewatch.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safewatch.ui.theme.*

@Composable
fun ProfileSetupScreen(onSetupComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(1) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Step Indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StepIndicatorItem(1, "Child", currentStep >= 1)
            Box(modifier = Modifier.weight(1f).height(2.dp).background(if (currentStep > 1) PrimaryBlue else CardBackground))
            StepIndicatorItem(2, "Contacts", currentStep >= 2)
            Box(modifier = Modifier.weight(1f).height(2.dp).background(if (currentStep > 2) PrimaryBlue else CardBackground))
            StepIndicatorItem(3, "Device", currentStep >= 3)
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        AnimatedContent(targetState = currentStep, label = "stepChange") { step ->
            when (step) {
                1 -> ChildInfoStep(onNext = { currentStep = 2 })
                2 -> EmergencyContactsStep(onNext = { currentStep = 3 }, onBack = { currentStep = 1 })
                3 -> DevicePairingStep(onComplete = onSetupComplete, onBack = { currentStep = 2 })
            }
        }
    }
}

@Composable
fun StepIndicatorItem(step: Int, label: String, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isActive) PrimaryBlue else CardBackground)
                .border(2.dp, if (isActive) PrimaryBlue else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(step.toString(), color = if (isActive) Color.White else TextSecondary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (isActive) Color.White else TextSecondary)
    }
}

@Composable
fun ChildInfoStep(onNext: () -> Unit) {
    Column {
        Text("Child's Information", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(CardBackground)
                .align(Alignment.CenterHorizontally)
                .clickable { },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(32.dp))
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        SetupTextField(label = "Child's Name", placeholder = "Enter name")
        Spacer(modifier = Modifier.height(16.dp))
        SetupTextField(label = "Age", placeholder = "Enter age")
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Next Step")
        }
    }
}

@Composable
fun EmergencyContactsStep(onNext: () -> Unit, onBack: () -> Unit) {
    Column {
        Text("Emergency Contacts", style = MaterialTheme.typography.displaySmall, color = Color.White)
        Text("Add up to 3 guardians", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(32.dp))
        
        SetupTextField(label = "Mom's Phone", placeholder = "+91 XXXXXXXXXX")
        Spacer(modifier = Modifier.height(16.dp))
        SetupTextField(label = "Dad's Phone", placeholder = "+91 XXXXXXXXXX")
        Spacer(modifier = Modifier.height(16.dp))
        SetupTextField(label = "Guardian's Phone", placeholder = "+91 XXXXXXXXXX")
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBackground)
            ) {
                Text("Back", color = Color.White)
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(2f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Next Step")
            }
        }
    }
}

@Composable
fun DevicePairingStep(onComplete: () -> Unit, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Pair Device", style = MaterialTheme.typography.displaySmall, color = Color.White, modifier = Modifier.align(Alignment.Start))
        Spacer(modifier = Modifier.height(32.dp))
        
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(Color.White, RoundedCornerShape(24.dp))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.QrCode2, contentDescription = null, tint = Color.Black, modifier = Modifier.fillMaxSize())
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text("Device ID: SW-9823-XYZ", color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Insert SIM into wristband and power on. Scan the QR code or enter the ID manually.",
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBackground)
            ) {
                Text("Back", color = Color.White)
            }
            Button(
                onClick = onComplete,
                modifier = Modifier.weight(2f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Finish Setup")
            }
        }
    }
}

@Composable
fun SetupTextField(label: String, placeholder: String) {
    Column {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = TextSecondary) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = CardBackground,
                focusedContainerColor = CardBackground,
                unfocusedContainerColor = CardBackground,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
    }
}
