package io.github.techpw.sementics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App2() {
    Column(modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally) {

        Text(text = "hello world 1", style = MaterialTheme.typography.titleLarge)
        // Semantic modifier passed to ComposableA.
        A(
            text = "A with semantics",
            modifier = Modifier.semantics(false) {
                testTag = "tag-a"
                testTagsAsResourceId = true
            }
        )


        // Calling ComposableAB directly with semantics modifier.
        B(
            text = "B with semantics",
            modifier = Modifier
        )

        C(
            text = "C with semantics",
            modifier = Modifier.semantics(true) {
                testTag = "tag-c"
                testTagsAsResourceId = true
            }
        )

        D(
            text = "D with semantics",
            modifier = Modifier.semantics(true) {
                testTag = "tag-d"
                testTagsAsResourceId = true
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun A(modifier: Modifier = Modifier, text: String) {
    // B is accepting parent modifier of a.
    B(
        modifier = modifier.semantics(true) {
            testTag = "tag-b"
            testTagsAsResourceId = true
        },
        text = text
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun B(modifier: Modifier = Modifier, text: String) {
    Column (modifier = modifier){
        Text(modifier = modifier, text = text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun C(modifier: Modifier = Modifier, text: String) {
    Text(modifier = modifier, text = text, style = MaterialTheme.typography.titleLarge)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun D(modifier: Modifier, text: String) {
    Button(
        modifier = modifier
            .semantics(true) {
                testTag = "tag-button"
                testTagsAsResourceId = true
            },
        onClick = { /* Do something */ }
    ) {
        Text(text)
    }
}