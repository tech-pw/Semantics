package io.github.techpw.sementics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import live.pw.compose.semantics.R

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val composeView = findViewById<ComposeView>(R.id.compose_view)

        composeView.setContent {
            App()
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    Column(modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "hello world 1")
        MyText(text = "hello world 2")
        Text(text = "hello world 4", modifier = Modifier.padding(16.dp))

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

@Composable
fun MyText(modifier: Modifier = Modifier.padding(2.dp), text: String) {
    Text(modifier = modifier, text = text)
}