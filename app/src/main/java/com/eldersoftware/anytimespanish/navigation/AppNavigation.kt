package com.eldersoftware.anytimespanish.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eldersoftware.anytimespanish.di.AppContainer
import com.eldersoftware.anytimespanish.domain.StartupState
import com.eldersoftware.anytimespanish.domain.model.ConversationTopics
import com.eldersoftware.anytimespanish.ui.chat.ChatScreen
import com.eldersoftware.anytimespanish.ui.chat.ChatViewModel
import com.eldersoftware.anytimespanish.ui.dashboard.DashboardScreen
import com.eldersoftware.anytimespanish.ui.dashboard.DashboardViewModel
import com.eldersoftware.anytimespanish.ui.feedback.FeedbackScreen
import com.eldersoftware.anytimespanish.ui.feedback.FeedbackViewModel
import com.eldersoftware.anytimespanish.ui.onboarding.OnboardingDownloadScreen
import com.eldersoftware.anytimespanish.ui.onboarding.OnboardingDownloadViewModel
import com.eldersoftware.anytimespanish.ui.onboarding.OnboardingPaywallScreen
import com.eldersoftware.anytimespanish.ui.onboarding.OnboardingWelcomeScreen
import com.eldersoftware.anytimespanish.ui.onboarding.PaywallViewModel

object Routes {
    const val DASHBOARD = "dashboard"
    const val CHAT = "chat/{topicId}"
    const val FEEDBACK = "feedback"
    const val ONBOARDING_GRAPH = "onboarding"
    const val ONBOARDING_DOWNLOAD_GRAPH = "onboarding/download_graph"
    const val ONBOARDING_WELCOME = "onboarding/welcome"
    const val ONBOARDING_PAYWALL = "onboarding/paywall"
    const val ONBOARDING_DOWNLOAD = "onboarding/download"
    const val ONBOARDING_DOWNLOAD_RECOVERY = "onboarding/download_recovery"
    const val PAYWALL = "paywall"

    fun chat(topicId: String) = "chat/$topicId"
}

@Composable
fun AnytimeSpanishNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val startDestination = remember(appContainer) {
        appContainer.decideStartupStateUseCase().startDestination()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        navigation(
            startDestination = Routes.ONBOARDING_WELCOME,
            route = Routes.ONBOARDING_GRAPH,
        ) {
            composable(Routes.ONBOARDING_WELCOME) {
                OnboardingWelcomeScreen(
                    onContinue = {
                        navController.navigate(Routes.ONBOARDING_PAYWALL)
                    }
                )
            }
            composable(Routes.ONBOARDING_PAYWALL) {
                val viewModel: PaywallViewModel = viewModel(
                    factory = PaywallViewModelFactory(appContainer),
                )
                OnboardingPaywallScreen(
                    viewModel = viewModel,
                    onPurchased = {
                        navController.navigate(Routes.ONBOARDING_DOWNLOAD)
                    },
                    onContinueFree = {
                        navController.navigate(Routes.ONBOARDING_DOWNLOAD)
                    },
                    onClose = {},
                )
            }
            composable(Routes.ONBOARDING_DOWNLOAD) {
                val viewModel: OnboardingDownloadViewModel = viewModel(
                    factory = OnboardingDownloadViewModelFactory(appContainer),
                )
                OnboardingDownloadScreen(
                    viewModel = viewModel,
                    onFinished = {
                        appContainer.onboardingPreferences.setComplete(true)
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING_GRAPH) { inclusive = true }
                        }
                    }
                )
            }
        }

        navigation(
            startDestination = Routes.ONBOARDING_DOWNLOAD_RECOVERY,
            route = Routes.ONBOARDING_DOWNLOAD_GRAPH,
        ) {
            composable(Routes.ONBOARDING_DOWNLOAD_RECOVERY) {
                val viewModel: OnboardingDownloadViewModel = viewModel(
                    factory = OnboardingDownloadViewModelFactory(appContainer),
                )
                OnboardingDownloadScreen(
                    viewModel = viewModel,
                    onFinished = {
                        appContainer.onboardingPreferences.setComplete(true)
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING_DOWNLOAD_GRAPH) { inclusive = true }
                        }
                    }
                )
            }
        }

        composable(Routes.PAYWALL) {
            val viewModel: PaywallViewModel = viewModel(
                factory = PaywallViewModelFactory(appContainer),
            )
            OnboardingPaywallScreen(
                viewModel = viewModel,
                onPurchased = { navController.popBackStack() },
                onContinueFree = null,
                onClose = { navController.popBackStack() },
            )
        }

        composable(Routes.DASHBOARD) {
            val viewModel: DashboardViewModel = viewModel(
                factory = DashboardViewModelFactory(appContainer),
            )
            DashboardScreen(
                viewModel = viewModel,
                onTopicSelected = { topic ->
                    navController.navigate(Routes.chat(topic.id))
                },
                onUnlockRequested = { navController.navigate(Routes.PAYWALL) },
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

private fun StartupState.startDestination(): String = when (this) {
    StartupState.NeedsOnboarding -> Routes.ONBOARDING_GRAPH
    StartupState.NeedsModelDownload -> Routes.ONBOARDING_DOWNLOAD_GRAPH
    StartupState.ReadyForDashboard -> Routes.DASHBOARD
}
