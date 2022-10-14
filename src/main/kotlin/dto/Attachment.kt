package dto

data class Attachment(
    val url: String,
    val description: String,
    val type: AttachmentImage,
)
enum class AttachmentImage {
    IMAGE
}
