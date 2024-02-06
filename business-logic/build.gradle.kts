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

import eu.europa.ec.euidi.addConfigField
import eu.europa.ec.euidi.getProperty

plugins {
    id("eudi.android.library")
    id("eudi.wallet.core")
}

android {
    namespace = "eu.europa.ec.businesslogic"

    defaultConfig {
        addConfigField("DEEPLINK", "eudi-wallet://")
        addConfigField(
            "APP_VERSION",
            getProperty("VERSION_NAME", "version.properties") ?: ""
        )
    }
}

dependencies {
    implementation(project(":resources-logic"))
    implementation(libs.gson)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security)
    implementation(libs.androidx.appAuth)
    implementation(libs.logcat)
    implementation(libs.google.phonenumber)
    implementation(libs.rootbeer)

    testImplementation(project(":test-logic"))
    androidTestImplementation(project(":test-logic"))
}