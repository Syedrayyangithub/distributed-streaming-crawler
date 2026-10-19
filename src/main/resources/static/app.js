const luckyQueries = [
    "why is it a good habit to wake up early in the morning",
    "how does circadian rhythm affect sleep",
    "what is sleep hygiene",
    "why do habits form",
    "how does sleep improve learning"
];

const homeView = document.getElementById("home-view");
const resultsView = document.getElementById("results-view");
const homeForm = document.getElementById("home-form");
const resultsForm = document.getElementById("results-form");
const homeInput = document.getElementById("home-q");
const resultsInput = document.getElementById("results-q");
const luckyBtn = document.getElementById("lucky-btn");
const loading = document.getElementById("loading");
const errorBox = document.getElementById("error-box");
const aiCard = document.getElementById("ai-card");
const aiOverview = document.getElementById("ai-overview");
const aiSources = document.getElementById("ai-sources");
const webResults = document.getElementById("web-results");
const resultsList = document.getElementById("results-list");
const statusLine = document.getElementById("status-line");
const homeIndexStatus = document.getElementById("home-index-status");

let activeMode = "all";
let overviewSource = null;
let searchGeneration = 0;

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function renderAiText(text) {
    return escapeHtml(text || "")
        .replace(/\*\*(.+?)\*\*/g, "<strong>$1</strong>")
        .replace(/\n/g, "<br>");
}

function decodeToken(raw) {
    if (!raw) {
        return "";
    }
    try {
        const parsed = JSON.parse(raw);
        if (parsed && typeof parsed.t === "string") {
            return parsed.t;
        }
    } catch {
        // Fall through for plain-text tokens.
    }
    return raw;
}

function hostname(url) {
    try {
        return new URL(url).hostname.replace(/^www\./, "");
    } catch {
        return url;
    }
}

function showHome() {
    closeOverviewStream();
    homeView.classList.remove("hidden");
    resultsView.classList.add("hidden");
    document.title = "Crawlr";
}

function showResultsShell(query) {
    homeView.classList.add("hidden");
    resultsView.classList.remove("hidden");
    resultsInput.value = query;
    document.title = `${query} - Crawlr`;
}

function setMode(mode) {
    activeMode = mode;
    document.querySelectorAll(".tab").forEach((tab) => {
        tab.classList.toggle("active", tab.dataset.mode === mode);
    });
    resultsView.classList.toggle("ai-only", mode === "ai");
}

function closeOverviewStream() {
    if (overviewSource) {
        overviewSource.close();
        overviewSource = null;
    }
}

function renderSources(sources) {
    aiSources.innerHTML = sources.map((source) => {
        const title = escapeHtml(source.title || hostname(source.url));
        const url = escapeHtml(source.url || "#");
        const initial = escapeHtml((source.title || "W").trim().charAt(0).toUpperCase());
        return `<a class="source-chip" href="${url}" target="_blank" rel="noopener noreferrer">
            <span class="favicon">${initial}</span>
            <span>${title}</span>
        </a>`;
    }).join("");

    if (!sources.length) {
        webResults.classList.add("hidden");
        resultsList.innerHTML = "";
        return;
    }
    resultsList.innerHTML = sources.map((source) => {
        const title = escapeHtml(source.title || source.url || "Untitled");
        const url = escapeHtml(source.url || "#");
        const snippet = escapeHtml(source.snippet || "Crawled page from the local index.");
        return `<article class="result-item">
            <a class="result-url" href="${url}" target="_blank" rel="noopener noreferrer">${escapeHtml(hostname(source.url))}</a>
            <h3 class="result-title"><a href="${url}" target="_blank" rel="noopener noreferrer">${title}</a></h3>
            <p class="result-snippet">${snippet}</p>
        </article>`;
    }).join("");
    webResults.classList.remove("hidden");
}

function streamOverview(query, generation) {
    closeOverviewStream();
    aiCard.classList.remove("hidden");
    aiOverview.innerHTML = "<em>Writing overview…</em>";
    let text = "";
    const source = new EventSource(`/api/overview?q=${encodeURIComponent(query)}`);
    overviewSource = source;

    source.addEventListener("token", (event) => {
        if (generation !== searchGeneration) {
            return;
        }
        text += decodeToken(event.data);
        aiOverview.innerHTML = renderAiText(text);
    });
    source.addEventListener("done", () => {
        source.close();
        if (overviewSource === source) {
            overviewSource = null;
        }
        if (!text && generation === searchGeneration) {
            aiOverview.innerHTML = renderAiText("No overview was generated.");
        }
    });
    source.onerror = () => {
        source.close();
        if (overviewSource === source) {
            overviewSource = null;
        }
        if (!text && generation === searchGeneration) {
            aiOverview.innerHTML = renderAiText("AI overview is unavailable.");
        }
    };
}

async function runSearch(query, pushUrl) {
    const q = (query || "").trim();
    if (!q) {
        showHome();
        return;
    }

    const generation = ++searchGeneration;
    closeOverviewStream();
    showResultsShell(q);
    loading.classList.remove("hidden");
    errorBox.classList.add("hidden");
    aiCard.classList.add("hidden");
    webResults.classList.add("hidden");
    statusLine.textContent = "";

    if (pushUrl) {
        const next = `/search?q=${encodeURIComponent(q)}`;
        history.pushState({ q }, "", next);
    }

    const started = performance.now();
    try {
        const response = await fetch(`/api/search?q=${encodeURIComponent(q)}`);
        if (!response.ok) {
            throw new Error(`Search failed (${response.status})`);
        }
        const data = await response.json();
        if (generation !== searchGeneration) {
            return;
        }
        const elapsed = ((performance.now() - started) / 1000).toFixed(2);
        const sources = Array.isArray(data.source_references) ? data.source_references : [];
        const count = data.results_count ?? sources.length;
        statusLine.textContent = count
            ? `About ${count} results (${elapsed} seconds)`
            : `No results found (${elapsed} seconds)`;
        renderSources(sources);
        loading.classList.add("hidden");
        if (count) {
            streamOverview(q, generation);
        }
    } catch (error) {
        if (generation !== searchGeneration) {
            return;
        }
        errorBox.textContent = error.message || "Search is unavailable.";
        errorBox.classList.remove("hidden");
        loading.classList.add("hidden");
    }
}

function queryFromUrl() {
    return new URLSearchParams(window.location.search).get("q") || "";
}

homeForm.addEventListener("submit", (event) => {
    event.preventDefault();
    runSearch(homeInput.value, true);
});

resultsForm.addEventListener("submit", (event) => {
    event.preventDefault();
    runSearch(resultsInput.value, true);
});

luckyBtn.addEventListener("click", () => {
    const query = luckyQueries[Math.floor(Math.random() * luckyQueries.length)];
    homeInput.value = query;
    runSearch(query, true);
});

document.querySelectorAll(".tab").forEach((tab) => {
    tab.addEventListener("click", () => setMode(tab.dataset.mode));
});

window.addEventListener("popstate", () => {
    const q = queryFromUrl();
    if (q) {
        runSearch(q, false);
    } else {
        showHome();
    }
});

async function loadIndexStatus() {
    try {
        const response = await fetch("/api/status");
        if (!response.ok) {
            return;
        }
        const data = await response.json();
        const count = data.indexed_documents ?? 0;
        homeIndexStatus.textContent = `${count} pages indexed`;
        if (!count) {
            homeIndexStatus.textContent = "Index is empty — open /api/crawl to ingest pages";
        }
    } catch {
        homeIndexStatus.textContent = "Index status unavailable";
    }
}

const initial = queryFromUrl();
if (initial) {
    homeInput.value = initial;
    runSearch(initial, false);
} else {
    showHome();
}
loadIndexStatus();
