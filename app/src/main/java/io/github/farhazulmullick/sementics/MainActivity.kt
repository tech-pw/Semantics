package io.github.farhazulmullick.sementics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "hello world 1", modifier = Modifier.padding(16.dp))
                Text(text = "hello world 2", modifier = Modifier.padding(16.dp))
                Text(text = "hello world 3", modifier = Modifier.padding(16.dp))

                Button(modifier = Modifier , onClick = {
                    println("button clicked")
                }){
                    Text(text = "Button A", modifier = Modifier)
                }

                Button(modifier = Modifier , onClick = {
                    println("button clicked")
                }){
                    Text(text = "Button B", modifier = Modifier)
                }

                Button(modifier = Modifier , onClick = {
                    println("button clicked")
                }){
                    Text(text = "Button C", modifier = Modifier)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Green)
                    .height(56.dp)
                ) {}

                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Green)
                    .height(56.dp)
                ) {}

                Widget()
            }
        }
    }
}