package io.github.farhazulmullick.sementics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.tooling.preview.Preview
import io.github.farhazulmullick.sementics.ui.theme.SementicsTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SementicsTheme {
                Scaffold(modifier = Modifier
                    .fillMaxSize()
                    .semantics {
                        testTagsAsResourceId = true
                        testTag = "test_tag_scaffold"
                    }) { innerPadding ->
                    Column(modifier = Modifier
                        .padding(innerPadding)
                        .semantics {
                            testTagsAsResourceId = true
                            testTag = "test_tag_column"
                        }) {
                        Text(
                            text = "Hello Android",
                            modifier = Modifier.semantics {
                                testTagsAsResourceId = true
                                testTag = "test_tag_tv_hello_android"
                            }
                        )

                        Button(
                            modifier = Modifier.semantics {
                                testTagsAsResourceId = true
                                testTag = "test_tag_button_1"
                            },
                            onClick = {
                                println("button_test_tag :: clicked!!")
                            }
                        ) {
                            Text(
                                text = "Button1",
                                modifier = Modifier.semantics {
                                    testTagsAsResourceId = true
                                    testTag = "test_tag_tv_btn_1"
                                }
                            )
                        }

                        Button(
                            modifier = Modifier.semantics {
                                testTagsAsResourceId = true
                                testTag = "test_tag_button_2"
                            },
                            onClick = {
                                println("button_test_tag :: clicked!!")
                            }
                        ) {
                            Text(
                                text = "Button2",
                                modifier = Modifier.semantics {
                                    testTagsAsResourceId = true
                                    testTag = "test_tag_tv_btn_2"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {

}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SementicsTheme {
        Greeting("Android")
    }
}