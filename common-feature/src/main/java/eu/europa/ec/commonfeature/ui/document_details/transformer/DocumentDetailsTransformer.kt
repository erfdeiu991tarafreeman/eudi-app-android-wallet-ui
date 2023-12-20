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

package eu.europa.ec.commonfeature.ui.document_details.transformer

import eu.europa.ec.businesslogic.util.getStringFromJsonOrEmpty
import eu.europa.ec.businesslogic.util.toDateFormatted
import eu.europa.ec.businesslogic.util.toList
import eu.europa.ec.commonfeature.model.DocumentUi
import eu.europa.ec.commonfeature.model.toDocumentTypeUi
import eu.europa.ec.commonfeature.ui.document_details.model.DocumentDetailsUi
import eu.europa.ec.commonfeature.ui.document_details.model.DocumentJsonKeys
import eu.europa.ec.commonfeature.util.extractFullNameFromDocumentOrEmpty
import eu.europa.ec.commonfeature.util.getKeyValueUi
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.nameSpacedDataJSONObject
import eu.europa.ec.resourceslogic.R
import eu.europa.ec.resourceslogic.provider.ResourceProvider
import eu.europa.ec.uilogic.component.InfoTextWithNameAndImageData
import eu.europa.ec.uilogic.component.InfoTextWithNameAndValueData
import org.json.JSONObject

object DocumentDetailsTransformer {

    fun transformToUiItems(
        document: Document,
        resourceProvider: ResourceProvider,
        docType: String,
    ): DocumentUi? {

        // Get the JSON Object from EudiWallerCore.
        val documentJson = (document.nameSpacedDataJSONObject[docType] as JSONObject)

        // Create a JSON Array with all its keys (i.e. given_name, family_name, etc.) keeping their original order.
        val documentKeysJsonArray = documentJson.names() ?: return null

        // Create a JSON Array with all its values (i.e. John, Smith, etc.) keeping their original order.
        val documentValuesJsonArray = documentJson.toJSONArray(documentKeysJsonArray) ?: return null

        val detailsItems = documentValuesJsonArray
            .toList()
            .withIndex()
            // Create a connection between keys and values using their index--original order.
            .associateBy {
                documentKeysJsonArray.get(it.index)
            }
            // Now that we have both the keys and the values, transform them to UI items.
            .map {
                val value = it.value.value
                val key = it.key.toString()
                transformToDocumentDetailsUi(
                    key = key,
                    item = value,
                    resourceProvider = resourceProvider
                )
            }

        return DocumentUi(
            documentId = document.id,
            documentName = document.name,
            documentType = document.docType.toDocumentTypeUi(),
            documentExpirationDateFormatted = documentJson.getStringFromJsonOrEmpty(
                key = DocumentJsonKeys.EXPIRY_DATE
            ).toDateFormatted().toString(),
            documentImage = documentJson.getStringFromJsonOrEmpty(
                key = DocumentJsonKeys.PORTRAIT
            ),
            documentDetails = detailsItems,
            userFullName = extractFullNameFromDocumentOrEmpty(document)
        )
    }

}

private fun transformToDocumentDetailsUi(
    key: String,
    item: Any,
    resourceProvider: ResourceProvider
): DocumentDetailsUi {
    val (keyUi, valueUi) = getKeyValueUi(item, key, resourceProvider)

    return when (key) {
        DocumentJsonKeys.SIGNATURE -> {
            DocumentDetailsUi.SignatureItem(
                itemData = InfoTextWithNameAndImageData(
                    title = keyUi,
                    base64Image = valueUi
                )
            )
        }

        DocumentJsonKeys.PORTRAIT -> {
            DocumentDetailsUi.DefaultItem(
                itemData = InfoTextWithNameAndValueData.create(
                    title = keyUi,
                    infoValues = arrayOf(resourceProvider.getString(R.string.document_details_portrait_readable_identifier))
                )
            )
        }

        else -> {
            DocumentDetailsUi.DefaultItem(
                itemData = InfoTextWithNameAndValueData.create(
                    title = keyUi,
                    infoValues = arrayOf(valueUi)
                )
            )
        }
    }
}