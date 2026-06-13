# Distributed Intelligent Web Crawler Framework

An enterprise-grade, multi-threaded data ingestion engine built using **Spring Boot**, **Redis Cache**, and an asynchronous **Asynchronous Stream Pipeline** capable of high-throughput web scraping, global state management, and localized content text-mining metrics analytics.

## 🏗️ Architectural Layout

- **Concurrency Layer:** Leverages a thread-safe Java `ExecutorService` fixed-worker pool running concurrent runtime context execution tasks to maximize data fetch speeds.
- **Idempotent State Store:** Integrated with an in-memory **Redis Cache Cluster** using set logic queries (`crawler:visited:urls`) to maintain global deduplication state and eliminate infinite link-traversal loops.
- **Asynchronous Messaging Canal:** Implements a decoupled **Producer-Consumer Design Pattern** via Spring Event Multicasters, executing heavy processing tasks asynchronously on isolated worker contexts without blocking live web spider worker allocations.
- **Data-Mining Processor:** Implements regex tokenizers, punctuation clean-up utilities, and localized English stop-word dictionaries using Java Streams maps to analyze word frequencies on incoming payloads in real-time.
- **Observability Hub:** Instrumented with **Spring Boot Actuator** and **Micrometer Core** exposing Prometheus-ready REST endpoints (`/actuator/metrics`) tracking live JVM thread allocations and customized transaction counter velocities across the application workspace.

---

## 🛠️ Technology Stack & Dependencies

* **Core Platform:** Java (JDK 17+) & Spring Boot Web Core
* **Distributed Storage:** Embedded Redis Cache Cluster Data Pool
* **Data Scraper:** JSoup HTML Document Processing Parser Core
* **Pipeline Infrastructure:** Spring Framework Async Task Executors
* **Production Observability:** Spring Boot Actuator, Micrometer Core Meter Registries

---

## ⚙️ Application Analytics Tracking

The engine exposes real-time structural performance tracking telemetry directly over REST interfaces:

- **Cluster Health Status:** `GET http://localhost:8081/actuator/health`
- **Active JVM Container Threads:** `GET http://localhost:8081/actuator/metrics/jvm.threads.live`
- **Custom Scrape Velocity Metrics:** `GET http://localhost:8081/actuator/metrics/crawler.pages.scraped`

---

## 📊 Live Sample Analytical Execution Logs

```text
🍃 Internal In-Memory Redis Server started successfully on port 6379!
🚀 Initializing Distributed Redis-Backed Streaming Engine...
🧵 Thread [pool-3-thread-1] is fetching: [https://en.wikipedia.org/wiki/Main_Page](https://en.wikipedia.org/wiki/Main_Page)
✅ Successfully scraped: Wikipedia, the free encyclopedia

📡 [Pipeline Consumer] Intercepted stream payload for: Wikipedia, the free encyclopedia
📊 --- CONTENT ANALYSIS REPORT ---
🔗 URL:          [https://en.wikipedia.org/wiki/Main_Page](https://en.wikipedia.org/wiki/Main_Page)
🏷️ TITLE:        Wikipedia, the free encyclopedia
🔤 TOTAL WORDS: 1502
🔥 TOP KEYWORDS INDICES:
   ▪️ wikipedia -> appeared 15 times
   ▪️ page -> appeared 8 times
   ▪️ wikimedia -> appeared 7 times
---------------------------------

🏁 Engine Finished! Total unique pages saved in Redis cache: 2