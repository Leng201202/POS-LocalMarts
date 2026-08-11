const stockFileInput = document.getElementById("stockFile");
const uploadZone = document.getElementById("uploadZone");
const fileInfo = document.getElementById("fileInfo");
const fileName = document.getElementById("fileName");
const fileSize = document.getElementById("fileSize");
const removeFileButton = document.getElementById("removeFileButton");
const uploadButton = document.getElementById("uploadButton");

const allowedExtensions = ["xlsx"];
const maximumFileSize = 10 * 1024 * 1024;

stockFileInput.addEventListener("change", function () {
    const file = stockFileInput.files[0];

    if (file) {
        selectFile(file);
    }
});

uploadZone.addEventListener("dragover", function (event) {
    event.preventDefault();
    uploadZone.classList.add("drag-over");
});

uploadZone.addEventListener("dragleave", function () {
    uploadZone.classList.remove("drag-over");
});

uploadZone.addEventListener("drop", function (event) {
    event.preventDefault();
    uploadZone.classList.remove("drag-over");

    const file = event.dataTransfer.files[0];

    if (!file) {
        return;
    }

    const dataTransfer = new DataTransfer();
    dataTransfer.items.add(file);
    stockFileInput.files = dataTransfer.files;

    selectFile(file);
});

removeFileButton.addEventListener("click", function () {
    stockFileInput.value = "";

    fileInfo.style.display = "none";
    uploadButton.disabled = true;
});

function selectFile(file) {
    const extension = file.name
        .split(".")
        .pop()
        .toLowerCase();

    if (!allowedExtensions.includes(extension)) {
        alert("Only .xlsx files are allowed.");
        stockFileInput.value = "";
        return;
    }

    if (file.size > maximumFileSize) {
        alert("File size must not exceed 10 MB.");
        stockFileInput.value = "";
        return;
    }

    fileName.textContent = file.name;
    fileSize.textContent = formatFileSize(file.size);

    fileInfo.style.display = "block";
    uploadButton.disabled = false;
}

function formatFileSize(bytes) {
    if (bytes < 1024) {
        return bytes + " bytes";
    }

    if (bytes < 1024 * 1024) {
        return (bytes / 1024).toFixed(1) + " KB";
    }

    return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}
