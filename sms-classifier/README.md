# On-device scam SMS detector (ML / AOT part)

A character-level CNN that classifies SMS text as scam or normal, trained in PyTorch and exported to ExecuTorch (`.pte`, XNNPACK backend) so it can run on an Arm-based Android phone with no cloud dependency.

## What's in this repo

| Folder / file | Contents |
|---|---|
| `train/` | Data preparation, training, error analysis, spot check |
| `export/` | Model spec, ExecuTorch export and validation, golden test, Llama test cases |
| `models/` | Files for the Android app: `sms_charcnn.pte`, `model_spec.json`, `vocab.json`, `golden_test.json` (and `sms_charcnn.pt`, the PyTorch weights) |
| `results/` | Metrics, export validation, delegation summary |
| `snapshot.sh` | Saves a copy of the current model, results and scripts |
| `VERSIONS.md` | Exact package versions (Python 3.12, ExecuTorch 1.6.0) |
| `report_notes.md` | Numbers and decisions for the technical report |

## Using the model in the Android app

Everything the app needs is in `models/`:
1. Load `sms_charcnn.pte` with the ExecuTorch Android API.
2. Preprocess text as described in `model_spec.json`: lowercase, keep the first 160 **code points**, map each to its index in `vocab.json` (unknown = 1), pad with 0 to length 160. Input is an `int64` tensor of shape `[1, 160]`.
3. Output is `[normal, scam]` probabilities (softmax is inside the model). Flag as scam if $\text{output}[1] \ge \text{threshold}$ (threshold $= 0.42$, specified in `model_spec.json`).
4. Verify your preprocessing with `golden_test.json`: it contains inputs, expected token IDs, and expected outputs. Check token IDs first, then outputs.

## Results

Held-out test set ($n = 1,560$), decision threshold $= 0.42$ (full metrics log in `results/metrics.json`):

| Slice | $n$ | Precision | Recall | F1 | FPR |
|---|---|---|---|---|---|
| `sentinel_dataset_multiclass_clean` | 820 | 1.000 | 1.000 | 1.000 | 0.00% |
| `Mendeley_dataset` | 575 | 0.943 | 0.912 | 0.927 | 1.03% |
| `inbox_clean` | 140 | 0.222 | 1.000 | 0.364 | 5.07% |
| `SMSSpamCollection` | 25 | 1.000 | 1.000 | 1.000 | 0.00% |
| **Overall** | **1,560** | **0.980** | **0.987** | **0.984** | **1.26%** |

*Overall confusion matrix ($n = 1,560$):* TP = 597, FP = 12, FN = 8, TN = 943.

The overall precision and recall figures are heavily weighted by the synthetic `sentinel` slice. When evaluating performance on real-world transaction and user inbox samples (`inbox_clean`), precision drops due to class imbalance ($2$ scams out of $140$ messages) and genuine bank alerts causing false positives. See Limitations.

### ExecuTorch export
* **Artifact:** `models/sms_charcnn.pte` ($57,352$ bytes, FP32).
* **Parity check:** Evaluated against PyTorch eager model on all $1,560$ test messages ($100\%$ argmax agreement, $100\%$ decision threshold agreement, max absolute difference $8.34 \times 10^{-7}$).
* **XNNPACK delegation:** 2 delegated subgraphs, 5 delegated nodes, 5 non-delegated nodes (`results/delegation_summary.txt`).

## Reproduce

```bash
python3 -m venv venv && source venv/bin/activate
pip install executorch pandas scikit-learn numpy
# put the datasets in data/ (see below), then:
python train/prep_data.py
python train/train.py
python export/make_spec.py
python export/export_pte.py
python export/make_golden.py
```

## Datasets

Not redistributed here. Download them and place them in `data/`:
- UCI SMS Spam Collection (Almeida et al., `SMSSpamCollection`)
- SMS Phishing Dataset, Mishra & Soni, Mendeley Data, DOI 10.17632/f45bkkt8pr.1 (`Mendeley_dataset`)
- Indian-context scam/legit SMS dataset (`sentinel_dataset_multiclass_clean`)
- Real-world clean inbox sample (`inbox_clean`)

Exact-text deduplication and conflicting-label removals are applied across all sources prior to splitting.

## Limitations

- **Label definition:** Positive = attempts fraud or credential theft; legitimate promotions and bank debit alerts count as normal. A text-only model cannot inspect sender IDs, so genuine bank transaction SMS and phishing clones share high semantic similarity.
- **Language support:** English and Roman-script Indian English only (Devanagari/Tamil scripts are excluded).
- **Class imbalance:** The test set ratio (~38.8% positive) is much higher than real mobile traffic, meaning real-world false-alarm rates will vary in production deployment.