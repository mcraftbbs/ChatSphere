package cn.sarskin.ChatSphere.client.link;

/** Parsed link metadata for one URL; every field may be empty when the page offers nothing. */
public record LinkPreview(String url, String title, String description, String imageUrl, String site) {

    public boolean empty() {
        return title.isEmpty() && description.isEmpty() && imageUrl.isEmpty();
    }
}
