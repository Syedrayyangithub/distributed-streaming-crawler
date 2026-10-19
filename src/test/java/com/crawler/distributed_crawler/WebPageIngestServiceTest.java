package com.crawler.distributed_crawler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebPageIngestServiceTest {

    @Mock
    private WebPageRepository webPageRepository;

    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void upsertsByUrlAndStillSavesWhenEmbeddingFails() {
        WebPageDocument existing = new WebPageDocument();
        existing.setId("doc-1");
        existing.setUrl("https://en.wikipedia.org/wiki/Laptop");
        existing.setTitle("Old title");

        when(webPageRepository.findByUrl("https://en.wikipedia.org/wiki/Laptop"))
                .thenReturn(Optional.of(existing));
        when(embeddingModel.embed(any(String.class))).thenThrow(new RuntimeException("ollama down"));
        when(webPageRepository.save(any(WebPageDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WebPageIngestService ingestService = new WebPageIngestService(webPageRepository, embeddingModel);
        WebPageDocument saved = ingestService.ingest(
                "https://en.wikipedia.org/wiki/Laptop",
                "Laptop - Wikipedia",
                "A laptop is a portable computer used on a desk or a lap."
        );

        ArgumentCaptor<WebPageDocument> captor = ArgumentCaptor.forClass(WebPageDocument.class);
        verify(webPageRepository).save(captor.capture());
        WebPageDocument persisted = captor.getValue();

        assertEquals("doc-1", persisted.getId());
        assertEquals("Laptop - Wikipedia", persisted.getTitle());
        assertTrue(persisted.getEmbedding().isEmpty());
        assertTrue(persisted.getWordFrequencies().containsKey("laptop"));
        assertEquals(saved.getId(), persisted.getId());
    }
}
