package dto

data class PostAndComments (
    val post: Post,
    val author: List<Author>,
    val comments: List<CommentsAndAuthor>
)