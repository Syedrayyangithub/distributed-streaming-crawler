
# Distributed AI RAG Search Engine

A high-performance, multi-threaded web crawling pipeline and semantic search engine built from the ground up using **Spring Boot (Java 17)**, **MongoDB**, **Redis**, and **Docker**. This project implements a local **Retrieval-Augmented Generation (RAG)** pipeline, computing high-dimensional vector embeddings locally via **Ollama** to synthesize contextualized search summaries complete with automated source references.

---

## 🚀 Key Architectural Features

* **Multi-Threaded Concurrent Ingestion:** Leverages a configurable, concurrent thread pool executor within a decoupled event-driven architecture to recursively parse web domains without thread avalanches or race conditions.
* **Distributed State Management:** Integrates a centralized **Redis** data layer to synchronize visited tracking states across crawl pipelines, eliminating redundant network overhead and preventing cyclic URL loops.
* **Hybrid Semantic & Keyword Search:** Implements a dual-layer data storage strategy in **MongoDB**. Captures traditional inverted-index token tokenization alongside dense, 384-dimensional vector coordinate properties (`all-minilm`).
* **Resilient Defensive Design:** Features an advanced multi-term regex search and title intersection fallback mechanism that ensures continuous RAG query fulfillment even when structural vector indexes are uninitialized.
* **Containerized Microservices:** Fully containerized and orchestrated via **Docker** and **Docker Compose**, establishing an isolated virtual bridge network to cleanly route data between the app container, database, cache, and LLM inference engine layers.

---

## 🛠️ Technology Stack & Dependencies

* **Backend Engine:** Spring Boot 3.3.x, Spring AI
* **Web Scraping:** Jsoup (HTML Parser)
* **Distributed Cache:** Redis (Lettuce Driver)
* **NoSQL Database:** MongoDB (MongoTemplate & Aggregation Pipelines)
* **AI & Embedding Models:** Ollama (`llama3.2:1b`, `all-minilm`)
* **DevOps Infrastructure:** Docker, Docker Compose, Alpine Linux Base Images

---

## 📦 System Architecture

The ecosystem splits operational tasks into four independent, isolated service layers:
1. **`spring_crawler_app`**: The core Java execution driver processing multi-threaded spider loops and hosting the Rest API gateway server on port `8081`.
2. **`crawler_mongodb`**: Stores crawled text payloads, word frequency token arrays, and generated vector coordinates persistently.
3. **`crawler_redis`**: Provides lightning-fast, atomic in-memory lookups to monitor and regulate visited URL states.
4. **`crawler_ollama`**: Runs local containerized AI inference tasks for semantic coordinates calculation and natural language synthesis text responses.

---

## ⚡ Getting Started & Deployment

### Prerequisites
* Ensure **Docker Desktop** is installed and running on your machine.
* Ensure **Java 17** and **Maven** are set up locally (if compiling outside the container).

### 1. Package the Application Artifact
Compile the Spring Boot source files into a production-ready `.jar` deployment binary using your IDE or terminal:

```bash
mvn clean package -DskipTests
```

### 2. Launch the Microservice Cluster

Spin up the entire coordinated container topology using Docker Compose:

```bash
docker compose up --build

```

### 3. Load the AI Model Weights

Because the containers are running in an isolated network sandbox, execute the following commands in a separate terminal window to pull the required AI models directly into the active Ollama engine container:

```bash
# Pull the vector embedding coordinate calculator
docker exec -it crawler_ollama ollama pull all-minilm

# Pull the core conversational LLM
docker exec -it crawler_ollama ollama pull llama3.2:1b

```

---

## 🔍 API Testing & Verification

Once the terminal logs confirm the services are up and connected, trigger a dynamic search query directly inside your web browser or via `curl`:

```text
http://localhost:8081/api/search?q=why is it a good habit to wake up early in the morning

```

### Expected Production Response Payload JSON

```json
{
  "timestamp": "2026-06-30T00:50:12.450+00:00",
  "query": "why is it a good habit to wake up early in the morning",
  "ai_overview": "According to verified crawled documents, early rising optimizes human productivity and biological circadian cycles... [Truncated LLM Synthesis Text]",
  "status": "SUCCESS",
  "results_count": 3,
  "source_references": [
    {
      "title": "Waking up early - Wikipedia",
      "url": "[https://en.wikipedia.org/wiki/Early_rising](https://en.wikipedia.org/wiki/Early_rising)"
    },
    {
      "title": "BBC News - Health and Wellbeing Portal",
      "url": "[https://www.bbc.com/news](https://www.bbc.com/news)"
    }
  ]
}

```

```

```