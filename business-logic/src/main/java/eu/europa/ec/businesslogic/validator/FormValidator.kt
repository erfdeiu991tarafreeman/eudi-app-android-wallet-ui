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

package eu.europa.ec.businesslogic.validator

import android.util.Patterns
import com.google.i18n.phonenumbers.PhoneNumberUtil
import eu.europa.ec.businesslogic.controller.log.LogController
import eu.europa.ec.businesslogic.util.safeLet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

interface FormValidator {
    fun validateForm(form: Form): Flow<FormValidationResult>
    fun validateForms(forms: List<Form>): Flow<FormsValidationResult>
}

class FormValidatorImpl(
    private val logController: LogController
) : FormValidator {

    override fun validateForm(form: Form): Flow<FormValidationResult> = flow {
        form.inputs.forEach { (rules, value) ->
            rules.forEach { rule ->
                validateRule(rule, value)?.let {
                    emit(it)
                    return@flow
                }
            }
        }
        emit(FormValidationResult(isValid = true))
    }.flowOn(Dispatchers.IO)

    override fun validateForms(forms: List<Form>): Flow<FormsValidationResult> = flow {
        val errors = mutableListOf<String>()
        var isValid = true
        forms.forEach { form ->
            form.inputs.forEach { (rules, value) ->
                rules.forEach { rule ->
                    validateRule(rule, value)?.let {
                        isValid = false
                        errors.add(it.message)
                    }
                }
            }
        }
        emit(FormsValidationResult(isValid, errors))
    }.flowOn(Dispatchers.IO)

    private fun validateRule(rule: Rule, value: String): FormValidationResult? {
        return when (rule) {
            is Rule.ValidateEmail -> checkValidationResult(isEmailValid(value), rule.errorMessage)
            is Rule.ValidatePhoneNumber -> checkValidationResult(
                isPhoneNumberValid(value, rule.countryCode),
                rule.errorMessage
            )

            is Rule.ValidateStringLength -> checkValidationResult(
                isStringLengthValid(
                    value,
                    rule.lengths
                ), rule.errorMessage
            )

            is Rule.ValidateStringMaxLength -> checkValidationResult(
                isStringMaxLengthValid(
                    value,
                    rule.length
                ), rule.errorMessage
            )

            is Rule.ValidateStringMinLength -> checkValidationResult(
                isStringMinLengthValid(value, rule.length),
                rule.errorMessage
            )

            is Rule.ValidateNotEmpty -> checkValidationResult(value.isNotEmpty(), rule.errorMessage)
            is Rule.ValidateRegex -> checkValidationResult(
                isRegexMatching(value, rule.regex),
                rule.errorMessage
            )

            is Rule.ContainsRegex -> checkValidationResult(
                isRegexIncluded(value, rule.regex),
                rule.errorMessage
            )

            is Rule.ValidateStringRange -> checkValidationResult(
                isInRange(value, rule.range),
                rule.errorMessage
            )

            is Rule.ValidateStringMatch -> checkValidationResult(
                isStringMatching(
                    value,
                    rule.stringToMatch,
                    rule.isCaseSensitive
                ), rule.errorMessage
            )

            is Rule.ValidateDuplicateCharacterNotInConsecutiveOrder -> checkValidationResult(
                duplicateCharacterNotInConsecutiveOrder(
                    value,
                    rule.maxTimesOfConsecutiveOrder
                ), rule.errorMessage
            )

            is Rule.ValidateStringNotMatch -> checkValidationResult(
                isStringNotMatching(
                    value,
                    rule.stringToMatch,
                    rule.isCaseSensitive
                ), rule.errorMessage
            )

            is Rule.ValidateNumericNotInConsecutiveSequenceOrder -> checkValidationResult(
                numericNotInConsecutiveSequenceOrder(value, rule.minLength), rule.errorMessage
            )

            is Rule.StringShouldEndWithNumber -> checkValidationResult(
                stringHasEndsWithNumber(value),
                rule.errorMessage
            )

            is Rule.ValidateFileSizeMaxSize -> checkValidationResult(
                isFileSizeValid(value, rule.size),
                rule.errorMessage
            )

            is Rule.ValidateFileExtension -> checkValidationResult(
                isFileExtensionValid(value, rule.supportedExtensions),
                rule.errorMessage
            )

            is Rule.ValidateMinimumAmount -> checkValidationResult(
                isMinimumAmountValid(value, rule.minimumAmount),
                rule.errorMessage
            )

            is Rule.ValidateMaximumAmount -> checkValidationResult(
                isMaximumAmountValid(value, rule.maximumAmount),
                rule.errorMessage
            )

            is Rule.ValidateFileName -> checkValidationResult(
                isFileNameValid(value),
                rule.errorMessage
            )
        }
    }

    @Suppress("KotlinConstantConditions")
    private fun checkValidationResult(
        isValid: Boolean,
        errorMessage: String
    ): FormValidationResult? {
        return if (!isValid) {
            FormValidationResult(
                isValid,
                errorMessage
            )
        } else null
    }

    private fun isEmailValid(value: String): Boolean =
        value.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(value).matches()

    private fun isPhoneNumberValid(value: String, countryCode: String): Boolean {
        val phoneNumberUtil = PhoneNumberUtil.getInstance()
        return try {
            phoneNumberUtil.isValidNumber(
                phoneNumberUtil.parse(
                    value,
                    countryCode
                )
            )
        } catch (e: Exception) {
            logController.e(javaClass.simpleName, e)
            false
        }
    }

    private fun isStringLengthValid(value: String, lengths: List<Int>): Boolean =
        value.length in lengths

    private fun isStringMaxLengthValid(value: String, length: Int): Boolean =
        value.length <= length

    private fun isStringMinLengthValid(value: String, length: Int): Boolean =
        value.length >= length

    private fun isRegexMatching(value: String, regex: Regex): Boolean =
        value.matches(regex)

    private fun isRegexIncluded(value: String, regex: Regex): Boolean =
        value.contains(regex)

    private fun isInRange(value: String, range: IntRange): Boolean = value.length in range

    private fun isStringMatching(
        value: String,
        stringToMatch: String,
        isCaseSensitive: Boolean = false
    ) = value.equals(
        stringToMatch,
        !isCaseSensitive
    )

    private fun isStringNotMatching(
        value: String,
        stringToMatch: String,
        isCaseSensitive: Boolean = false
    ) = !value.equals(
        stringToMatch,
        !isCaseSensitive
    )

    private fun duplicateCharacterNotInConsecutiveOrder(
        value: String,
        maxTimesOfConsecutiveOrder: Int
    ): Boolean {
        if (maxTimesOfConsecutiveOrder < 2) {
            return true
        }
        val consecutiveOrderRegex = "(\\d|\\w)\\1{${maxTimesOfConsecutiveOrder - 1},}".toRegex()
        val consecutiveOrderCount = consecutiveOrderRegex.findAll(value).toList().size
        return consecutiveOrderCount == 0
    }

    private fun numericNotInConsecutiveSequenceOrder(value: String, minLength: Int): Boolean {
        if (value.isEmpty() || minLength < 2 || value.toIntOrNull() == null || value.length != minLength) return true
        val firstNumber = value.first()
        val isReversed = firstNumber > value[1]
        val sequence = StringBuilder().apply {
            append(firstNumber)
            for (i in 1 until minLength) {
                append(
                    if (isReversed) {
                        firstNumber - i
                    } else {
                        firstNumber + i
                    }
                )
            }
        }.toString()
        return !value.contains(sequence)
    }

    private fun stringHasEndsWithNumber(text: String): Boolean {
        return text.last().isDigit()
    }

    private fun isFileSizeValid(text: String, size: Long): Boolean {
        return text.toLong() <= size
    }

    private fun isFileNameValid(text: String): Boolean {
        return isRegexMatching(text, "^[a-zA-Z\\d-!@#\$%^&*()_+= ]+\$".toRegex())
    }

    private fun isFileExtensionValid(text: String, extensions: List<String>): Boolean {
        return extensions.any { text.endsWith(it) }
    }

    private fun isMinimumAmountValid(value: String, minimumAmount: Int?): Boolean =
        safeLet(
            minimumAmount?.toBigInteger(),
            value.toBigIntegerOrNull()
        ) { min, current ->
            current >= min
        } ?: false

    private fun isMaximumAmountValid(value: String, maximumAmount: Int?): Boolean =
        safeLet(
            maximumAmount?.toBigInteger(),
            value.toBigIntegerOrNull()
        ) { max, current ->
            current <= max
        } ?: false
}

data class Form(val inputs: Map<List<Rule>, String>)
data class FormValidationResult(val isValid: Boolean, val message: String = "")
data class FormsValidationResult(val isValid: Boolean, val messages: List<String> = listOf())

sealed class Rule(val errorMsg: String) {
    data class ValidateNotEmpty(val errorMessage: String) : Rule(errorMessage)
    data class ValidateEmail(val errorMessage: String) : Rule(errorMessage)
    data class ValidatePhoneNumber(val errorMessage: String, val countryCode: String) :
        Rule(errorMessage)

    data class ValidateStringLength(
        val lengths: List<Int>,
        val errorMessage: String
    ) : Rule(errorMessage)

    data class ValidateStringMaxLength(
        val length: Int,
        val errorMessage: String
    ) : Rule(errorMessage)

    data class ValidateStringMinLength(
        val length: Int,
        val errorMessage: String
    ) : Rule(errorMessage)

    data class ValidateRegex(val regex: Regex, val errorMessage: String) : Rule(errorMessage)
    data class ContainsRegex(val regex: Regex, val errorMessage: String) : Rule(errorMessage)

    data class ValidateStringRange(
        val range: IntRange,
        val errorMessage: String
    ) : Rule(errorMessage)

    data class ValidateStringMatch(
        val stringToMatch: String,
        val errorMessage: String,
        val isCaseSensitive: Boolean = false
    ) : Rule(errorMessage)

    data class ValidateStringNotMatch(
        val stringToMatch: String,
        val errorMessage: String,
        val isCaseSensitive: Boolean = false
    ) : Rule(errorMessage)

    data class ValidateDuplicateCharacterNotInConsecutiveOrder(
        val maxTimesOfConsecutiveOrder: Int,
        val errorMessage: String
    ) : Rule(errorMessage)

    data class ValidateNumericNotInConsecutiveSequenceOrder(
        val minLength: Int,
        val errorMessage: String
    ) : Rule(errorMessage)

    data class StringShouldEndWithNumber(
        val errorMessage: String
    ) : Rule(errorMessage)

    data class ValidateFileSizeMaxSize(
        val size: Long,
        val errorMessage: String,
    ) : Rule(errorMessage)

    data class ValidateFileExtension(
        val supportedExtensions: List<String>,
        val errorMessage: String,
    ) : Rule(errorMessage)

    data class ValidateMinimumAmount(
        val minimumAmount: Int,
        val errorMessage: String,
    ) : Rule(errorMessage)


    data class ValidateMaximumAmount(
        val maximumAmount: Int,
        val errorMessage: String,
    ) : Rule(errorMessage)

    data class ValidateFileName(
        val errorMessage: String
    ) : Rule(errorMessage)
}