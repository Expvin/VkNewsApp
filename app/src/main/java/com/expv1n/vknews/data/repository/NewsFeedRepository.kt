package com.expv1n.vknews.data.repository

import android.app.Application
import com.expv1n.vknews.data.mapper.NewsFeedMapper
import com.expv1n.vknews.data.network.ApiFactory
import com.expv1n.vknews.domain.FeedPost
import com.expv1n.vknews.domain.PostComment
import com.expv1n.vknews.domain.StatisticItem
import com.expv1n.vknews.domain.StatisticType
import com.expv1n.vknews.extensions.mergeWith
import com.expv1n.vknews.domain.AuthState
import com.vk.api.sdk.VKPreferencesKeyValueStorage
import com.vk.api.sdk.auth.VKAccessToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn

class NewsFeedRepository(application: Application) {

    private val storage = VKPreferencesKeyValueStorage(application)
    private val token
        get() = VKAccessToken.restore(storage)

    private val nextDataNeededEvent = MutableSharedFlow<Unit>(replay = 1)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val refreshedListFlow = MutableSharedFlow<List<FeedPost>>()
    private val loadedListFlow = flow {
        nextDataNeededEvent.emit(Unit)
        nextDataNeededEvent.collect {
            val startFrom = nextFrom
            if (startFrom == null && feedPosts.isNotEmpty()) {
                emit(feedPosts)
                return@collect
            }
            val response = if (startFrom == null) {
                apiService.loadRecommendations(getAccessToken())
            } else {
                apiService.loadRecommendations(getAccessToken(), startFrom)
            }
            nextFrom = response.newsFeedContent.nextFrom
            val posts = mapper.mapResponseToPosts(response)
            _feedPosts.addAll(posts)
            emit(feedPosts)
        }
    }.retry {
            delay(TIME_OUT_MILLIS)
            true
        }

    private val checkAuthStateFlow = MutableSharedFlow<Unit>(replay = 1)
    val authStateFlow = flow {
        checkAuthStateFlow.emit(Unit)
        checkAuthStateFlow.collect {
            val currentToken = token
            val loggedIn = currentToken != null && currentToken.isValid
            val state = if (loggedIn) AuthState.Authorized else AuthState.NotAuthorized
            emit(state)
        }
    }.stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = AuthState.Initial
    )

    private val apiService = ApiFactory.apiService
    private val mapper = NewsFeedMapper()

    private val _feedPosts = mutableListOf<FeedPost>()
    private val feedPosts: List<FeedPost>
        get() = _feedPosts.toList()

    private var nextFrom: String? = null


    val recommendation: StateFlow<List<FeedPost>> = loadedListFlow
        .mergeWith(refreshedListFlow)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.Lazily,
            initialValue = feedPosts
        )

    suspend fun loadNextDate() {
        nextDataNeededEvent.emit(Unit)
    }

    suspend fun checkAuthState() {
        checkAuthStateFlow.emit(Unit)
    }

    suspend fun deletePost(feedPost: FeedPost) {
        apiService.ignorePost(
            token = getAccessToken(),
            ownerId = feedPost.communityId,
            postId = feedPost.id
        )
        _feedPosts.remove(feedPost)
        refreshedListFlow.emit(feedPosts)

    }

    fun getComments(feedPost: FeedPost): Flow<List<PostComment>> = flow {
        val comments = apiService.getComments(
            token = getAccessToken(),
            ownerId = feedPost.communityId,
            postId = feedPost.id
        )
        emit(mapper.mapResponseToComments(comments))
    }.retry {
        delay(TIME_OUT_MILLIS)
        true
    }

    private fun getAccessToken(): String {
        return token?.accessToken ?: throw IllegalStateException("Token is null")
    }

    suspend fun changeLikeStatus(feedPost: FeedPost) {
        val response = if (feedPost.isLiked) {
            apiService.deleteLike(
                token = getAccessToken(),
                ownerId = feedPost.communityId,
                postId = feedPost.id
            )
        } else {
            apiService.addLike(
                token = getAccessToken(),
                ownerId = feedPost.communityId,
                postId = feedPost.id
            )
        }
        val newLikesCount = response.likes.count
        val newStatistics = feedPost.statistics.toMutableList().apply {
            removeIf { it.type == StatisticType.LIKES }
            add(StatisticItem(type = StatisticType.LIKES, newLikesCount))
        }
        val newPost = feedPost.copy(statistics = newStatistics, isLiked = !feedPost.isLiked)
        val postIndex = _feedPosts.indexOf(feedPost)
        _feedPosts[postIndex] = newPost
        refreshedListFlow.emit(feedPosts)
    }

    companion object {
        private const val TIME_OUT_MILLIS = 5_000L
    }
}