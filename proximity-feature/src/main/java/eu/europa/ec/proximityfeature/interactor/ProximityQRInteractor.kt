/*
 * Copyright (c) 2023 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.proximityfeature.interactor

import androidx.activity.ComponentActivity
import eu.europa.ec.businesslogic.controller.walletcore.TransferEventPartialState
import eu.europa.ec.businesslogic.controller.walletcore.WalletCorePresentationController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.mapNotNull

sealed class ProximityQRPartialState {
    data class QrReady(val qrCode: String) : ProximityQRPartialState()
    data class Error(val error: String) : ProximityQRPartialState()
    data object Connected : ProximityQRPartialState()
    data object Disconnected : ProximityQRPartialState()
}

interface ProximityQRInteractor {
    fun startQrEngagement(): Flow<ProximityQRPartialState>
    fun toggleNfcEngagement(
        componentActivity: ComponentActivity,
        toggle: Boolean
    )

    fun cancelTransfer()
    fun setConfig(config: RequestUriConfig)
}

class ProximityQRInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCorePresentationController: WalletCorePresentationController
) : ProximityQRInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun setConfig(config: RequestUriConfig) {
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }

    override fun startQrEngagement(): Flow<ProximityQRPartialState> {
        walletCorePresentationController.startQrEngagement()
        return walletCorePresentationController.events.mapNotNull {
            when (it) {
                is TransferEventPartialState.Connected -> {
                    ProximityQRPartialState.Connected
                }

                is TransferEventPartialState.Error -> {
                    ProximityQRPartialState.Error(error = it.error)
                }

                is TransferEventPartialState.QrEngagementReady -> {
                    ProximityQRPartialState.QrReady(qrCode = it.qrCode)
                }

                is TransferEventPartialState.Disconnected -> {
                    ProximityQRPartialState.Disconnected
                }

                else -> null
            }
        }.safeAsync {
            ProximityQRPartialState.Error(error = it.localizedMessage ?: genericErrorMsg)
        }.cancellable()
    }

    override fun toggleNfcEngagement(
        componentActivity: ComponentActivity,
        toggle: Boolean
    ) {
        walletCorePresentationController.toggleNfcEngagement(componentActivity, toggle)
    }

    override fun cancelTransfer() {
        walletCorePresentationController.stopPresentation()
    }
}