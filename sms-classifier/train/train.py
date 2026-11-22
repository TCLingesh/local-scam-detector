import json, copy
import numpy as np, pandas as pd, torch, torch.nn as nn
from sklearn.metrics import precision_recall_fscore_support, confusion_matrix
from common import SmsCharCNN, encode, load_vocab

torch.manual_seed(42); np.random.seed(42)
vocab = load_vocab()

def load(split):
    df = pd.read_csv(f"data/{split}.csv")
    X = torch.tensor([encode(t, vocab) for t in df["text"]], dtype=torch.long)
    y = torch.tensor(df["label"].values, dtype=torch.long)
    return X, y

def metrics(y, p, thr):
    pred = (p >= thr).astype(int)
    pr, rc, f1, _ = precision_recall_fscore_support(y, pred, average="binary", zero_division=0)
    tn, fp, fn, tp = confusion_matrix(y, pred, labels=[0, 1]).ravel()
    return dict(threshold=float(thr), precision=float(pr), recall=float(rc), f1=float(f1),
                fpr=float(fp / (fp + tn)), tn=int(tn), fp=int(fp), fn=int(fn), tp=int(tp))

Xtr, ytr = load("train"); Xva, yva = load("val"); Xte, yte = load("test")

model = SmsCharCNN(vocab_size=len(vocab) + 2)
n_ham, n_spam = (ytr == 0).sum().item(), (ytr == 1).sum().item()
loss_fn = nn.CrossEntropyLoss(weight=torch.tensor([1.0, n_ham / n_spam]))
opt = torch.optim.Adam(model.parameters(), lr=2e-3)

best_f1, best_state = -1, None
for epoch in range(1, 11):
    model.train()
    perm = torch.randperm(len(Xtr)); total = 0
    for i in range(0, len(Xtr), 64):
        idx = perm[i:i + 64]
        opt.zero_grad()
        loss = loss_fn(model.logits(Xtr[idx]), ytr[idx])
        loss.backward(); opt.step()
        total += loss.item() * len(idx)
    model.eval()
    with torch.no_grad():
        pv = model(Xva)[:, 1].numpy()
    m = metrics(yva.numpy(), pv, 0.5)
    print(f"epoch {epoch:2d} loss {total/len(Xtr):.4f} val F1 {m['f1']:.4f} "
          f"P {m['precision']:.3f} R {m['recall']:.3f}")
    if m["f1"] > best_f1:
        best_f1, best_state = m["f1"], copy.deepcopy(model.state_dict())

model.load_state_dict(best_state); model.eval()
with torch.no_grad():
    pv = model(Xva)[:, 1].numpy()
    pt = model(Xte)[:, 1].numpy()


# threshold chosen on VALIDATION only
# threshold chosen on VALIDATION only: lowest threshold that keeps false alarms <= 1%
grid = np.arange(0.05, 0.99, 0.01)
ok = [t for t in grid if metrics(yva.numpy(), pv, t)["fpr"] <= 0.01]
thr = round(float(min(ok)), 2) if ok else 0.5
val_m, test_m = metrics(yva.numpy(), pv, thr), metrics(yte.numpy(), pt, thr)
print("\nchosen threshold:", round(float(thr), 2))
print("VAL :", val_m)
print("TEST:", test_m)
te = pd.read_csv("data/test.csv")
print("\nTEST per source (threshold %.2f):" % thr)
per_source = {}
for s in te["source"].unique():
    mask = (te["source"] == s).values
    m = metrics(yte.numpy()[mask], pt[mask], thr)
    per_source[s] = m
    print(f"  {s:9s} n={mask.sum():4d} P {m['precision']:.3f} R {m['recall']:.3f} "
          f"F1 {m['f1']:.3f} FPR {m['fpr']:.3f}")
torch.save(best_state, "models/sms_charcnn.pt")
with open("results/metrics.json", "w") as f:
    json.dump({"threshold": float(thr), "val": val_m, "test": test_m,
           "test_per_source": per_source}, f, indent=2)