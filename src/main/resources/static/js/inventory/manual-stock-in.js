(() => {
    const byId = id => document.getElementById(id);
    const search = byId("productSearch");
    const results = byId("searchResults");
    const status = byId("searchStatus");
    const form = byId("manualStockInForm");
    const quantity = byId("quantity");
    const reason = byId("reason");
    const preview = byId("preview");
    let selected = null;
    let timer;
    let version = 0;

    search.addEventListener("input", () => {
        clearTimeout(timer);
        const requestVersion = ++version;
        const keyword = search.value.trim();
        results.replaceChildren();
        status.textContent = keyword ? "Searching…" : "Type to search existing active products.";
        if (!keyword) return;
        timer = setTimeout(async () => {
            try {
                const response = await fetch(search.dataset.lookupUrl + "?keyword=" + encodeURIComponent(keyword));
                if (!response.ok) throw new Error("Search failed");
                const products = await response.json();
                if (requestVersion !== version) return;
                status.textContent = products.length ? "Select a product." : "No matching active products found.";
                products.forEach(product => {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.className = "list-group-item list-group-item-action";
                    button.textContent = [product.name, product.size, product.barcode || "No barcode",
                        "Stock: " + product.stockQuantity + " " + (product.unitName || "")].filter(Boolean).join(" · ");
                    button.addEventListener("click", () => select(product));
                    results.appendChild(button);
                });
            } catch (error) {
                if (requestVersion === version) status.textContent = "Unable to search products. Please try again.";
            }
        }, 300);
    });

    function select(product) {
        ++version;
        selected = product;
        form.reset();
        byId("productId").value = product.id;
        byId("productName").textContent = product.name;
        byId("productDetails").textContent = [product.size, product.unitName].filter(Boolean).join(" / ");
        byId("productBarcode").textContent = product.barcode || "—";
        byId("currentStock").textContent = product.stockQuantity;
        byId("productUnit").textContent = product.unitName || "";
        byId("confirmButton").disabled = false;
        results.replaceChildren();
        search.value = "";
        status.textContent = "Product selected.";
        reason.setCustomValidity("");
        form.hidden = false;
        updatePreview();
        quantity.focus();
    }

    function updatePreview() {
        quantity.setCustomValidity("");
        preview.hidden = true;
        if (!selected || quantity.value === "") return;
        const received = Number(quantity.value);
        const before = selected.stockQuantity;
        const after = before + received;
        if (!Number.isInteger(received) || received <= 0 || !Number.isInteger(before)
                || before < 0 || after > 2147483647) {
            quantity.setCustomValidity("Enter a positive whole-number quantity within the supported stock limit.");
            return;
        }
        preview.textContent = "The system stock will increase from " + before + " to " + after + ".";
        preview.hidden = false;
    }

    quantity.addEventListener("input", updatePreview);
    reason.addEventListener("input", () => reason.setCustomValidity(
        reason.value.trim() ? "" : "Enter a reason or note."));
    byId("changeProduct").addEventListener("click", () => {
        selected = null;
        form.hidden = true;
        byId("productId").value = "";
        preview.hidden = true;
        search.focus();
    });
    form.addEventListener("submit", event => {
        updatePreview();
        reason.setCustomValidity(reason.value.trim() ? "" : "Enter a reason or note.");
        if (!selected || !form.reportValidity()) {
            event.preventDefault();
            return;
        }
        byId("confirmButton").disabled = true;
    });
    window.addEventListener("pageshow", () => { byId("confirmButton").disabled = false; });
})();
