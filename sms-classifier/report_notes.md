# Report notes (ML / AOT part)

## Data
- Sources used: UCI SMS Spam Collection (`SMSSpamCollection`), SMS Phishing Dataset (Mishra & Soni, Mendeley Data, DOI 10.17632/f45bkkt8pr.1, `Mendeley_dataset`), Indian-context scam/legit SMS dataset (`sentinel_dataset_multiclass_clean`), and real-world clean inbox sample (`inbox_clean`).
- Label definition: positive = message attempts fraud or credential theft; negative = everything else, including legitimate promotions and transaction notifications.
- Cleaning: lowercase labels, exact-text deduplication across sources, conflicting-label texts removed.
- Splits: validation $n = 1,559$ and test $n = 1,560$ messages.
- Test set composition: 820 `sentinel_dataset_multiclass_clean`, 575 `Mendeley_dataset`, 140 `inbox_clean`, and 25 `SMSSpamCollection` messages. Positives: 605 of 1,560 (approx. $38.8\%$).

## Model
- Character-level CNN (embedding, 1D convolution, global max pool, linear, softmax), trained in PyTorch, FP32.
- Decision threshold selected on the validation set; threshold $= 0.42$.

## Results (held-out test set, threshold 0.42)
| Slice | Precision | Recall | F1 | FPR | Positives | Total ($n$) |
|---|---|---|---|---|---|---|
| Overall | 0.980 | 0.987 | 0.984 | 1.26% | 605 | 1,560 |
| `sentinel_dataset_multiclass_clean` | 1.000 | 1.000 | 1.000 | 0.00% | 507 | 820 |
| `Mendeley_dataset` | 0.943 | 0.912 | 0.927 | 1.03% | 91 | 575 |
| `inbox_clean` | 0.222 | 1.000 | 0.364 | 5.07% | 2 | 140 |
| `SMSSpamCollection` | 1.000 | 1.000 | 1.000 | 0.00% | 5 | 25 |

- Overall test confusion matrix: TP 597, FP 12, FN 8, TN 943.
- Validation set metrics ($n = 1,559$): Precision 0.985, Recall 0.988, F1 0.987, FPR 0.94% (TP 598, FP 9, FN 7, TN 945).
- Test set numbers remain the primary benchmark to report.

## ExecuTorch export
- Exported with `torch.export`, lowered with the XNNPACK partitioner, saved as `models/sms_charcnn.pte` (57,352 bytes). FP32 format; INT8 quantization not applied (tiny model size under 60 KB).
- Delegation breakdown: 2 delegated subgraphs, 5 delegated nodes, 5 non-delegated nodes.
- Validation of the `.pte` artifact against PyTorch eager model on full test set ($n = 1,560$):
  - Argmax agreement: $1.0$ ($100\%$)
  - Threshold decision agreement: $1.0$ ($100\%$)
  - Max absolute difference: $8.34 \times 10^{-7}$
- Environment: Python 3.12, ExecuTorch 1.6.0.

## Model contract for the Android app
- `models/model_spec.json`, `models/vocab.json`, `models/golden_test.json`. Preprocessing: lowercase, keep first 160 code points, map to vocab index (unknown = 1), pad with 0.

## Limitations
- The `sentinel` slice yields perfect scores ($F_1 = 1.000$), indicating high template consistency in that subset.
- The `inbox_clean` slice demonstrates low precision ($0.222$) due to a heavy class imbalance (only 2 positive scam samples out of 140 total messages) and 7 false positives out of 138 legitimate messages (FPR $= 5.07\%$). Genuine bank transaction alerts remain the main source of false positives.
- Text-only input: lacks sender header context, making fake bank notifications visually and textually close to legitimate ones.
- Script coverage: restricted to English and Roman-script text; native Hindi/Tamil script tokenization is left for future iterations.