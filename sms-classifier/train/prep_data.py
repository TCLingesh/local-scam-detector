import csv, json, os, re, glob, collections
import pandas as pd
from sklearn.model_selection import train_test_split
from common import MAX_LEN
try:
    from ftfy import fix_text
except ImportError:
    fix_text = lambda s: s

RAW_DIR, OUT_DIR, SEED = "data/raw", "data", 42
SPAM_DROP_FILES = set()      # file stems whose "spam" rows should be dropped instead of counted as scam
SKIP_FILES = set()           # file stems to ignore completely

TEXT_COLS  = ["text", "message", "msg", "sms", "body", "content", "v2", "sms_text"]
LABEL_COLS = ["label", "labels", "class", "category", "target", "v1", "orig"]
LEGIT = {"ham", "legit", "legitimate", "safe", "normal", "benign", "0", "false", "no"}
SCAM  = {"spam", "scam", "smishing", "phishing", "fraud", "1", "true", "yes"}


def read_any(path, **kw):
    err = None
    for enc in ("utf-8-sig", "latin-1"):
        try:
            return pd.read_csv(path, encoding=enc, **kw)
        except UnicodeDecodeError as e:
            err = e
    raise err


def map_label(v, stem):
    s = str(v).strip().lower()
    if s == "spam" and stem in SPAM_DROP_FILES:
        return None
    if s in LEGIT: return 0
    if s in SCAM:  return 1
    try:
        return 0 if float(s) == 0 else 1          # numeric multiclass: 0 legit, >0 scam
    except ValueError:
        return None


def scam_type(raw, lab):
    if lab == 0:
        return "legit"
    s = str(raw).strip().lower()
    if s in {"spam", "1", "true", "yes", "scam"}: return "scam"
    return s if not s.replace(".", "").isdigit() else f"class_{int(float(s))}"


def load_file(path):
    stem = os.path.splitext(os.path.basename(path))[0]
    with open(path, "rb") as f:
        first = (f.readline().decode("latin-1") or "")
    if re.match(r"^(ham|spam)\t", first, re.I):                    # UCI: tab-separated, no header
        df = read_any(path, sep="\t", header=None, names=["label", "text"], quoting=csv.QUOTE_NONE)
        tcol, lcol = "text", "label"
    else:
        df = read_any(path, sep=None, engine="python")
        cols = {str(c).strip().lower(): c for c in df.columns}
        tcol = next((cols[c] for c in TEXT_COLS if c in cols), None)
        lcol = next((cols[c] for c in LABEL_COLS if c in cols), None)
        if tcol is None or lcol is None:
            raise ValueError(f"no text/label column found in {list(df.columns)}")
    raw = df[lcol]
    lab = [map_label(v, stem) for v in raw]
    out = pd.DataFrame({"text": df[tcol], "label": lab, "source": stem,
                        "scam_type": [scam_type(r, l) if l is not None else None for r, l in zip(raw, lab)]})
    n_unmapped = int(out["label"].isna().sum())
    return out, tcol, lcol, n_unmapped


# ---------- discover and load ----------
files = sorted(p for p in glob.glob(os.path.join(RAW_DIR, "*"))
               if os.path.isfile(p) and "readme" not in os.path.basename(p).lower())
if not files:
    raise SystemExit(f"no files found in {RAW_DIR}/")

parts = []
print(f"{'file':35s} {'rows':>6s} {'text col':>10s} {'label col':>10s} {'dropped':>8s}  labels")
for p in files:
    stem = os.path.splitext(os.path.basename(p))[0]
    if stem in SKIP_FILES:
        print(f"{stem:35s} skipped (SKIP_FILES)"); continue
    try:
        d, tcol, lcol, nun = load_file(p)
    except Exception as e:
        print(f"{stem:35s} SKIPPED: {e}"); continue
    print(f"{stem:35s} {len(d):6d} {str(tcol):>10s} {str(lcol):>10s} {nun:8d}  "
          f"{d['label'].value_counts(dropna=True).sort_index().to_dict()}")
    parts.append(d)

df = pd.concat(parts, ignore_index=True)
df = df.dropna(subset=["text", "label"])
df["text"] = df["text"].astype(str).map(fix_text).str.strip()
df = df[df["text"].str.len() > 0]
df["label"] = df["label"].astype(int)
print("\nrows after dropping unmapped/empty:", len(df))

# ---------- dedupe across ALL sources (digits and URLs masked) ----------
URLRX = r"https?://\S+|\b\S+\.\S+/\S*"
df["key"] = (df["text"].str.lower().str.replace(URLRX, "url", regex=True)
             .str.replace(r"\d", "0", regex=True).str.replace(r"\s+", " ", regex=True).str.strip())
nconf = df.groupby("key")["label"].nunique()
bad = nconf[nconf > 1].index
print("conflicting-label keys dropped:", len(bad))
df = df[~df["key"].isin(bad)]
before = len(df)
df = df.drop_duplicates("key")
print("duplicates removed:", before - len(df), "| final rows:", len(df))
print(pd.crosstab(df["source"], df["label"]).to_string())

# ---------- split (stratified on source+label; rare strata fall back to label only) ----------
strat = df["source"] + "_" + df["label"].astype(str)
rare = strat.map(strat.value_counts()) < 5
strat = strat.where(~rare, "rare_" + df["label"].astype(str))
keys = df["key"]
cols = ["text", "label", "source", "scam_type"]
d_all = df.drop(columns="key")
train, temp, s_tr, s_tmp = train_test_split(d_all, strat, test_size=0.2, stratify=strat, random_state=SEED)
val, test = train_test_split(temp, test_size=0.5, stratify=s_tmp, random_state=SEED)

# sanity: no normalized text may appear in two splits
k = lambda x: set(keys.loc[x.index])
assert not (k(train) & k(val)) and not (k(train) & k(test)) and not (k(val) & k(test)), "split leakage"

os.makedirs(OUT_DIR, exist_ok=True)
for name, d in [("train", train), ("val", val), ("test", test)]:
    d[cols].to_csv(f"{OUT_DIR}/{name}.csv", index=False)
    print(f"\n{name}: {len(d)}")
    print(pd.crosstab(d["source"], d["label"]).to_string())

# ---------- vocab from TRAIN only; 0=PAD, 1=UNK ----------
counts = collections.Counter(c for t in train["text"] for c in t.lower())
chars = [c for c, n in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0])) if n >= 2]
vocab = {c: i + 2 for i, c in enumerate(chars)}
os.makedirs("models", exist_ok=True)
with open("models/vocab.json", "w", encoding="utf-8") as f:
    json.dump(vocab, f, ensure_ascii=False, indent=0)
print("\nvocab size (incl PAD, UNK):", len(vocab) + 2)

print("\nlength percentiles per source (train): [median, 95th, 99th]")
print(train.groupby("source")["text"].apply(lambda s: s.str.len().quantile([.5, .95, .99]).round().tolist()).to_string())
print("train longer than %d chars: %.1f%%" % (MAX_LEN, (train["text"].str.len() > MAX_LEN).mean() * 100))