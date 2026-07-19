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

    let selectedProduct = null;
    let searchTimeout = null;

    productSearch.addEventListener("input", function () {
    clearTimeout(searchTimeout);

    const keyword = productSearch.value.trim();

    searchResults.innerHTML = "";
    noResults.classList.add("d-none");

    if (keyword.length === 0) {
    return;
}

    searchTimeout = setTimeout(async function () {
    try {
    const response = await fetch(
    "/products/search?keyword=" +
    encodeURIComponent(keyword)
    );

    if (!response.ok) {
    throw new Error("Unable to search products");
}

    const products = await response.json();

    renderSearchResults(products);
} catch (error) {
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

        const difference =
        actualStock - currentStock;

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
        previewAfterStock.textContent = actualStock;

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
