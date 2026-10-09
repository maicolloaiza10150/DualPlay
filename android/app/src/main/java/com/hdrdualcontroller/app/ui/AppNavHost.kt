// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hdrdualcontroller.app.ui.main.MainScreen
import com.hdrdualcontroller.app.ui.manual.EditManualScreen
import com.hdrdualcontroller.app.ui.psn.PsnLoginScreen
import com.hdrdualcontroller.app.ui.regist.RegistExecuteScreen
import com.hdrdualcontroller.app.ui.regist.RegistFlowViewModel
import com.hdrdualcontroller.app.ui.regist.RegistScreen
import com.hdrdualcontroller.app.ui.settings.LogsScreen
import com.hdrdualcontroller.app.ui.settings.RegisteredHostsScreen
import com.hdrdualcontroller.app.ui.settings.SettingsScreen
import com.hdrdualcontroller.app.ui.spike.DualSenseBluetoothSpikeScreen
import com.hdrdualcontroller.app.ui.spike.DualSenseSpikeScreen
import android.net.Uri

private object Routes
{
	const val MAIN = "main"
	const val SETTINGS = "settings"
	const val REGISTERED_HOSTS = "settings/hosts"
	const val LOGS = "settings/logs"
	const val PSN_LOGIN = "psn"
	const val SPIKE_DUALSENSE = "spike/dualsense"
	const val SPIKE_BLUETOOTH = "spike/bluetooth"
	const val MANUAL = "manual?id={id}"
	const val REGIST = "regist?host={host}&manualId={manualId}"
	const val REGIST_EXECUTE = "regist/execute"
}

@Composable
fun AppNavHost()
{
	val navController = rememberNavController()
	// Ámbito de la actividad: lleva el formulario de registro a la pantalla de ejecución.
	val registFlow: RegistFlowViewModel = viewModel()

	fun openRegist(host: String?, manualId: Long?)
	{
		navController.navigate("regist?host=${Uri.encode(host ?: "")}&manualId=${manualId ?: -1L}")
	}

	NavHost(navController, startDestination = Routes.MAIN)
	{
		composable(Routes.MAIN) {
			MainScreen(
				onOpenSettings = { navController.navigate(Routes.SETTINGS) },
				onAddManual = { navController.navigate("manual?id=-1") },
				onRegister = { openRegist(null, null) },
				onRegisterHost = { host, manualId -> openRegist(host, manualId) },
				onEditManual = { navController.navigate("manual?id=$it") })
		}

		composable(Routes.SETTINGS) {
			SettingsScreen(
				onBack = { navController.popBackStack() },
				onOpenRegisteredHosts = { navController.navigate(Routes.REGISTERED_HOSTS) },
				onOpenLogs = { navController.navigate(Routes.LOGS) },
				onPsnLogin = { navController.navigate(Routes.PSN_LOGIN) },
				onOpenDualSenseSpike = { navController.navigate(Routes.SPIKE_DUALSENSE) },
				onOpenBluetoothSpike = { navController.navigate(Routes.SPIKE_BLUETOOTH) })
		}

		composable(Routes.SPIKE_BLUETOOTH) {
			DualSenseBluetoothSpikeScreen(onBack = { navController.popBackStack() })
		}

		composable(Routes.SPIKE_DUALSENSE) {
			DualSenseSpikeScreen(onBack = { navController.popBackStack() })
		}

		composable(Routes.REGISTERED_HOSTS) {
			RegisteredHostsScreen(
				onBack = { navController.popBackStack() },
				onRegister = { openRegist(null, null) })
		}

		composable(Routes.LOGS) {
			LogsScreen(onBack = { navController.popBackStack() })
		}

		composable(Routes.PSN_LOGIN) {
			PsnLoginScreen(
				onBack = { navController.popBackStack() },
				onLoggedIn = { navController.popBackStack() })
		}

		composable(
			Routes.MANUAL,
			arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
		) { entry ->
			val id = entry.arguments?.getLong("id") ?: -1L
			EditManualScreen(manualHostId = id.takeIf { it >= 0 }, onBack = { navController.popBackStack() })
		}

		composable(
			Routes.REGIST,
			arguments = listOf(
				navArgument("host") { type = NavType.StringType; defaultValue = "" },
				navArgument("manualId") { type = NavType.LongType; defaultValue = -1L })
		) { entry ->
			val host = entry.arguments?.getString("host").orEmpty()
			val manualId = entry.arguments?.getLong("manualId") ?: -1L
			RegistScreen(
				// Con un host concreto (consola descubierta/manual) no se usa broadcast, igual que antes.
				initialHost = host.ifEmpty { null },
				initialBroadcast = host.isEmpty(),
				assignManualHostId = manualId.takeIf { it >= 0 },
				flow = registFlow,
				onBack = { navController.popBackStack() },
				onPsnLogin = { navController.navigate(Routes.PSN_LOGIN) },
				onStart = { navController.navigate(Routes.REGIST_EXECUTE) })
		}

		composable(Routes.REGIST_EXECUTE) {
			RegistExecuteScreen(
				flow = registFlow,
				onBack = { navController.popBackStack() },
				// Registro correcto: se vuelve a la pantalla desde la que se abrió el formulario.
				onFinished = { navController.popBackStack(Routes.REGIST, inclusive = true) })
		}
	}
}
