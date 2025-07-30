package io.github.techpw.sementics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import live.pw.compose.semantics.R

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        //io.github.techpw.semantics:id/auto_login_MainActivity_Column_Text_1340562411
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
//        Text(text = "hello world 0")
//        Text(text = "hello world 1") //io.github.techpw.semantics:id/auto_login_MainActivity_Column_Text_498198592
//
//        //io.github.techpw.semantics:id/auto_login_MainActivity_Column_MyText_-1671319640
//        MyText(text = "hello world 2")
//
//        Text(text = "Hello world 3", style = TextStyle.Default)
//
//        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Text_-1416452045
//        Text(text = "hello world 4", modifier = Modifier.padding(16.dp)) // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Text_-1416452045
//

        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Row_-1282579546
//        Row(modifier = Modifier
//            .fillMaxWidth()
//            .background(color = Color.Green)
//            .height(50.dp)
//        ) {}
//
//        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Text_1841024471
//        Text(text = stringResource(R.string.testing_text))
//
//        //io.github.techpw.semantics:id/auto_login_MainActivity_Column_Text_-507501049
        Text(text = stringResource(R.string.hello_world_6))

        Button(modifier = Modifier , onClick = { // io.github.techpw.semantics:id/auto_login_MainActivity_Text_-539451011
            println("button clicked")
        }){
            Text(text = "Button B", modifier = Modifier)
        }
        //io.github.techpw.semantics:id/auto_login_MainActivity_Column_HorizontalPager_416281447
        HorizontalPager(state = rememberPagerState { 0 }, modifier = Modifier.size(20.dp)) { }
        //io.github.techpw.semantics:id/auto_login_MainActivity_Column_HorizontalPager_416281447
        HorizontalPager(state = rememberPagerState { 0 }, modifier = Modifier.size(21.dp)) { }

//        Button(modifier = Modifier , onClick = {
//            println("button clicked")
//        }){
//            Text(text = "Button C", modifier = Modifier)
//        }

        //Spacer(modifier = Modifier.height(16.dp))

        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Row_-382487263
        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Row_-1337241181
        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Row_1653461923
        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Row_29622250
        // io.github.techpw.semantics:id/auto_login_MainActivity_Column_Row_-570331632
//        Row(modifier = Modifier
//            .fillMaxWidth()
//            .background(color = Color.Green)
//            .height(56.dp)
//        ) {}

        // Spacer(modifier = Modifier.height(16.dp))
//
//        Row(modifier = Modifier
//            .fillMaxWidth()
//            .background(color = Color.Green)
//            .height(56.dp)
//        ) {}

        //Widget()
    }
}

//@Composable
//fun MyText(modifier: Modifier = Modifier.padding(2.dp), text: String) {
//    Text(modifier = modifier, text = text)
//}