    const productSearch = document.getElementById("productSearch");
    const searchResults = document.getElementById("searchResults");
    const noResults = document.getElementById("noResults");

    const selectedProductCard =
    document.getElementById("selectedProductCard");

    const clearProductButton =
    document.getElementById("clearProductButton");

    const productIdInput =
    document.getElementById("productId");

    const selectedProductName =
    document.getElementById("selectedProductName");

    const selectedProductSize =
    document.getElementById("selectedProductSize");

    const selectedProductBarcode =
    document.getElementById("selectedProductBarcode");

    const selectedCurrentStock =
    document.getElementById("selectedCurrentStock");

    const selectedUnit =
    document.getElementById("selectedUnit");

    const actualStockInput =
    document.getElementById("actualStock");

    const stockDifference =
    document.getElementById("stockDifference");

    const adjustmentPreview =
    document.getElementById("adjustmentPreview");

    const previewBeforeStock =
    document.getElementById("previewBeforeStock");

    const previewAfterStock =
    document.getElementById("previewAfterStock");

    const adjustmentType = document.getElementById("adjustmentType");
    adjustmentType.addEventListener("change", function () {
        const correction = adjustmentType.value === "ADJUSTMENT";
        document.getElementById("amountLabel").textContent = correction ? "Actual Physical Stock" : "Quantity";
        document.getElementById("amountHelp").textContent = correction
            ? "Enter the actual quantity counted in the shop."
            : "Enter the quantity to remove from sellable stock.";
        actualStockInput.min = correction ? "0" : "1";
        actualStockInput.value = "";
        actualStockInput.dispatchEvent(new Event("input"));
    });

    let selectedProduct = null;
    let searchVersion = 0;
    let searchTimeout = null;

    productSearch.addEventListener("input", function () {
    clearTimeout(searchTimeout);
    const version = ++searchVersion;

    const keyword = productSearch.value.trim();

    searchResults.innerHTML = "";
    noResults.classList.add("d-none");

    if (keyword.length === 0) {
    return;
}

    searchTimeout = setTimeout(async function () {
    try {
    const response = await fetch(
    "/inventory/adjustment/products?keyword=" +
    encodeURIComponent(keyword)
    );

    if (!response.ok) {
    throw new Error("Unable to search products");
}

    const products = await response.json();

    if (version !== searchVersion) return;
    renderSearchResults(products);
} catch (error) {
    if (version !== searchVersion) return;
    searchResults.innerHTML = `
                    <div class="alert alert-danger mb-0">
                        Unable to search products. Please try again.
                    </div>
                `;
}
}, 300);
});

    function renderSearchResults(products) {
        searchResults.innerHTML = "";

        if (!products || products.length === 0) {
            noResults.classList.remove("d-none");
            return;
        }

        noResults.classList.add("d-none");

        products.forEach(function (product) {
        const item = document.createElement("button");

        item.type = "button";
        item.className =
        "list-group-item list-group-item-action search-result";

        const sizeText =
        product.size ? ` - ${product.size}` : "";

        const barcodeText =
        product.barcode ? product.barcode : "No barcode";

        const unitText =
        product.unitName ? product.unitName : "";

        item.innerHTML = `
                        <div class="d-flex justify-content-between align-items-start">
                            <div>
                                <div class="fw-semibold">
                                    ${escapeHtml(product.name)}${escapeHtml(sizeText)}
                                </div>
        
                                <small class="text-muted">
                                    Barcode: ${escapeHtml(barcodeText)}
                                </small>
                            </div>
        
                            <span class="badge bg-primary-subtle text-primary">
                                Stock: ${product.stockQuantity} ${escapeHtml(unitText)}
                            </span>
                        </div>
                    `;

            item.addEventListener("click", function () {
            selectProduct(product);
            });

    searchResults.appendChild(item);
        });
    }

    function selectProduct(product) {
        selectedProduct = product;
        ++searchVersion;

        productIdInput.value = product.id;
        selectedProductName.textContent = product.name;
        selectedProductSize.textContent = product.size || "-";
        selectedProductBarcode.textContent =
        product.barcode || "-";

        selectedCurrentStock.textContent =
        product.stockQuantity ?? 0;

        selectedUnit.textContent =
        product.unitName || "";

        actualStockInput.value = "";
        stockDifference.textContent = "-";
        stockDifference.className = "difference-zero";

        adjustmentPreview.classList.add("d-none");

        selectedProductCard.style.display = "block";

        searchResults.innerHTML = "";
        noResults.classList.add("d-none");
        productSearch.value = "";

        actualStockInput.focus();
    }

        clearProductButton.addEventListener("click", function () {
        selectedProduct = null;

        productIdInput.value = "";
        actualStockInput.value = "";

        selectedProductCard.style.display = "none";
        adjustmentPreview.classList.add("d-none");

        productSearch.focus();
    });

        actualStockInput.addEventListener("input", function () {
        actualStockInput.setCustomValidity("");
        if (!selectedProduct || actualStockInput.value === "") {
        stockDifference.textContent = "-";
        stockDifference.className = "difference-zero";
        adjustmentPreview.classList.add("d-none");
        return;
    }

        const currentStock =
        Number(selectedProduct.stockQuantity ?? 0);

        const actualStock =
        Number(actualStockInput.value);

        const afterStock = adjustmentType.value === "ADJUSTMENT"
            ? actualStock : currentStock - actualStock;
        const valid = Number.isInteger(actualStock) && actualStock >= Number(actualStockInput.min)
            && actualStock <= 2147483647 && afterStock >= 0 && afterStock !== currentStock;
        actualStockInput.setCustomValidity(valid ? "" : "Enter a valid quantity that changes stock without making it negative.");
        if (!valid) {
            stockDifference.textContent = "-";
            adjustmentPreview.classList.add("d-none");
            return;
        }
        const difference = afterStock - currentStock;

        stockDifference.textContent =
        difference > 0
        ? `+${difference}`
        : `${difference}`;

        if (difference > 0) {
        stockDifference.className = "difference-positive";
    } else if (difference < 0) {
        stockDifference.className = "difference-negative";
    } else {
        stockDifference.className = "difference-zero";
    }

        previewBeforeStock.textContent = currentStock;
        previewAfterStock.textContent = afterStock;

        adjustmentPreview.classList.remove("d-none");
    });

    function escapeHtml(value) {
        return String(value)
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
    }

    actualStockInput.form.addEventListener("submit", function (event) {
        actualStockInput.dispatchEvent(new Event("input"));
        if (!selectedProduct || !this.reportValidity()) {
            event.preventDefault();
            return;
        }
        this.querySelector('button[type="submit"]').disabled = true;
    });
