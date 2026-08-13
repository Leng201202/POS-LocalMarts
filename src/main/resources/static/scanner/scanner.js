(function () {
    "use strict";

    // ================= CONFIG =================

    const DUPLICATE_COOLDOWN_MS = 1500;
    const FEEDBACK_DISPLAY_MS = 1400;

    // Built lazily (not at parse time) so a blocked/failed CDN load can't
    // crash this entire script before the Start/Stop button handlers attach.
    function getSupportedFormats() {
        return [
            ZXing.BarcodeFormat.EAN_13,
            ZXing.BarcodeFormat.EAN_8,
            ZXing.BarcodeFormat.UPC_A,
            ZXing.BarcodeFormat.UPC_E,
            ZXing.BarcodeFormat.CODE_128,
            ZXing.BarcodeFormat.QR_CODE
        ];
    }

    // ================= DOM =================

    const app = document.getElementById("scannerApp");
    const token = app.dataset.token;

    const connectionStatus = document.getElementById("connectionStatus");
    const connectionStatusText = document.getElementById("connectionStatusText");

    const cameraViewport = document.getElementById("cameraViewport");
    const cameraPreview = document.getElementById("cameraPreview");
    const cameraOverlay = document.getElementById("cameraOverlay");
    const cameraIdleState = document.getElementById("cameraIdleState");

    const lastScanValue = document.getElementById("lastScanValue");

    const feedbackBanner = document.getElementById("feedbackBanner");
    const feedbackIcon = document.getElementById("feedbackIcon");
    const feedbackText = document.getElementById("feedbackText");

    const scannerStatusText = document.getElementById("scannerStatusText");

    const startButton = document.getElementById("startButton");
    const stopButton = document.getElementById("stopButton");
    const torchButton = document.getElementById("torchButton");
    const torchButtonLabel = document.getElementById("torchButtonLabel");

    // ================= STATE =================

    let stream = null;
    let videoTrack = null;
    let codeReader = null;
    let scanControls = null;
    let torchOn = false;
    let audioCtx = null;

    let lastBarcode = null;
    let lastScanTime = 0;
    let feedbackTimer = null;
    let cameraAvailable = true;

    // ================= CONNECTION STATUS =================

    function setConnectionState(state, message) {
        connectionStatus.className = "connection-status connection-status-" + state;
        connectionStatusText.textContent = message;
    }

    async function joinSession() {
        setConnectionState("pending", "Connecting...");

        try {
            const response = await fetch("/api/scanner/session/" + token + "/connect", {
                method: "POST"
            });

            if (!response.ok) {
                setConnectionState("disconnected", "Session invalid");
                setScannerStatus("This scanner session is no longer valid. Rescan the QR code from the POS.");
                startButton.disabled = true;
                return;
            }

            setConnectionState("connected", "Connected ✓");
            if (cameraAvailable) {
                setScannerStatus("Ready to scan");
            }
        } catch (error) {
            console.error(error);
            setConnectionState("disconnected", "Disconnected");
            setScannerStatus("Unable to reach LocalMart POS. Check your Wi-Fi connection.");
        }
    }

    // Best-effort: tell the POS we're leaving when the tab is closed/backgrounded.
    document.addEventListener("visibilitychange", () => {
        if (document.visibilityState === "hidden" && navigator.sendBeacon) {
            navigator.sendBeacon("/api/scanner/session/" + token + "/disconnect");
        }
    });

    // ================= STATUS / FEEDBACK =================

    function setScannerStatus(text) {
        scannerStatusText.textContent = text;
    }

    function showFeedback(kind, message) {
        clearTimeout(feedbackTimer);

        feedbackBanner.classList.remove("d-none", "success", "error");
        feedbackBanner.classList.add(kind);
        feedbackIcon.className = "bi me-2 " + (kind === "success" ? "bi-check-circle-fill" : "bi-x-circle-fill");
        feedbackText.textContent = message;

        cameraViewport.classList.remove("flash-success", "flash-error");
        cameraViewport.classList.add(kind === "success" ? "flash-success" : "flash-error");

        feedbackTimer = setTimeout(() => {
            feedbackBanner.classList.add("d-none");
            cameraViewport.classList.remove("flash-success", "flash-error");
        }, FEEDBACK_DISPLAY_MS);
    }

    function beep() {
        try {
            if (!audioCtx) {
                return;
            }
            const osc = audioCtx.createOscillator();
            const gain = audioCtx.createGain();
            osc.connect(gain);
            gain.connect(audioCtx.destination);
            osc.frequency.value = 880;
            gain.gain.setValueAtTime(0.15, audioCtx.currentTime);
            osc.start();
            osc.stop(audioCtx.currentTime + 0.12);
        } catch (error) {
            // Beep is a nice-to-have; ignore if the browser blocks it.
        }
    }

    // ================= CAMERA ERRORS =================

    function cameraErrorMessage(error) {
        if (!window.isSecureContext) {
            return "Camera access requires a secure HTTPS connection.\nPlease connect using the secure LocalMart scanner URL.";
        }
        switch (error && error.name) {
            case "NotAllowedError":
            case "PermissionDeniedError":
                return "Camera permission denied. Please allow camera access in your browser settings and try again.";
            case "NotFoundError":
            case "OverconstrainedError":
                return "No usable camera was found on this device.";
            case "NotReadableError":
                return "The camera is already in use by another app.";
            default:
                return "Unable to access the camera on this device.";
        }
    }

    function checkCameraAvailability() {
        if (!window.isSecureContext) {
            setScannerStatus("Camera access requires a secure HTTPS connection. Please connect using the secure LocalMart scanner URL.");
            startButton.disabled = true;
            return false;
        }
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
            setScannerStatus("This browser does not support camera access.");
            startButton.disabled = true;
            return false;
        }
        if (typeof ZXing === "undefined") {
            setScannerStatus("Barcode scanner library failed to load. Check the phone's internet connection and reload the page.");
            startButton.disabled = true;
            return false;
        }
        return true;
    }

    // ================= START / STOP =================

    async function startScanner() {
        startButton.disabled = true;

        try {
            audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
        } catch (error) {
            audioCtx = null;
        }

        try {
            stream = await navigator.mediaDevices.getUserMedia({
                audio: false,
                video: { facingMode: { ideal: "environment" } }
            });
        } catch (error) {
            console.error(error);
            setScannerStatus(cameraErrorMessage(error));
            startButton.disabled = false;
            return;
        }

        cameraPreview.srcObject = stream;
        cameraPreview.classList.add("active");
        cameraOverlay.classList.add("active");
        cameraIdleState.classList.add("d-none");

        try {
            await cameraPreview.play();
        } catch (error) {
            // play() can reject if the user backgrounds the tab immediately; safe to ignore.
        }

        videoTrack = stream.getVideoTracks()[0];
        setupTorchButton(videoTrack);

        const hints = new Map();
        hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS, getSupportedFormats());
        hints.set(ZXing.DecodeHintType.TRY_HARDER, true);
        codeReader = new ZXing.BrowserMultiFormatReader(hints);

        setScannerStatus("Ready to scan");
        startButton.classList.add("d-none");
        stopButton.classList.remove("d-none");

        try {
            scanControls = await codeReader.decodeFromVideoElement(cameraPreview, (result, error) => {
                if (result) {
                    handleDetectedBarcode(result.getText());
                }
                // NotFoundException fires continuously between detections - expected, ignore.
            });
        } catch (error) {
            console.error(error);
        }
    }

    function stopScanner() {
        if (codeReader) {
            try {
                codeReader.reset();
            } catch (error) {
                // ignore
            }
            codeReader = null;
        }
        scanControls = null;

        if (stream) {
            stream.getTracks().forEach((track) => track.stop());
            stream = null;
        }
        videoTrack = null;

        cameraPreview.pause();
        cameraPreview.srcObject = null;
        cameraPreview.classList.remove("active");
        cameraOverlay.classList.remove("active");
        cameraIdleState.classList.remove("d-none");

        torchButton.classList.add("d-none");
        torchOn = false;

        startButton.classList.remove("d-none");
        startButton.disabled = false;
        stopButton.classList.add("d-none");

        setScannerStatus("Scanner stopped");
    }

    // ================= TORCH =================

    function setupTorchButton(track) {
        torchButton.classList.add("d-none");
        torchOn = false;

        if (!track || typeof track.getCapabilities !== "function") {
            return;
        }

        let capabilities;
        try {
            capabilities = track.getCapabilities();
        } catch (error) {
            return;
        }

        if (capabilities && capabilities.torch) {
            torchButton.classList.remove("d-none");
            torchButtonLabel.textContent = "Flashlight";
        }
    }

    async function toggleTorch() {
        if (!videoTrack) {
            return;
        }
        try {
            torchOn = !torchOn;
            await videoTrack.applyConstraints({ advanced: [{ torch: torchOn }] });
            torchButtonLabel.textContent = torchOn ? "Flashlight On" : "Flashlight";
        } catch (error) {
            console.error(error);
            torchOn = !torchOn;
        }
    }

    // ================= BARCODE HANDLING =================

    function handleDetectedBarcode(barcode) {
        const now = Date.now();
        if (barcode === lastBarcode && (now - lastScanTime) < DUPLICATE_COOLDOWN_MS) {
            return;
        }
        lastBarcode = barcode;
        lastScanTime = now;
        sendScan(barcode);
    }

    async function sendScan(barcode) {
        lastScanValue.textContent = barcode;

        try {
            const response = await fetch("/api/scanner/session/" + token + "/scan", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ barcode: barcode })
            });

            let data = {};
            try {
                data = await response.json();
            } catch (error) {
                // no body
            }

            if (response.ok && data.success) {
                onScanSuccess(barcode);
            } else if (response.status === 409) {
                setConnectionState("disconnected", "Disconnected");
                onScanFailure(data.error || "Scanner is not connected");
            } else if (response.status === 404) {
                setConnectionState("disconnected", "Session invalid");
                onScanFailure(data.error || "Scanner session is no longer valid");
            } else {
                onScanFailure(data.error || "Unable to send barcode");
            }
        } catch (error) {
            console.error(error);
            onScanFailure("Network error - unable to reach LocalMart POS");
        }
    }

    function onScanSuccess(barcode) {
        showFeedback("success", "✓ Scan successful");
        setScannerStatus("Ready to scan");

        if (navigator.vibrate) {
            navigator.vibrate(80);
        }
        beep();
    }

    function onScanFailure(message) {
        showFeedback("error", "✕ Unable to send barcode");
        setScannerStatus(message);

        if (navigator.vibrate) {
            navigator.vibrate([80, 60, 80]);
        }
    }

    // ================= EVENTS =================

    startButton.addEventListener("click", startScanner);
    stopButton.addEventListener("click", stopScanner);
    torchButton.addEventListener("click", toggleTorch);

    window.addEventListener("pagehide", () => {
        if (stream) {
            stream.getTracks().forEach((track) => track.stop());
        }
        if (navigator.sendBeacon) {
            navigator.sendBeacon("/api/scanner/session/" + token + "/disconnect");
        }
    });

    // ================= INIT =================

    setScannerStatus("Initializing...");
    cameraAvailable = checkCameraAvailability();
    if (cameraAvailable) {
        setScannerStatus("Ready to scan");
    }
    joinSession();
})();
