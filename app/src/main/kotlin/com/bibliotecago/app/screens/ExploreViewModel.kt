package com.bibliotecago.app.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bibliotecago.app.AppContainer
import com.bibliotecago.catalog.OpdsFeed
import com.bibliotecago.catalog.OpdsNavigation
import com.bibliotecago.core.model.Book
import com.bibliotecago.server.ConnectionProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface ExploreState {
    data object Loading : ExploreState
    data class Failed(val problem: ConnectionProblem) : ExploreState
    data class Content(
        val navigation: List<OpdsNavigation>,
        val books: List<Book>,
        val hasMore: Boolean,
        val moreProblem: ConnectionProblem? = null,
    ) : ExploreState
}

/**
 * Un feed OPDS cualquiera: índice de letras, lista de autores, series o
 * etiquetas, o los libros de uno de ellos (RF-03). Pagina igual que la biblioteca.
 */
class ExploreViewModel(private val app: AppContainer, private val href: String) : ViewModel() {

    var state by mutableStateOf<ExploreState>(ExploreState.Loading)
        private set
    var downloaded by mutableStateOf<Set<String>>(emptySet())
        private set

    val coverBase get() = app.coverBase

    private var nextHref: String? = null
    private var job: Job? = null

    init {
        reload()
        viewModelScope.launch { app.downloadedBookIds().collect { downloaded = it } }
    }

    fun reload() {
        job?.cancel()
        state = ExploreState.Loading
        job = viewModelScope.launch {
            state = try {
                accept(app.catalog.feed(href), previous = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ExploreState.Failed(ConnectionProblem.fromException(e))
            }
        }
    }

    fun loadMore() {
        val current = state as? ExploreState.Content ?: return
        val next = nextHref ?: return
        if (job?.isActive == true) return
        state = current.copy(moreProblem = null)
        job = viewModelScope.launch {
            state = try {
                accept(app.catalog.feed(next), previous = current)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                current.copy(moreProblem = ConnectionProblem.fromException(e))
            }
        }
    }

    private fun accept(feed: OpdsFeed, previous: ExploreState.Content?): ExploreState.Content {
        nextHref = feed.nextHref
        app.books.putAll(feed.books)
        return ExploreState.Content(
            navigation = (previous?.navigation.orEmpty() + feed.navigation).distinctBy { it.href },
            books = (previous?.books.orEmpty() + feed.books).distinctBy { it.id },
            hasMore = feed.nextHref != null,
        )
    }
}
