package com.aiagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aiagent.agent.AgentLoop
import com.aiagent.config.ConfigManager
import com.aiagent.data.ExperienceDao
import com.aiagent.experience.ExperienceMatcher
import com.aiagent.experience.ExperienceParser
import com.aiagent.experience.ExperienceParserResult
import com.aiagent.experience.Reflector
import com.aiagent.tools.*
import com.aiagent.ui.ChatScreen
import com.aiagent.ui.ExperienceScreen
import com.aiagent.ui.SettingsScreen
import com.aiagent.ui.theme.AIAgentTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var agentLoop: AgentLoop
    private lateinit var experienceDao: ExperienceDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AIAgentApplication
        configManager = app.configManager
        experienceDao = app.database.experienceDao()

        // 初始化工具注册中心
        val toolRegistry = ToolRegistry()
        toolRegistry.register(ImageAnalysisTool())
        toolRegistry.register(SearchTool())
        toolRegistry.register(CalculateTool())
        toolRegistry.register(SummarizeTool())

        val experienceMatcher = ExperienceMatcher(experienceDao)
        val reflector = Reflector(experienceDao, app.llmClient, configManager)

        agentLoop = AgentLoop(
            llmClient = app.llmClient,
            configManager = configManager,
            toolRegistry = toolRegistry,
            experienceMatcher = experienceMatcher,
            reflector = reflector
        )

        val experienceParser = ExperienceParser(experienceDao, app.llmClient, configManager)

        setContent {
            AIAgentTheme {
                MainNavigation(
                    configManager = configManager,
                    agentLoop = agentLoop,
                    experienceDao = experienceDao,
                    experienceParser = experienceParser
                )
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainNavigation(
    configManager: ConfigManager,
    agentLoop: AgentLoop,
    experienceDao: ExperienceDao,
    experienceParser: ExperienceParser
) {
    val navController = rememberNavController()
    var isConfigured by remember { mutableStateOf(configManager.hasConfig()) }
    val coroutineScope = rememberCoroutineScope()

    val bottomNavItems = listOf(
        BottomNavItem("chat", "对话", Icons.Default.Chat),
        BottomNavItem("experience", "经验库", Icons.Default.AutoAwesome),
        BottomNavItem("settings", "设置", Icons.Default.Settings)
    )

    // 未配置时跳转到设置页
    LaunchedEffect(isConfigured) {
        if (!isConfigured) {
            navController.navigate("settings") {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            // 如果未配置且不是设置页，跳转到设置页
                            if (!isConfigured && item.route != "settings") {
                                navController.navigate("settings") {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                }
                                return@NavigationBarItem
                            }
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "settings", // 始终从设置页开始（LaunchedEffect 会自动跳转）
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("chat") {
                ChatScreen(agentLoop = agentLoop)
            }

            composable("experience") {
                ExperienceScreen(
                    getExperiences = {
                        try { experienceDao.getAll() } catch (_: Exception) { emptyList() }
                    },
                    onDelete = { id ->
                        coroutineScope.launch {
                            try { experienceDao.deleteById(id) } catch (_: Exception) { }
                        }
                    },
                    onParseDemo = { demoText ->
                        experienceParser.parseDemo(demoText)
                    }
                )
            }

            composable("settings") {
                SettingsScreen(
                    configManager = configManager,
                    onConfigSaved = {
                        agentLoop.invalidateCache()
                        isConfigured = true
                        coroutineScope.launch {
                            navController.navigate("chat") {
                                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            }
                        }
                    }
                )
            }
        }
    }
}
