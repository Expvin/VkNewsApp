package com.expv1n.vknews.domain.usecases

import com.expv1n.vknews.domain.FeedPost
import com.expv1n.vknews.domain.repository.NewsFeedRepository
import javax.inject.Inject

class DeletePostUseCase @Inject constructor(
    private val repository: NewsFeedRepository
) {

    suspend operator fun invoke(feedPost: FeedPost) = repository.deletePost(feedPost)
}