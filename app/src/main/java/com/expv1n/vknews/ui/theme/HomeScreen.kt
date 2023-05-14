package com.expv1n.vknews.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DismissDirection
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expv1n.vknews.MainViewModel
import com.expv1n.vknews.domain.FeedPost


@Composable
fun HomeScreen(
    viewModel: MainViewModel, paddingValues: PaddingValues
) {
    val screenState = viewModel.screenState.observeAsState(HomeScreenState.Initial)

    when (val currentState = screenState.value) {
        is HomeScreenState.Posts -> {
            FeedPost(
                viewModel = viewModel,
                paddingValues = paddingValues,
                posts = currentState.posts
            )
        }

        is HomeScreenState.Comments -> {
            CommentScreen(
                feedPost = currentState.feedPost,
                comments = currentState.comments,
                onBackPressed = {
                    viewModel.closeComments()
                }
            )
            BackHandler {
                viewModel.closeComments()
            }
        }

        HomeScreenState.Initial -> {

        }
    }

}

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
private fun FeedPost(
    viewModel: MainViewModel, paddingValues: PaddingValues, posts: List<FeedPost>
) {
    LazyColumn(
        modifier = Modifier.padding(paddingValues), contentPadding = PaddingValues(
            start = 8.dp, top = 8.dp, end = 8.dp, bottom = 36.dp
        ), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = posts, key = { it.id }) { feedPost ->
            val dismissState = rememberDismissState()
            if (dismissState.isDismissed(DismissDirection.EndToStart)) {
                viewModel.remove(feedPost)
            }
            SwipeToDismiss(
                modifier = Modifier.animateItemPlacement(),
                state = dismissState,
                background = {},
                directions = setOf(DismissDirection.EndToStart)
            ) {
                PostCard(modifier = Modifier.padding(8.dp),
                    feedPost = feedPost,
                    onViewsClickListener = { StatisticItem ->
                        viewModel.updateCount(feedPost, StatisticItem)
                    },
                    onSharesClickListener = { StatisticItem ->
                        viewModel.updateCount(feedPost, StatisticItem)
                    },
                    onCommentsClickListener = {
                        viewModel.showComments(feedPost)
                    },
                    onLikesClickListener = { StatisticItem ->
                        viewModel.updateCount(feedPost, StatisticItem)
                    })
            }
        }
    }
}