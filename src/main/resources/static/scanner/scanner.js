(function () {
    "use strict";

    // ================= CONFIG =================

    const DUPLICATE_COOLDOWN_MS = 1500;
    const FEEDBACK_DISPLAY_MS = 1400;

    // Brief pause after a successful scan so the cashier sees/hears/feels
    // the success feedback before the page navigates to the result screen.
    const RESULT_NAV_DELAY_MS = 400;

    // Preferred capture resolution for barcode scanning. These are "ideal"
    // hints only - the browser/device picks the closest supported mode, so
    // this is safe on low-end hardware too.
    //
    // NOTE: ZXing-js decodes every frame in pure JS on a canvas sized to the
    // camera's *actual* captured resolution - it does not internally
    // downscale. 1920x1080 is sharp but can be too slow to decode in real
    // time on mid-range phones, which looks like "camera is fine but never
    // detects." 1280x720 is still plenty sharp for retail barcodes and
    // decodes much faster. Raise it only if testing shows a device handles
    // 1080p decode smoothly.
    const IDEAL_WIDTH = 1280;
    const IDEAL_HEIGHT = 720;
    const IDEAL_FRAME_RATE = 30;

    // Zoom levels we offer when the device reports zoom support, clipped to
    // whatever range the hardware actually allows.
    const ZOOM_LEVELS = [1, 1.5, 2];

    // Lens labels that indicate a non-main rear camera (ultra-wide/tele/macro
    // lenses focus poorly on close-up barcodes). Used only as a best-effort
    // heuristic on Android/Chrome - iOS typically only exposes one "Back
    // Camera" label, so this never triggers there.
    const NON_MAIN_LENS_PATTERN = /ultra\s*-?\s*wide|wide\s*angle|tele(photo)?|macro/i;
    const FRONT_LENS_PATTERN = /front|user|face/i;

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

    const zoomControls = document.getElementById("zoomControls");
    const zoomButtons = document.getElementById("zoomButtons");

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

    // Set right before we navigate to the scan-result page ourselves, so the
    // pagehide/visibilitychange handlers below know this isn't the phone
    // actually leaving - the scanner session should stay connected.
    let navigatingToResult = false;

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
        if (document.visibilityState === "hidden" && !navigatingToResult && navigator.sendBeacon) {
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
                return "No usable camera was found on this device.";
            case "OverconstrainedError":
            case "ConstraintNotSatisfiedError":
                return "This device does not support the requested camera settings.";
            case "NotReadableError":
            case "TrackStartError":
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

    // ================= CAMERA ACQUISITION =================

    // Requests the rear camera at a good resolution for barcode scanning.
    // Uses "ideal" hints only, and falls back to a bare-minimum request if
    // the richer constraints are rejected (OverconstrainedError), so this
    // never breaks devices/browsers with limited constraint support.
    async function acquireCameraStream() {
        const richConstraints = {
            audio: false,
            video: {
                facingMode: { ideal: "environment" },
                width: { ideal: IDEAL_WIDTH },
                height: { ideal: IDEAL_HEIGHT },
                frameRate: { ideal: IDEAL_FRAME_RATE }
            }
        };

        try {
            return await navigator.mediaDevices.getUserMedia(richConstraints);
        } catch (error) {
            if (error && error.name === "OverconstrainedError") {
                console.warn("[Scanner] Rich camera constraints rejected, retrying with minimal constraints.", error);
                return await navigator.mediaDevices.getUserMedia({
                    audio: false,
                    video: { facingMode: { ideal: "environment" } }
                });
            }
            throw error;
        }
    }

    // Best-effort: if the active rear lens looks like an ultra-wide/tele/macro
    // lens (poor close-focus behaviour on Android multi-camera phones), try
    // switching to a better-labelled rear lens. Any failure just keeps the
    // original stream - this must never block scanning from starting.
    async function preferMainRearCamera(initialStream) {
        try {
            const activeTrack = initialStream.getVideoTracks()[0];
            const activeLabel = activeTrack.label || "";

            if (!NON_MAIN_LENS_PATTERN.test(activeLabel)) {
                return initialStream;
            }

            const devices = await navigator.mediaDevices.enumerateDevices();
            const rearDevices = devices.filter((device) =>
                device.kind === "videoinput" && !FRONT_LENS_PATTERN.test(device.label)
            );
            const mainRear = rearDevices.find((device) => !NON_MAIN_LENS_PATTERN.test(device.label));

            if (!mainRear) {
                return initialStream;
            }

            const betterStream = await navigator.mediaDevices.getUserMedia({
                audio: false,
                video: {
                    deviceId: { exact: mainRear.deviceId },
                    width: { ideal: IDEAL_WIDTH },
                    height: { ideal: IDEAL_HEIGHT },
                    frameRate: { ideal: IDEAL_FRAME_RATE }
                }
            });

            initialStream.getTracks().forEach((track) => track.stop());
            return betterStream;
        } catch (error) {
            console.warn("[Scanner] Skipping main-lens reselection.", error);
            return initialStream;
        }
    }

    // Requests continuous autofocus if the device/browser reports support
    // for it. No-op (and safe) everywhere else, including iOS Safari.
    async function applyContinuousAutofocus(track) {
        try {
            if (typeof track.getCapabilities !== "function") {
                return;
            }
            const capabilities = track.getCapabilities();
            if (capabilities.focusMode && capabilities.focusMode.includes("continuous")) {
                await track.applyConstraints({ advanced: [{ focusMode: "continuous" }] });
            }
        } catch (error) {
            console.warn("[Scanner] Continuous autofocus not applied.", error);
        }
    }

    function logCameraDebugInfo(track) {
        try {
            if (typeof track.getSettings === "function") {
                console.log("[Scanner] camera settings:", track.getSettings());
            }
            if (typeof track.getCapabilities === "function") {
                console.log("[Scanner] camera capabilities:", track.getCapabilities());
            }
        } catch (error) {
            // Debug info only - never let this affect scanning.
        }
    }

    // ================= START / STOP =================

    async function startScanner() {
        startButton.disabled = true;
        setScannerStatus("Starting camera...");

        try {
            audioCtx = audioCtx || new (window.AudioContext || window.webkitAudioContext)();
        } catch (error) {
            audioCtx = null;
        }

        try {
            stream = await acquireCameraStream();
            stream = await preferMainRearCamera(stream);
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

        // IMPORTANT: do NOT call cameraPreview.play() here. ZXing's own
        // decodeFromVideoElementContinuously() waits internally for the
        // video's 'playing' event before it starts the decode loop. If we
        // already play() the video ourselves first, that event has already
        // fired by the time ZXing attaches its listener, so it never fires
        // again and the decode loop silently never starts. Let ZXing start
        // playback itself.

        videoTrack = stream.getVideoTracks()[0];
        logCameraDebugInfo(videoTrack);
        await applyContinuousAutofocus(videoTrack);
        setupTorchButton(videoTrack);
        setupZoomControls(videoTrack);

        const hints = new Map();
        hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS, getSupportedFormats());
        hints.set(ZXing.DecodeHintType.TRY_HARDER, true);
        codeReader = new ZXing.BrowserMultiFormatReader(hints);

        setScannerStatus("Ready to scan");
        startButton.classList.add("d-none");
        stopButton.classList.remove("d-none");

        try {
            // decodeFromVideoElement() (no "Continuously" suffix) only takes a
            // single argument and resolves ONCE after the first successful
            // decode - it silently ignores a second callback argument. That
            // meant the callback below was never actually invoked, so no scan
            // ever reached the POS even though ZXing could read the code fine.
            // decodeFromVideoElementContinuously() is the correct method for a
            // persistent scan-loop-with-callback, matching how this is used.
            let decodeLoopConfirmed = false;
            scanControls = await codeReader.decodeFromVideoElementContinuously(cameraPreview, (result, error) => {
                if (!decodeLoopConfirmed) {
                    // Fires on the very first callback invocation, success or
                    // not - proves frames are actually reaching the decoder.
                    // If this never logs, the loop itself never started.
                    decodeLoopConfirmed = true;
                    console.log("[Scanner] Decode loop is running - frames are being analyzed.");
                }
                if (result) {
                    handleDetectedBarcode(result.getText());
                }
                // NotFoundException fires continuously between detections - expected, ignore.
            });
            console.log("[Scanner] decodeFromVideoElementContinuously setup resolved - video is playing.");
        } catch (error) {
            // If the decode loop fails to start, the camera preview is still
            // showing (misleadingly looks "ready") but nothing is being
            // scanned. Surface this instead of failing silently.
            console.error("[Scanner] Failed to start barcode decoding:", error);
            setScannerStatus("Barcode scanning failed to start. Try Stop then Start again.");
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

        zoomControls.classList.add("d-none");
        zoomButtons.innerHTML = "";

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

    // ================= ZOOM =================

    function setupZoomControls(track) {
        zoomControls.classList.add("d-none");
        zoomButtons.innerHTML = "";

        if (!track || typeof track.getCapabilities !== "function") {
            return;
        }

        let capabilities;
        try {
            capabilities = track.getCapabilities();
        } catch (error) {
            return;
        }

        if (!capabilities || !capabilities.zoom) {
            return;
        }

        const min = capabilities.zoom.min;
        const max = capabilities.zoom.max;
        let levels = ZOOM_LEVELS.filter((level) => level >= min && level <= max);
        if (levels.length === 0) {
            levels = [min];
        }

        levels.forEach((level, index) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "zoom-btn" + (index === 0 ? " active" : "");
            button.textContent = level + "x";
            button.addEventListener("click", () => applyZoom(level, button));
            zoomButtons.appendChild(button);
        });

        zoomControls.classList.remove("d-none");
    }

    async function applyZoom(level, buttonEl) {
        if (!videoTrack) {
            return;
        }
        try {
            await videoTrack.applyConstraints({ advanced: [{ zoom: level }] });
            zoomButtons.querySelectorAll(".zoom-btn").forEach((btn) => btn.classList.remove("active"));
            buttonEl.classList.add("active");
        } catch (error) {
            console.warn("[Scanner] Zoom not applied.", error);
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

        // Stop decoding immediately so a stray frame can't trigger a second
        // scan while we navigate to the result page.
        navigatingToResult = true;
        if (stream) {
            stream.getTracks().forEach((track) => track.stop());
        }

        setTimeout(() => {
            window.location.href = "/scanner/" + token + "/result?barcode=" + encodeURIComponent(barcode);
        }, RESULT_NAV_DELAY_MS);
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
