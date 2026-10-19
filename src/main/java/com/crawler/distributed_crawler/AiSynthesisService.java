package com.crawler.distributed_crawler;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class AiSynthesisService {

    private final ChatClient chatClient;

    public AiSynthesisService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String synthesizeAnswer(String fullUserPrompt, List<WebPageDocument> sourceDocs) {
        StringBuilder out = new StringBuilder();
        streamAnswer(fullUserPrompt, sourceDocs, out::append);
        return out.toString();
    }

    public void streamAnswer(String fullUserPrompt, List<WebPageDocument> sourceDocs, Consumer<String> onToken) {
        if (sourceDocs == null || sourceDocs.isEmpty()) {
            onToken.accept("No crawled pages matched this query, so there are no source links to show yet.");
            return;
        }

        String contextPayload = sourceDocs.stream()
                .limit(2)
                .map(doc -> {
                    String shortText = doc.getRawText();
                    if (shortText != null && shortText.length() > 350) {
                        shortText = shortText.substring(0, 350) + "...";
                    }
                    return "Title: " + doc.getTitle() + "\nURL: " + doc.getUrl() + "\n" + shortText;
                })
                .collect(Collectors.joining("\n---\n"));

        String systemPrompt = """
            Answer in at most 4 short sentences using ONLY the sources below.
            Do not invent citations. If sources are weak, say that.
            
            Sources:
            %s
            """.formatted(contextPayload);

        try {
            chatClient.prompt()
                    .system(systemPrompt)
                    .user(fullUserPrompt)
                    .stream()
                    .content()
                    .toStream()
                    .forEach(token -> {
                        if (token != null && !token.isEmpty()) {
                            onToken.accept(token);
                        }
                    });
        } catch (Exception streamError) {
            try {
                String full = chatClient.prompt()
                        .system(systemPrompt)
                        .user(fullUserPrompt)
                        .call()
                        .content();
                if (full != null) {
                    onToken.accept(full);
                }
            } catch (Exception e) {
                onToken.accept("Retrieved " + sourceDocs.size() + " source page(s), but the chat model is unavailable: "
                        + e.getMessage());
            }
        }
    }
}
