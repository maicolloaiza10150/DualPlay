// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL

package com.hdrdualcontroller.app.ui.regist

import androidx.lifecycle.ViewModel
import com.hdrdualcontroller.app.lib.RegistInfo

/**
 * Pasa los datos del formulario de registro (incluye PIN y Account ID) a la pantalla que ejecuta el registro
 * sin serializarlos en el estado de navegación ni en un Intent. Se vacía en cuanto se consumen.
 */
class RegistFlowViewModel: ViewModel()
{
	var info: RegistInfo? = null
	var assignManualHostId: Long? = null
}
