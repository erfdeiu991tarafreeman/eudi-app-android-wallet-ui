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

package eu.europa.ec.presentationfeature.interactor

import eu.europa.ec.businesslogic.controller.walletcore.TransferEventPartialState
import eu.europa.ec.businesslogic.controller.walletcore.WalletCoreDocumentsController
import eu.europa.ec.businesslogic.controller.walletcore.WalletCorePresentationController
import eu.europa.ec.businesslogic.extension.safeAsync
import eu.europa.ec.commonfeature.config.RequestUriConfig
import eu.europa.ec.commonfeature.config.toDomainConfig
import eu.europa.ec.commonfeature.ui.request.Event
import eu.europa.ec.commonfeature.ui.request.model.RequestDataUi
import eu.europa.ec.commonfeature.ui.request.transformer.RequestTransformer
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

sealed class PresentationRequestInteractorPartialState {
    data class Success(
        val verifierName: String? = null,
        val verifierIsTrusted: Boolean,
        val requestDocuments: List<RequestDataUi<Event>>
    ) : PresentationRequestInteractorPartialState()

    data class NoData(
        val verifierName: String? = null,
        val verifierIsTrusted: Boolean,
    ) : PresentationRequestInteractorPartialState()

    data class Failure(val error: String) : PresentationRequestInteractorPartialState()
    data object Disconnect : PresentationRequestInteractorPartialState()
}

interface PresentationRequestInteractor {
    fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState>
    fun stopPresentation()
    fun updateRequestedDocuments(items: List<RequestDataUi<Event>>)
    fun setConfig(config: RequestUriConfig)
}

class PresentationRequestInteractorImpl(
    private val resourceProvider: ResourceProvider,
    private val walletCorePresentationController: WalletCorePresentationController,
    private val walletCoreDocumentsController: WalletCoreDocumentsController
) : PresentationRequestInteractor {

    private val genericErrorMsg
        get() = resourceProvider.genericErrorMessage()

    override fun setConfig(config: RequestUriConfig) {
        walletCorePresentationController.setConfig(config.toDomainConfig())
    }

    override fun getRequestDocuments(): Flow<PresentationRequestInteractorPartialState> =
        walletCorePresentationController.events.mapNotNull { response ->
            when (response) {
                is TransferEventPartialState.RequestReceived -> {
                    if (response.requestData.all { it.docRequest.requestItems.isEmpty() }) {
                        PresentationRequestInteractorPartialState.NoData(
                            verifierName = response.verifierName,
                            verifierIsTrusted = response.verifierIsTrusted,
                        )
                    } else {
                        val requestDataUi = RequestTransformer.transformToUiItems(
                            storageDocuments = walletCoreDocumentsController.getAllDocuments(),
                            requestDocuments = response.requestData,
                            requiredFieldsTitle = resourceProvider.getString(R.string.request_required_fields_title),
                            resourceProvider = resourceProvider
                        )
                        PresentationRequestInteractorPartialState.Success(
                            verifierName = response.verifierName,
                            verifierIsTrusted = response.verifierIsTrusted,
                            requestDocuments = requestDataUi
                        )
                    }
                }

                is TransferEventPartialState.Error -> {
                    PresentationRequestInteractorPartialState.Failure(error = response.error)
                }

                is TransferEventPartialState.Disconnected -> {
                    PresentationRequestInteractorPartialState.Disconnect
                }

                else -> null
            }
        }.safeAsync {
            PresentationRequestInteractorPartialState.Failure(
                error = it.localizedMessage ?: genericErrorMsg
            )
        }

    override fun stopPresentation() {
        walletCorePresentationController.stopPresentation()
    }

    override fun updateRequestedDocuments(items: List<RequestDataUi<Event>>) {
        val disclosedDocuments = RequestTransformer.transformToDomainItems(items)
        walletCorePresentationController.updateRequestedDocuments(disclosedDocuments)
    }
}