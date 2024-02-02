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

package eu.europa.ec.commonfeature.ui.loading

import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.config.NavigationType
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.Screen

data class State(
    val error: ContentErrorConfig? = null,
    val screenTitle: String,
    val screenSubtitle: String,
) : ViewState

sealed class Event : ViewEvent {
    data object DoWork : Event()
    data object GoBack : Event()
    data object DismissError : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data class SwitchScreen(val screenRoute: String) : Navigation()
        data class PopBackStackUpTo(
            val screenRoute: String,
            val inclusive: Boolean
        ) : Navigation()
    }
}

abstract class LoadingViewModel : MviViewModel<Event, State, Effect>() {

    /**
     * The title of the re-usable [LoadingScreen] .
     */
    abstract fun getTitle(): String

    /**
     * The subtitle of the re-usable [LoadingScreen] .
     */
    abstract fun getSubtitle(): String

    /**
     * The [Screen] the user will be navigated to:
     * 1. If they press the "X" button--cancel the [LoadingScreen] .
     * 2. If they press the "X" button of the Error screen (should any Error happen).
     */
    abstract fun getPreviousScreen(): Screen

    /**
     * The [Screen] which opened the re-usable [LoadingScreen] .
     * It will be erased from the back-stack when user successfully moves to the next step [Screen].
     */
    abstract fun getCallerScreen(): Screen

    /**
     * Used to perform any kind of work the calling viewModel needs to.
     * Gets called once upon initialization of the [LoadingScreen] +
     * each time the user presses "Try again" in its Error screen.
     */
    abstract fun doWork()

    override fun setInitialState(): State {
        return State(
            screenTitle = getTitle(),
            screenSubtitle = getSubtitle(),
            error = null
        )
    }

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.DoWork -> doWork()

            is Event.GoBack -> {
                setState {
                    copy(error = null)
                }
                doNavigation(NavigationType.Pop)
            }

            is Event.DismissError -> {
                setState {
                    copy(error = null)
                }
            }
        }
    }

    protected fun setErrorState(errorMsg: String) {
        setState {
            copy(
                error = ContentErrorConfig(
                    errorSubTitle = errorMsg,
                    onCancel = { setEvent(Event.GoBack) },
                    onRetry = { setEvent(Event.DoWork) }
                )
            )
        }
    }

    protected fun doNavigation(navigationType: NavigationType) {
        when (navigationType) {
            is NavigationType.PushScreen -> {
                setEffect {
                    Effect.Navigation.SwitchScreen(navigationType.screen.screenRoute)
                }
            }

            is NavigationType.Pop -> {
                setEffect {
                    Effect.Navigation.PopBackStackUpTo(
                        screenRoute = getPreviousScreen().screenRoute,
                        inclusive = false
                    )
                }
            }

            is NavigationType.PopTo -> {
                setEffect {
                    Effect.Navigation.PopBackStackUpTo(
                        screenRoute = navigationType.screen.screenRoute,
                        inclusive = false
                    )
                }
            }

            is NavigationType.Deeplink -> {}

            is NavigationType.PushRoute -> setEffect {
                Effect.Navigation.SwitchScreen(navigationType.route)
            }
        }
    }
}