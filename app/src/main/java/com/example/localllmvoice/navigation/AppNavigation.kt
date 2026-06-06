package com.example.localllmvoice.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.localllmvoice.di.AppContainer
import com.example.localllmvoice.domain.model.ConversationTopics
import com.example.localllmvoice.ui.chat.ChatScreen
import com.example.localllmvoice.ui.chat.ChatViewModel
import com.example.localllmvoice.ui.dashboard.DashboardScreen
import com.example.localllmvoice.ui.dashboard.DashboardViewModel
import com.example.localllmvoice.ui.feedback.FeedbackScreen
import com.example.localllmvoice.ui.feedback.FeedbackViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat/{topicId}"
    const val FEEDBACK = "feedback"

    fun chat(topicId: String) = "chat/$topicId"
}

@Composable
fun SoloTalkNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = modifier,
    ) {
        composable(Routes.DASHBOARD) {
            val viewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModelFactory(appContainer),
            )
            DashboardScreen(
                viewModel = viewModel,
                onTopicSelected = { topic ->
                    navController.navigate(Routes.chat(topic.id))
                },
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("topicId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString("topicId")
            val topic = topicId?.let(ConversationTopics::findById)
            if (topic == null) {
                navController.popBackStack()
                return@composable
            }
            val viewModel: ChatViewModel = viewModel(
                key = topicId,
                factory = ChatViewModelFactory(
                    topic = topic,
                    appContainer = appContainer,
                ),
            )
            ChatScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    viewModel.endConversation()
                    navController.popBackStack()
                },
                onEndChat = {
                    val hasFeedback = viewModel.prepareFeedback()
                    viewModel.endConversation()
                    if (hasFeedback) {
                        navController.navigate(Routes.FEEDBACK) {
                            popUpTo(Routes.CHAT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }

        composable(Routes.FEEDBACK) {
            val viewModel: FeedbackViewModel = viewModel(
                factory = FeedbackViewModelFactory(appContainer),
            )
            FeedbackScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
