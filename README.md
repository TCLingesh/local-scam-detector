# ScamGuard 🛡️

**On-Device Scam SMS Detection and Explanation with ExecuTorch and Llama**

ScamGuard is a privacy-first Android application designed to detect scam text messages and explain the threats in plain language—entirely on your device. By leveraging a character-level CNN and a quantized Llama 3.2 1B Instruct model running via ExecuTorch, ScamGuard provides rapid, offline protection without ever sending your private messages to the cloud.

📄 **Final Report:** For full technical details, model architectures, hardware benchmarks, and evaluation metrics, refer to [`ScamGuard_Technical_Report.pdf`](ScamGuard_Technical_Report.pdf).

## 🚀 Key Features

* **Live Notification Scanning:** Automatically reads and scores incoming alerts from SMS, WhatsApp, and Telegram.
* **100% Offline Processing:** Requires strictly **no internet permission**. All inference runs on the phone's CPU.
* **Hybrid Detection Engine:** Combines a fast Character-Level CNN with a rule-based engine to catch scams and highlight specific warning signs (e.g., urgent wording, suspicious links).
* **AI-Powered Explanations:** Uses Meta's Llama 3.2 1B Instruct (4-bit weights) to generate a simple "WHY" explanation and actionable "TODO" steps for the user.
* **Privacy by Design:** Llama never receives the raw message text. It only receives the scam category and warning signs, protecting user privacy and preventing prompt injection.

## 🧠 System Architecture

1. **Notification Listener:** Captures notifications in the background, filters duplicates, and extracts text.
2. **Detector Pipeline:** 
   * **Character CNN (.pte):** Processes the text (lowercased, padded to 256 code points) and outputs a scam probability.
   * **Rule Engine:** Flags transparent warning signs (secret code requests, prize lures, account threats).
3. **Hybrid Decision:** A message is flagged as a scam if the CNN probability is $\ge 0.42$ OR if $\ge 2$ rule-based warning signs are triggered.
4. **Advisory Engine (Llama):** If flagged, Llama 3.2 1B (via ExecuTorch XNNPACK backend) is loaded lazily to stream an explanation of the threat and recommend remediation steps.

## 📊 Performance & Evaluation

* **Accuracy:** Reaches an F1 score of 0.927 on the realistic Mendeley slice. On a real-inbox slice, it maintains a low false-alarm rate of ~5.1%.
* **Speed:** Detection and verdict generation take mere milliseconds after a notification arrives. The Llama-generated explanation streams and completes in roughly 4-7 seconds.
* **Memory Footprint:** The app requires ~25MB of storage, plus ~820MB stored separately on the device for the quantized Llama `.pte` model.

## 🛠️ Tech Stack

* **Android:** Java, Android SDK (Min API 29 / Android 10).
* **Machine Learning:** PyTorch, ExecuTorch 1.5.1, XNNPACK Backend.
* **Models:** 
  * Custom 1D Character-level CNN (FP32, ~57 KB).
  * Llama 3.2 1B Instruct (8da4w quantized, ~820 MB).

## ⚠️ Limitations

* **Text-Only Detection:** The model evaluates wording and cannot verify sender reputation or validate URLs.
* **Language Support:** Currently optimized for English and Latin-script Hinglish.
* **Context Window:** Obfuscated text or scam indicators pushed past the 256-character limit may evade the CNN.

## 👥 Team & Acknowledgments

**SSN College of Engineering **
* Mayakavin P
* Meiyappan V
* Lingesh TC
